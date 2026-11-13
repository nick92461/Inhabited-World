package io.github.nick92461.inhabitedworld.worldstate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.worldgen.planner.BlockRect;
import io.github.nick92461.inhabitedworld.worldgen.planner.GeneratedTerrainSampler;
import io.github.nick92461.inhabitedworld.worldgen.planner.NoiseTerrainSampler;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteEvaluator;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteKind;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteMetrics;
import io.github.nick92461.inhabitedworld.worldgen.planner.TerrainGrid;
import io.github.nick92461.inhabitedworld.worldgen.planner.TerrainLimits;
import io.github.nick92461.inhabitedworld.worldgen.planner.TerrainSampler;
import io.github.nick92461.inhabitedworld.worldgen.pregen.PregenState;
import io.github.nick92461.inhabitedworld.worldgen.pregen.Pregeneration;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/*
  Verification after pre-generation (canon 02 section 6: pre-generation "lets the planner verify that required POIs
  actually generated, rather than assuming placement succeeded"; 02 section 8: facts may be verified against the
  generated terrain).

  Once pre-generation has finished, each planned site's terrain is measured again, this time from the generated
  chunks (GeneratedTerrainSampler), with the same SiteEvaluator the planner used, and judged by the rules the site
  was planned under, loosened by a tolerance but never below VALID_GROUND: the generated world adds what the noise
  can't predict (surface rules, carvers, aquifer lakes, trees). The world spawn vanilla chose must also lie outside
  every POI region. The result is recorded in the WorldPlan once and logged.

  On failure (decided for P2A step 5): the world still opens, the failure is logged as an error with every shortfall
  and recorded in the plan (the dev command /inhabited plan shows it). The plan is never changed after generation
  (02 section 7: Phase 3 places builds at the planned sites as the chunks generate, so moving a site afterwards
  would leave it on terrain generated for something else). Failure is meant to be near-impossible: the planner
  only picks sites whose predicted terrain meets the rules, its fallback steps guarantee every site exists, and the
  game tests check the prediction against a generated world.
 */
public final class SiteVerifier {
    /** How much generated terrain may differ from the planned rules before verification fails. */
    public static final double LAND_TOLERANCE = 0.10;
    public static final int RELIEF_TOLERANCE = 8;
    public static final int SURFACE_Y_TOLERANCE = 8;
    public static final double MOUNTAIN_TOLERANCE = 0.10;
    public static final double SEA_TOLERANCE = 0.15;

    /*
      The least any site's ground must be, whatever rules it was planned under: a site placed by the planner's last
      resort records "any terrain" as its rules, and must still fail verification if it stands mostly in water or on
      a cliff. Valid terrain (02 section 6) means at least this.
     */
    public static final TerrainLimits VALID_GROUND = new TerrainLimits(0.6, 48, 55, 160, 0.6);

    private static final long SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

    private SiteVerifier() {
    }

    /** Verifies the plan if it has sites, isn't verified yet, and pre-generation has finished. */
    public static void verifyIfNeeded(MinecraftServer server) {
        WorldPlanData data = WorldPlans.data(server);
        if (data == null || !data.plan().hasSites() || data.plan().verification().isPresent()) {
            return;
        }
        ServerLevel overworld = server.overworld();
        PregenState pregen = overworld.getDataStorage().get(PregenState.TYPE);
        if (pregen == null || !pregen.isComplete()) {
            return;
        }
        SiteVerification result = verify(server, overworld, data.plan());
        if (result == null) {
            return;
        }
        data.recordVerification(result);
        log(result);
    }

    /**
     * The rectangle verification reads for a site: its build, plus the sea checked beyond a waterfront side, as deep as
     * the site's own waterfront depth.
     */
    public static BlockRect sampledArea(PlannedSite site) {
        BlockRect b = site.bounds();
        int d = site.rules().waterfrontDepth();
        return site.waterfront().map(side -> switch (side) {
            case NORTH -> new BlockRect(b.minX(), b.minZ() - d, b.maxX(), b.maxZ());
            case SOUTH -> new BlockRect(b.minX(), b.minZ(), b.maxX(), b.maxZ() + d);
            case WEST -> new BlockRect(b.minX() - d, b.minZ(), b.maxX(), b.maxZ());
            case EAST -> new BlockRect(b.minX(), b.minZ(), b.maxX() + d, b.maxZ());
            default -> b;
        }).orElse(b);
    }

