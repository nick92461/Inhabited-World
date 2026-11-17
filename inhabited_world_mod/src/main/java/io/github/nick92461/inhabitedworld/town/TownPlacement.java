package io.github.nick92461.inhabitedworld.town;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.mixin.ServerLevelEntitiesAccessor;
import io.github.nick92461.inhabitedworld.region.RegionIndex;
import io.github.nick92461.inhabitedworld.region.SemanticRegion;
import io.github.nick92461.inhabitedworld.worldgen.planner.BlockRect;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;
import io.github.nick92461.inhabitedworld.worldgen.pregen.PregenState;
import io.github.nick92461.inhabitedworld.worldgen.pregen.Pregeneration;
import io.github.nick92461.inhabitedworld.worldstate.PlacedStructure;
import io.github.nick92461.inhabitedworld.worldstate.PlacementCheck;
import io.github.nick92461.inhabitedworld.worldstate.SemanticLocation;
import io.github.nick92461.inhabitedworld.worldstate.SiteVerifier;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlan;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlans;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

/*
  Places a new world's handcrafted builds at their planned sites (canon 02 sections 6, 7, 9 and 16; 09 section 54,
  Phase 3): today the town, Drachenfels district D1 Harbour. It is placed exactly once, while the world is being
  created, before the player can enter.

  Mechanism: a controlled paste after pre-generation, not a structure placed during chunk generation. The site is
  per world (it comes from the WorldPlan, not from data-driven structure placement), the build is 72 tiles that must
  be placed with the export's contract (no updates, air above, solid below) and loaded one at a time, and the blend
  has to dig and fill far more than vanilla's terrain adaptation (beardifier) reaches: a seabed at the sea side and a
  ~30-block climb over 32 blocks of border. Placing into finished chunks also lets every step be checked and resumed.

  When (MinecraftServerMixin, at the start of level preparation): after pre-generation has finished and the planned
  sites' natural terrain has been verified (SiteVerifier measures the ground the town replaces), and before the
  world opens. Only sites listed in the world's PlacementState are placed; that list is made once, with the
  WorldPlan, so older worlds are never touched and a placed build is never placed again.

  Steps, per site (PlacementState): TILES (each tile: its columns' contract and ground cleanup, then the template,
  turned by the site's rotation), BLEND (TownBlend's heights for every blend column; then generated entities in the
  site's region are removed, light is brought up to date, sampled blocks are read back, and the PlacedStructure with
  its semantic locations is recorded in the WorldPlan), DONE. All of it is idempotent, and progress is written to
  disk only right after a full save of the chunks (checkpoint), so a world closed or crashed in the middle resumes
  without a double or partial town.
 */
public final class TownPlacement {
    private static final long SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final long ENTITY_WAIT_NANOS = TimeUnit.SECONDS.toNanos(30);

    /** What placing one site took, for the log and the tests. */
    public record Result(String site, String build, int tiles, long templateBlocks, long cleared, long filled, long groundReplaced,
                         int blendColumns, int blendChanged, int maxRaise, int maxCut, int steepestStep, int solveIterations,
                         Map<String, Integer> templateEntitiesDropped, Map<String, Integer> generatedEntitiesRemoved,
                         Map<String, Integer> entitiesLeft, Optional<PlacementCheck> check, long tileMillis, long blendMillis, long totalMillis, boolean finished) {
        public String describe() {
            return this.tiles + " tiles, " + this.templateBlocks + " template blocks, " + this.cleared + " cleared above, " + this.filled
                    + " filled below, " + this.groundReplaced + " ores/infested replaced; blend " + this.blendColumns + " columns ("
                    + this.blendChanged + " changed, raise up to " + this.maxRaise + ", cut up to " + this.maxCut + ", steepest step "
                    + this.steepestStep + ", " + this.solveIterations + " solver sweeps); template entities dropped "
                    + this.templateEntitiesDropped + ", generated entities removed " + this.generatedEntitiesRemoved + ", entities left "
                    + this.entitiesLeft + "; tiles "
                    + this.tileMillis + " ms, blend " + this.blendMillis + " ms, total " + this.totalMillis + " ms"
                    + this.check.map(c -> "; check " + c.describe()).orElse("");
        }
    }

