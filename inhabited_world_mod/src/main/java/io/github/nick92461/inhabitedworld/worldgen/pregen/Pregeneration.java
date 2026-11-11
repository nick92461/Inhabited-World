package io.github.nick92461.inhabitedworld.worldgen.pregen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.mixin.ChunkMapInvoker;
import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlan;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlans;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.ChunkPos;

import org.jspecify.annotations.Nullable;

/*
  Pre-generation of the finite world (canon 02 section 6): the whole playable area is generated while the world is
  being created, before the player enters, so play never waits on chunk generation (and never competes with local
  inference for the CPU).

  It runs on the server thread at the start of vanilla's level preparation (MinecraftServerMixin), the same place
  and the same way vanilla loads its initial chunks: the server thread hands out tickets and keeps running chunk
  tasks, while the generation itself runs on vanilla's worldgen worker threads. The client keeps rendering its
  loading screen on its own thread throughout.

  Progress goes out through vanilla's LevelLoadListener, the server-to-client loading channel that exists before any
  player connection (canon 08 section 2.21): in single-player it drives the loading screen's progress bar and chunk
  map, and on a dedicated server it is logged as "Preparing spawn area: N%".

  What is generated: the playable area plus a ring of ocean beyond the world border, as wide as the default view
  distance, so a player standing at the border sees only finished chunks and play never generates any (user
  decision 2026-11-11).

  Work proceeds band by band (PregenSchedule). The next band's tickets are added before the previous band's are
  removed, so the chunks they share stay loaded, and everything else unloads (and is saved) as it goes.
 */
public final class Pregeneration {
    /** Rows of chunks per band: 4 rows of a 64-chunk-wide map is 256 chunks, plenty for every worker thread. */
    public static final int BAND_ROWS = 4;

    /** Chunks of ocean pre-generated beyond the world border on every side: vanilla's default view distance. */
    public static final int OCEAN_RING_CHUNKS = 12;

    /** Development only: pre-generate just this many chunk rows from the north edge (shortens test runs). */
    public static final String DEV_ROWS_PROPERTY = "inhabited_world.dev.pregenRows";