    /**
     * Judges a site's generated terrain by its planned rules plus the tolerance, and never by less than VALID_GROUND.
     * Empty when it passes.
     */
    public static List<String> judge(PlannedSite site, SiteMetrics generated) {
        TerrainLimits tolerant = site.rules().limits().relaxed(LAND_TOLERANCE, RELIEF_TOLERANCE, SURFACE_Y_TOLERANCE, MOUNTAIN_TOLERANCE);
        TerrainLimits limits = new TerrainLimits(Math.max(tolerant.minLand(), VALID_GROUND.minLand()), Math.min(tolerant.maxRelief(), VALID_GROUND.maxRelief()),
                Math.max(tolerant.minSurfaceY(), VALID_GROUND.minSurfaceY()), Math.min(tolerant.maxSurfaceY(), VALID_GROUND.maxSurfaceY()),
                Math.min(tolerant.maxMountain(), VALID_GROUND.maxMountain()));
        return SiteEvaluator.shortfalls(generated, limits, SiteEvaluator.MIN_SEA - SEA_TOLERANCE, Math.max(0.0, site.rules().minStripSea() - SEA_TOLERANCE));
    }

    /** Problems with the world spawn: it must not lie inside a POI's region. Empty when it passes. */
    public static List<String> judgeSpawn(List<PlannedSite> sites, BlockPos spawn) {
        List<String> problems = new ArrayList<>();
        for (PlannedSite site : sites) {
            if (site.kind() == SiteKind.POI && site.region().contains(spawn.getX(), spawn.getZ())) {
                problems.add("the world spawn " + spawn.toShortString() + " lies inside POI " + site.id() + "'s region " + site.region());
            }
        }
        return problems;
    }