    private static final List<Result> RESULTS = new ArrayList<>();

    private TownPlacement() {
    }

    /** What placement did in this game session (tests and the creation-time measurement read it). */
    public static List<Result> results() {
        return List.copyOf(RESULTS);
    }

    /*
      Called once, when the WorldPlan is made: every site whose catalog ID names a town in the mod's data gets an entry,
      fixed to the version found now. Other sites (the POI's placeholder) have nothing to place yet.
     */
    public static void schedule(MinecraftServer server, ServerLevel overworld, WorldPlan plan) {
        List<PlacementState.Pending> pending = new ArrayList<>();
        for (PlannedSite site : plan.sites()) {
            if (!site.catalogId().getNamespace().equals(InhabitedWorld.MOD_ID)) {
                continue;
            }
            Optional<TownManifest> town = TownManifest.find(server.getResourceManager(), site.catalogId().getPath());
            if (town.isPresent()) {
                pending.add(new PlacementState.Pending(site.id(), town.get().key()));
                InhabitedWorld.LOGGER.info("[{}] site {} will get build {} ({}), turned {}", InhabitedWorld.MOD_ID, site.id(), town.get().key(),
                        town.get().name(), site.rotation().getSerializedName());
            } else {
                InhabitedWorld.LOGGER.info("[{}] site {} ({}) has no build to place yet", InhabitedWorld.MOD_ID, site.id(), site.catalogId());
            }
        }
        overworld.getDataStorage().set(PlacementState.TYPE, PlacementState.start(pending));
    }

    public static @Nullable PlacementState state(ServerLevel overworld) {
        return overworld.getDataStorage().get(PlacementState.TYPE);
    }

    /** Places whatever this world still has to place, if pre-generation and the terrain verification are done. */
    public static void placeIfNeeded(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        PlacementState state = state(overworld);
        if (state == null || state.isComplete()) {
            return;
        }
        WorldPlan plan = WorldPlans.get(server);
        PregenState pregen = overworld.getDataStorage().get(PregenState.TYPE);
        if (plan == null || pregen == null || !pregen.isComplete() || plan.verification().isEmpty()) {
            InhabitedWorld.LOGGER.warn("[{}] builds are placed after pre-generation and the site check; they are placed at the next opening", InhabitedWorld.MOD_ID);
            return;
        }
        for (PlacementState.Entry entry : state.entries()) {
            if (entry.isDone() || entry.failure().isPresent()) {
                continue;
            }
            if (!server.isRunning()) {
                return;
            }
            try {
                place(server, overworld, state, entry);
            } catch (RuntimeException e) {
                // A broken build file must not stop the world from opening, at this or any later opening.
                InhabitedWorld.LOGGER.error("[{}] placing site {} ({}) failed; the world opens without it, and placement is not retried",
                        InhabitedWorld.MOD_ID, entry.site(), entry.build(), e);
                state.fail(entry, e.toString());
            }
        }
    }