    // Loads chunks to full status; never saved with the world (a resumed run adds its own again).
    public static final TicketType TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, InhabitedWorld.id("pregeneration"),
            new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));

    private static final long SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

    private Pregeneration() {
    }

    // Forces the ticket type's registration during mod initialization.
    public static void init() {
    }

    /** What a pre-generation run did, for the log and for tests. */
    public record Result(int chunks, long millis, long peakHeapBytes, int peakLoadedChunks, boolean finished) {
    }

    private static @Nullable Result lastResult;

    public static @Nullable Result lastResult() {
        return lastResult;
    }

    /**
     * Pre-generates the overworld if it has a WorldPlan and isn't finished yet. Returns whether it reported progress
     * through the load listener's LOAD_INITIAL_CHUNKS stage (then vanilla must not start that stage again).
     */
    public static boolean runIfNeeded(MinecraftServer server, LevelLoadListener listener) {
        WorldPlan plan = WorldPlans.get(server);
        if (plan == null) {
            return false;
        }

        ServerLevel overworld = server.overworld();
        PregenState state = overworld.getDataStorage().get(PregenState.TYPE);
        if (state == null) {
            state = PregenState.start(target(plan.playableArea()));
            overworld.getDataStorage().set(PregenState.TYPE, state);
        }
        if (state.isComplete()) {
            return false;
        }

        lastResult = run(server, overworld, state, listener);
        return true;
    }

    /** Everything a world pre-generates: its playable area and the ocean ring around it. */
    public static PregenSchedule fullSchedule(PlayableArea playable) {
        return PregenSchedule.covering(playable, OCEAN_RING_CHUNKS, BAND_ROWS);
    }

    private static PregenSchedule target(PlayableArea playable) {
        PregenSchedule full = fullSchedule(playable);
        String rows = System.getProperty(DEV_ROWS_PROPERTY);
        if (rows == null || rows.equals("all") || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return full;
        }
        PregenSchedule limited = full.firstRows(Integer.parseInt(rows));
        InhabitedWorld.LOGGER.warn("[{}] development run: pre-generating only the first {} rows ({} of {} chunks)", InhabitedWorld.MOD_ID,
                limited.rows(), limited.totalChunks(), full.totalChunks());
        return limited;
    }

    private static Result run(MinecraftServer server, ServerLevel level, PregenState state, LevelLoadListener listener) {
        PregenSchedule schedule = state.schedule();
        ServerChunkCache chunks = level.getChunkSource();
        int total = schedule.totalChunks();
        long started = System.nanoTime();
        Runtime runtime = Runtime.getRuntime();
        long peakHeap = 0;
        int peakLoaded = 0;
        int firstBand = state.nextBand();

        InhabitedWorld.LOGGER.info("[{}] pre-generating the map and its ocean ring: {} chunks in {} bands (starting at band {})",
                InhabitedWorld.MOD_ID, total, schedule.bandCount(), firstBand);
        listener.start(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS, total);
        listener.update(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS, schedule.chunksBefore(firstBand), total);

        List<ChunkPos> previous = List.of();
        boolean finished = true;
        for (int band = firstBand; band < schedule.bandCount(); band++) {
            List<ChunkPos> current = schedule.band(band);
            AtomicInteger ready = new AtomicInteger();
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            for (ChunkPos pos : current) {
                chunks.addTicketAndLoadWithRadius(TICKET, pos, 0).whenComplete((result, error) -> {
                    if (error != null) {
                        failures.add(error);
                    } else if (result instanceof ChunkResult<?> chunkResult && !chunkResult.isSuccess()) {
                        failures.add(new IllegalStateException(pos + ": " + chunkResult.getError()));
                    }
                    ready.incrementAndGet();
                });
            }
            for (ChunkPos pos : previous) {
                chunks.removeTicketWithRadius(TICKET, pos, 0);
            }
            listener.updateFocus(level.dimension(), schedule.bandCenter(band));

            while (ready.get() < current.size() && server.isRunning()) {
                long sliceEnd = System.nanoTime() + SLICE_NANOS;
                server.managedBlock(() -> ready.get() >= current.size() || !server.isRunning() || System.nanoTime() > sliceEnd);
                // Unloading (and saving) normally happens in the level tick, which isn't running yet.
                ((ChunkMapInvoker) chunks.chunkMap).inhabitedWorld$tick(() -> true);
                listener.update(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS, schedule.chunksBefore(band) + ready.get(), total);
                peakHeap = Math.max(peakHeap, runtime.totalMemory() - runtime.freeMemory());
                peakLoaded = Math.max(peakLoaded, chunks.getLoadedChunksCount());
            }

            if (!failures.isEmpty()) {
                InhabitedWorld.LOGGER.error("[{}] {} chunks of band {} failed to generate; they will generate when first visited",
                        InhabitedWorld.MOD_ID, failures.size(), band, failures.getFirst());
            }
            if (ready.get() < current.size()) {
                // The game is closing: keep this band's progress for the next opening.
                for (ChunkPos pos : current) {
                    chunks.removeTicketWithRadius(TICKET, pos, 0);
                }
                previous = List.of();
                finished = false;
                break;
            }
            state.completeBand(band);
            previous = current;
        }
        for (ChunkPos pos : previous) {
            chunks.removeTicketWithRadius(TICKET, pos, 0);
        }
        // Write every generated chunk to disk before the world opens (vanilla's own full save), so the map exists on
        // disk as soon as pre-generation counts as done, and the thousands of chunks still loaded unload right away
        // instead of trickling out during the first minutes of play.
        long saveStarted = System.nanoTime();
        chunks.save(true);
        long saveMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - saveStarted);
        listener.finish(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS);

        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        int generated = schedule.chunksBefore(state.nextBand()) - schedule.chunksBefore(firstBand);
        InhabitedWorld.LOGGER.info("[{}] pre-generation {}: {} chunks in {} ms ({} chunks/s; final save {} ms), peak heap in use {} MB, peak loaded chunks {}, loaded after save {}",
                InhabitedWorld.MOD_ID, finished ? "finished" : "stopped early", generated, millis,
                millis == 0 ? generated : generated * 1000L / millis, saveMillis, peakHeap >> 20, peakLoaded, chunks.getLoadedChunksCount());
        return new Result(generated, millis, peakHeap, peakLoaded, finished);
    }
}