    private static SiteVerification verify(MinecraftServer server, ServerLevel level, WorldPlan plan) {
        long started = System.nanoTime();
        Set<ChunkPos> needed = new LinkedHashSet<>();
        for (PlannedSite site : plan.sites()) {
            BlockRect area = sampledArea(site);
            for (int cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++) {
                for (int cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++) {
                    needed.add(new ChunkPos(cx, cz));
                }
            }
        }

        ServerChunkCache chunks = level.getChunkSource();
        if (!load(server, chunks, needed)) {
            InhabitedWorld.LOGGER.warn("[{}] the game closed before the planned sites could be verified; verification runs at the next opening", InhabitedWorld.MOD_ID);
            release(chunks, needed);
            return null;
        }

        List<SiteVerification.SiteCheck> checks = new ArrayList<>();
        boolean passed = true;
        try {
            GeneratedTerrainSampler sampler = new GeneratedTerrainSampler((cx, cz) -> {
                LevelChunk chunk = chunks.getChunkNow(cx, cz);
                if (chunk == null) {
                    throw new IllegalStateException("chunk " + cx + ", " + cz + " was not loaded for verification");
                }
                return chunk;
            });
            TerrainSampler predictor = new NoiseTerrainSampler((NoiseBasedChunkGenerator) chunks.getGenerator(), chunks.randomState(), level);
            for (PlannedSite site : plan.sites()) {
                TerrainGrid grid = TerrainGrid.sample(sampler, sampledArea(site));
                SiteMetrics generated = SiteEvaluator.measure(grid, site);
                List<String> problems = judge(site, generated);
                logAgreement(site, grid, TerrainGrid.sample(predictor, sampledArea(site)));
                passed &= problems.isEmpty();
                checks.add(new SiteVerification.SiteCheck(site.id(), problems.isEmpty(), generated, problems));
            }
        } finally {
            release(chunks, needed);
        }

        BlockPos spawn = level.getRespawnData().pos();
        List<String> spawnProblems = judgeSpawn(plan.sites(), spawn);
        passed &= spawnProblems.isEmpty();
        InhabitedWorld.LOGGER.info("[{}] verified {} planned sites against {} generated chunks in {} ms", InhabitedWorld.MOD_ID,
                plan.sites().size(), needed.size(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        return new SiteVerification(passed, checks, Optional.of(spawn), spawnProblems);
    }

    /*
      How well the noise prediction matched the generated world, column by column (dry land / open sea / other water),
      for the log: the planner's terrain facts rest on this agreement (02 section 8).
     */
    private static void logAgreement(PlannedSite site, TerrainGrid generated, TerrainGrid predicted) {
        int total = generated.columnCount();
        int agree = 0;
        Map<String, Integer> mismatches = new TreeMap<>();
        List<String> examples = new ArrayList<>();
        BlockRect area = generated.bounds();
        for (int j = 0; j < area.sizeZ() / TerrainGrid.STEP; j++) {
            for (int i = 0; i < area.sizeX() / TerrainGrid.STEP; i++) {
                TerrainSampler.Column g = generated.column(i, j);
                TerrainSampler.Column p = predicted.column(i, j);
                String pc = classOf(p);
                String gc = classOf(g);
                if (pc.equals(gc)) {
                    agree++;
                } else {
                    mismatches.merge(pc + " -> " + gc, 1, Integer::sum);
                    if (examples.size() < 3) {
                        examples.add("(" + generated.columnX(i) + ", " + generated.columnZ(j) + ") predicted " + p + ", generated " + g);
                    }
                }
            }
        }
        InhabitedWorld.LOGGER.info("[{}]   site {}: noise prediction matched the generated terrain on {} of {} columns{}{}", InhabitedWorld.MOD_ID,
                site.id(), agree, total, mismatches.isEmpty() ? "" : "; mismatches " + mismatches, examples.isEmpty() ? "" : "; e.g. " + String.join("; ", examples));
    }

    private static String classOf(TerrainSampler.Column column) {
        return column.dryLand() ? "dry" : column.sea() ? "sea" : "other";
    }

    // Loads every chunk (generating any that pre-generation skipped, as in a shortened development run), the same way
    // pre-generation does: tickets out, then the server thread keeps running chunk tasks until all are ready.
    private static boolean load(MinecraftServer server, ServerChunkCache chunks, Set<ChunkPos> needed) {
        AtomicInteger ready = new AtomicInteger();
        for (ChunkPos pos : needed) {
            chunks.addTicketAndLoadWithRadius(Pregeneration.TICKET, pos, 0).whenComplete((result, error) -> ready.incrementAndGet());
        }
        while (ready.get() < needed.size() && server.isRunning()) {
            long sliceEnd = System.nanoTime() + SLICE_NANOS;
            server.managedBlock(() -> ready.get() >= needed.size() || !server.isRunning() || System.nanoTime() > sliceEnd);
        }
        return ready.get() >= needed.size();
    }

    private static void release(ServerChunkCache chunks, Set<ChunkPos> needed) {
        for (ChunkPos pos : needed) {
            chunks.removeTicketWithRadius(Pregeneration.TICKET, pos, 0);
        }
    }

    private static void log(SiteVerification result) {
        for (SiteVerification.SiteCheck check : result.sites()) {
            if (check.passed()) {
                InhabitedWorld.LOGGER.info("[{}]   site {} verified: {}", InhabitedWorld.MOD_ID, check.id(), check.generated().describe());
            } else {
                InhabitedWorld.LOGGER.error("[{}]   site {} FAILED verification: {} (generated terrain: {})", InhabitedWorld.MOD_ID, check.id(),
                        String.join("; ", check.problems()), check.generated().describe());
            }
        }
        for (String problem : result.problems()) {
            InhabitedWorld.LOGGER.error("[{}]   {}", InhabitedWorld.MOD_ID, problem);
        }
        if (result.passed()) {
            InhabitedWorld.LOGGER.info("[{}] WorldPlan verification PASSED: every planned site stands on valid generated terrain", InhabitedWorld.MOD_ID);
        } else {
            InhabitedWorld.LOGGER.error("[{}] WorldPlan verification FAILED: the world opens, but a planned site's terrain or the spawn is not as planned (see above)",
                    InhabitedWorld.MOD_ID);
        }
    }
}