    private static void place(MinecraftServer server, ServerLevel level, PlacementState state, PlacementState.Entry entry) {
        long started = System.nanoTime();
        WorldPlan plan = WorldPlans.get(server);
        PlannedSite site = plan.site(entry.site());
        Optional<TownManifest> found = TownManifest.find(server.getResourceManager(), entry.build());
        if (site == null || found.isEmpty() || !found.get().key().equals(entry.build())) {
            String why = site == null ? "the plan has no site " + entry.site() : "the mod's data has no build " + entry.build();
            InhabitedWorld.LOGGER.error("[{}] cannot place site {}: {}; the world opens without it", InhabitedWorld.MOD_ID, entry.site(), why);
            state.fail(entry, why);
            return;
        }
        TownManifest town = found.get();
        TownColumns columns = TownColumns.load(server.getResourceManager(), town.columnsId());
        TownTransform t = TownTransform.forSite(site, columns.width(), columns.depth(), town.baseY());
        BlockRect region = site.region();
        int cleanupMinY = regionFloor(plan, site.id(), level.getMinY());

        Set<ChunkPos> chunks = chunksOf(region.inflate(1));
        ServerChunkCache cache = level.getChunkSource();
        InhabitedWorld.LOGGER.info("[{}] placing {} at site {}: bounds {}, turned {}, template y 0 at world y {}; loading {} chunks", InhabitedWorld.MOD_ID,
                entry.build(), site.id(), site.bounds(), t.rotation().getSerializedName(), town.baseY(), chunks.size());
        if (!load(server, cache, chunks)) {
            release(cache, chunks);
            return;
        }
        try {
            // TILES
            long tileStart = System.nanoTime();
            long blocks = 0;
            long cleared = 0;
            long filled = 0;
            long replaced = 0;
            Map<String, Integer> dropped = new TreeMap<>();
            int firstTile = entry.nextTile();
            if (entry.stage() == PlacementState.Stage.TILES) {
                for (int i = entry.nextTile(); i < town.tiles().size(); i++) {
                    if (!server.isRunning()) {
                        logStopped(entry);
                        return;
                    }
                    TownPlacer.TileResult r = TownPlacer.placeTile(level, town, columns, t, i, cleanupMinY);
                    blocks += r.templateBlocks();
                    cleared += r.cleared();
                    filled += r.filled();
                    replaced += r.groundReplaced();
                    r.entitiesDropped().forEach((k, v) -> dropped.merge(k, v, Integer::sum));
                    state.tilePlaced(entry, i);
                    pumpLight(server, cache, chunks, false);
                }
                checkpoint(server, level, () -> state.tilesDone(entry));
            }
            long tileMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tileStart);

            // BLEND
            long blendStart = System.nanoTime();
            if (!server.isRunning()) {
                logStopped(entry);
                return;
            }
            TownBlend.Field field = blendField(level, columns, t, region);
            int waterTop = level.getSeaLevel() - 1;
            int free = 0;
            int changed = 0;
            int maxRaise = 0;
            int maxCut = 0;
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int x = region.minX(); x <= region.maxX(); x++) {
                    if (!field.free(x, z)) {
                        continue;
                    }
                    free++;
                    TownPlacer.BlendResult r = TownPlacer.blendColumn(level, x, z, field.ground(x, z), waterTop);
                    if (r.changed()) {
                        changed++;
                        maxRaise = Math.max(maxRaise, r.delta());
                        maxCut = Math.max(maxCut, -r.delta());
                    }
                }
            }
            Map<String, Integer> removed = removeEntities(server, level, region, chunks);
            Map<String, Integer> left = entitiesIn(level, region);
            pumpLight(server, cache, chunks, true);
            long blendMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - blendStart);

            // record: semantic locations and the read-back check, once
            PlacementCheck check = SiteVerifier.checkPlacement(level, server, town, columns, t);
            if (WorldPlans.get(server).structure(site.id()).isEmpty()) {
                PlacedStructure placed = new PlacedStructure(site.id(), site.catalogId(), town.key(), t.rotation(),
                        new BlockPos(t.worldX(0, 0), t.baseY(), t.worldZ(0, 0)), locations(plan, site, town, t), check);
                WorldPlans.recordPlacement(server, placed);
            }
            checkpoint(server, level, () -> state.blendDone(entry));

            Result result = new Result(site.id(), town.key(), town.tiles().size() - firstTile, blocks, cleared, filled, replaced, free, changed,
                    maxRaise, maxCut, TownBlend.steepestStep(field), field.iterations(), dropped, removed, left, Optional.of(check), tileMillis, blendMillis,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), true);
            RESULTS.add(result);
            InhabitedWorld.LOGGER.info("[{}] placed {} at site {}: {}", InhabitedWorld.MOD_ID, town.key(), site.id(), result.describe());
            WorldPlans.get(server).structure(site.id()).ifPresent(placed -> placed.locations().forEach(l ->
                    InhabitedWorld.LOGGER.info("[{}]   semantic location {} at {}, yaw {}, region {}", InhabitedWorld.MOD_ID, l.id(),
                            l.pos().toShortString(), l.yaw(), l.region())));
            if (check.passed()) {
                InhabitedWorld.LOGGER.info("[{}] placement check PASSED for site {}: {}", InhabitedWorld.MOD_ID, site.id(), check.describe());
            } else {
                InhabitedWorld.LOGGER.error("[{}] placement check FAILED for site {}: {} (the world opens; the site stays where it was planned)",
                        InhabitedWorld.MOD_ID, site.id(), check.describe());
            }
        } finally {
            release(cache, chunks);
        }
    }

    private static void logStopped(PlacementState.Entry entry) {
        InhabitedWorld.LOGGER.warn("[{}] the game closed while placing site {} ({}); placement resumes at the next opening", InhabitedWorld.MOD_ID,
                entry.site(), entry);
    }

    /*
      The blend grid: the site's region plus one ring of generated ground outside it. Stored town columns and the ring are
      fixed; the town's blend columns and the border between bounds and region are free. A stored column's edge height
      is the median ground of the stored columns within EDGE_MEDIAN of it (TownBlend.edgeGround), so a single odd
      column (a pillar's footing, one deep wall) doesn't pull the blend into a spike or a pit.
     */
    public static TownBlend.Field blendField(ServerLevel level, TownColumns columns, TownTransform t, BlockRect region) {
        BlockRect area = region.inflate(1);
        int w = area.sizeX();
        byte[] kind = new byte[w * area.sizeZ()];
        double[] heights = new double[kind.length];
        BlockRect bounds = t.bounds();
        for (int z = area.minZ(); z <= area.maxZ(); z++) {
            for (int x = area.minX(); x <= area.maxX(); x++) {
                int i = (z - area.minZ()) * w + (x - area.minX());
                if (!region.contains(x, z)) {
                    kind[i] = TownBlend.FIXED;
                    heights[i] = TownPlacer.groundY(level, x, z);
                } else if (bounds.contains(x, z)) {
                    int[] tc = t.toTemplate(x, z);
                    if (columns.stored(tc[0], tc[1])) {
                        kind[i] = TownBlend.FIXED;
                        heights[i] = t.worldY(TownBlend.edgeGround(columns, tc[0], tc[1]));
                    } else {
                        kind[i] = TownBlend.FREE;
                    }
                } else {
                    kind[i] = TownBlend.FREE;
                }
            }
        }
        return TownBlend.solve(area, kind, heights);
    }

    // The manifest's markers, turned and moved into the world, each with the semantic region at its block.
    public static List<SemanticLocation> locations(WorldPlan plan, PlannedSite site, TownManifest town, TownTransform t) {
        RegionIndex index = plan.buildRegionIndex();
        List<SemanticLocation> out = new ArrayList<>();
        for (TownManifest.Marker m : town.markers()) {
            BlockPos pos = t.toWorld(m.pos());
            SemanticRegion region = index.regionAt(pos.getX(), pos.getY(), pos.getZ());
            out.add(new SemanticLocation(m.id(), pos, t.yaw(m.yaw()), site.id(), region.id()));
        }
        return out;
    }

    // The lowest y of the site's own semantic regions: ground cleanup reaches down to it.
    private static int regionFloor(WorldPlan plan, String site, int fallback) {
        return plan.regions().stream().filter(r -> r.site().filter(site::equals).isPresent())
                .mapToInt(r -> r.volume().minY()).min().orElse(fallback);
    }

    /*
      Generated entities inside the site's region (animals that spawned with the chunks) stood on terrain that is now
      a town or a blend slope; they are removed, except types a town template may carry. Players are never touched.
      Entity loads are processed here because no level tick runs yet.
     */
    private static Map<String, Integer> removeEntities(MinecraftServer server, ServerLevel level, BlockRect region, Set<ChunkPos> chunks) {
        PersistentEntitySectionManager<Entity> manager = ((ServerLevelEntitiesAccessor) level).inhabitedWorld$entityManager();
        long deadline = System.nanoTime() + ENTITY_WAIT_NANOS;
        while (server.isRunning() && System.nanoTime() < deadline && !chunks.stream().allMatch(c -> level.areEntitiesLoaded(c.pack()))) {
            long sliceEnd = System.nanoTime() + SLICE_NANOS;
            server.managedBlock(() -> System.nanoTime() > sliceEnd);
            manager.processPendingLoads();
        }
        long notLoaded = chunks.stream().filter(c -> !level.areEntitiesLoaded(c.pack())).count();
        if (notLoaded > 0) {
            InhabitedWorld.LOGGER.warn("[{}] entities of {} chunks had not loaded when the town's region was cleared of generated entities", InhabitedWorld.MOD_ID, notLoaded);
        }
        Map<String, Integer> removed = new TreeMap<>();
        for (Entity e : level.getEntities((Entity) null, box(level, region), e -> !(e instanceof Player) && !TownPlacer.allowedInTown(e.getType()))) {
            removed.merge(EntityType.getKey(e.getType()).toString(), 1, Integer::sum);
            e.discard();
        }
        return removed;
    }

    // The entities (other than players) in a site's region, by type.
    static Map<String, Integer> entitiesIn(ServerLevel level, BlockRect region) {
        Map<String, Integer> out = new TreeMap<>();
        for (Entity e : level.getEntities((Entity) null, box(level, region), e -> !(e instanceof Player) && !e.isRemoved())) {
            out.merge(EntityType.getKey(e.getType()).toString(), 1, Integer::sum);
        }
        return out;
    }

    private static AABB box(ServerLevel level, BlockRect r) {
        return new AABB(r.minX(), level.getMinY(), r.minZ(), r.maxX() + 1, level.getMaxY() + 1.0, r.maxZ() + 1);
    }

    // Lets the light engine catch up with the blocks set so far (no level tick runs yet to do it); `all` waits for every chunk.
    private static void pumpLight(MinecraftServer server, ServerChunkCache cache, Set<ChunkPos> chunks, boolean all) {
        List<CompletableFuture<?>> pending = new ArrayList<>();
        if (all) {
            for (ChunkPos c : chunks) {
                pending.add(cache.getLightEngine().waitForPendingTasks(c.x(), c.z()));
            }
        } else {
            ChunkPos any = chunks.iterator().next();
            pending.add(cache.getLightEngine().waitForPendingTasks(any.x(), any.z()));
        }
        cache.getLightEngine().tryScheduleUpdate();
        while (server.isRunning() && !pending.stream().allMatch(CompletableFuture::isDone)) {
            long sliceEnd = System.nanoTime() + SLICE_NANOS;
            cache.getLightEngine().tryScheduleUpdate();
            server.managedBlock(() -> pending.stream().allMatch(CompletableFuture::isDone) || System.nanoTime() > sliceEnd);
        }
    }

    /*
      Writes the world as it is (chunks, entities, the level's data) and the WorldPlan, then advances the progress in
      memory and writes it. The progress on disk is therefore never ahead of the blocks or the plan on disk: a crash
      anywhere in between repeats a step, which changes nothing.
     */
    private static void checkpoint(MinecraftServer server, ServerLevel level, Runnable advance) {
        level.save(null, true, false);
        server.getDataStorage().saveAndJoin();
        advance.run();
        level.getDataStorage().saveAndJoin();
    }

    static Set<ChunkPos> chunksOf(BlockRect area) {
        Set<ChunkPos> out = new LinkedHashSet<>();
        for (int cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++) {
            for (int cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++) {
                out.add(new ChunkPos(cx, cz));
            }
        }
        return out;
    }

    // Loads the chunks to full status the way pre-generation does, generating any it skipped (shortened dev runs).
    private static boolean load(MinecraftServer server, ServerChunkCache cache, Set<ChunkPos> chunks) {
        AtomicInteger ready = new AtomicInteger();
        for (ChunkPos pos : chunks) {
            cache.addTicketAndLoadWithRadius(Pregeneration.TICKET, pos, 0).whenComplete((result, error) -> ready.incrementAndGet());
        }
        while (ready.get() < chunks.size() && server.isRunning()) {
            long sliceEnd = System.nanoTime() + SLICE_NANOS;
            server.managedBlock(() -> ready.get() >= chunks.size() || !server.isRunning() || System.nanoTime() > sliceEnd);
        }
        return ready.get() >= chunks.size();
    }

    private static void release(ServerChunkCache cache, Set<ChunkPos> chunks) {
        for (ChunkPos pos : chunks) {
            cache.removeTicketWithRadius(Pregeneration.TICKET, pos, 0);
        }
    }
}
