package io.github.nick92461.inhabitedworld.gametest;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;
import io.github.nick92461.inhabitedworld.worldgen.RpgWorlds;
import io.github.nick92461.inhabitedworld.worldgen.planner.BlockRect;
import io.github.nick92461.inhabitedworld.worldgen.planner.GeneratedTerrainSampler;
import io.github.nick92461.inhabitedworld.worldgen.planner.NoisePlanning;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlanRequest;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteEvaluator;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteKind;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteMetrics;
import io.github.nick92461.inhabitedworld.worldgen.planner.SitePlanner;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteRules;
import io.github.nick92461.inhabitedworld.worldgen.planner.TerrainGrid;
import io.github.nick92461.inhabitedworld.worldgen.planner.V01Sites;
import io.github.nick92461.inhabitedworld.worldgen.planner.Waterfront;
import io.github.nick92461.inhabitedworld.worldstate.SiteVerification;
import io.github.nick92461.inhabitedworld.worldstate.SiteVerifier;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlan;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlans;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
  P2A step 5, the planner, in a real Inhabited World created by this game-test run (canon 02 sections 6 to 8): the
  WorldPlan holds both required sites inside the map, verification after pre-generation passed, re-planning the
  same seed gives the same plan, and verification fails on terrain that is not valid (negative control).

  The multi-seed survey plans many seeds from the generator's noise alone (no chunks) and reports the success rate
  and where sites land. Normal builds survey a few seeds; -PplannerSeeds=250 surveys more and writes
  build/reports/planner-survey.csv.
 */
public class PlannerGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger("inhabited_world/gametest");
    public static final String SURVEY_SEEDS_PROPERTY = "inhabited_world.dev.plannerSeeds";

    private static WorldPlan plan(GameTestHelper helper) {
        WorldPlan plan = WorldPlans.get(helper.getLevel().getServer());
        if (plan == null) {
            throw helper.assertionException("a new Inhabited World must have a WorldPlan");
        }
        return plan;
    }

    // Both required sites exist, inside the map with its margin, apart, and with the spacing rule kept (02 section 6).
    @GameTest
    public void worldPlanHasTheTownAndThePoi(GameTestHelper helper) {
        WorldPlan plan = plan(helper);
        helper.assertTrue(plan.dataVersion() == WorldPlan.DATA_VERSION, "the plan carries data version " + WorldPlan.DATA_VERSION);
        helper.assertTrue(plan.planner().isPresent() && plan.planner().get().algorithm().equals(SitePlanner.ALGORITHM)
                && plan.planner().get().version() == SitePlanner.VERSION, "the plan records the planner that made it");
        PlannedSite town = plan.site(V01Sites.TOWN);
        PlannedSite poi = plan.site(V01Sites.EXTERNAL_POI);
        helper.assertTrue(town != null && town.kind() == SiteKind.SETTLEMENT, "the plan has the town, a settlement");
        helper.assertTrue(poi != null && poi.kind() == SiteKind.POI, "the plan has the external POI");
        helper.assertTrue(plan.sites().size() == 2, "Version 0.1 plans exactly two sites, got " + plan.sites().size());

        BlockRect usable = V01Sites.request(plan.playableArea()).usableArea();
        for (PlannedSite site : plan.sites()) {
            helper.assertTrue(usable.contains(site.region()), site.id() + "'s region " + site.region() + " must lie inside " + usable);
            helper.assertTrue(site.region().equals(site.bounds().inflate(site.rules().footprint().blend())), site.id() + "'s region is its bounds plus the blend border");
        }
        helper.assertFalse(town.region().intersects(poi.region()), "the town and POI regions must not overlap");
        if (plan.planner().get().relaxation() < SitePlanner.LOOSER_LAYOUT) {
            helper.assertTrue(town.region().gapTo(poi.region()) >= V01Sites.MIN_WILDERNESS_GAP, "at least " + V01Sites.MIN_WILDERNESS_GAP + " blocks of wilderness between them");
            helper.assertTrue(town.bounds().centerDistanceTo(poi.bounds()) <= V01Sites.MAX_CENTER_DISTANCE, "at most " + V01Sites.MAX_CENTER_DISTANCE + " blocks apart");
        }
        LOGGER.info("game-test world plan: {} at {}, {}{}; {} at {}, {}; {} ({})", town.id(), town.centerX(), town.centerZ(),
                town.waterfront().map(d -> " facing " + d.getName()).orElse(""), poi.id(), poi.centerX(), poi.centerZ(),
                plan.planner().get().describe(), town.predicted().describe());
        helper.succeed();
    }

    // Verification ran after pre-generation (it generates the site chunks a shortened development run skipped) and passed.
    @GameTest
    public void verificationAfterPreGenerationPassed(GameTestHelper helper) {
        WorldPlan plan = plan(helper);
        helper.assertTrue(plan.verification().isPresent(), "the plan must be verified once pre-generation has finished");
        SiteVerification verification = plan.verification().get();
        helper.assertTrue(verification.sites().size() == plan.sites().size(), "every site is verified");
        for (SiteVerification.SiteCheck check : verification.sites()) {
            helper.assertTrue(check.passed(), "site " + check.id() + " failed verification: " + String.join("; ", check.problems())
                    + " (generated " + check.generated().describe() + ")");
            helper.assertTrue(check.generated().land() >= 0.75, "site " + check.id() + " stands on land (" + check.generated().describe() + ")");
        }
        helper.assertTrue(verification.spawn().isPresent(), "verification records the world spawn");
        helper.assertTrue(verification.passed(), "verification passed: " + verification.problems());
        helper.succeed();
    }

    // The plan is a pure function of the seed and the generator: planning this world's seed again gives the same sites.
    @GameTest(maxTicks = 400)
    public void replanningTheSameSeedGivesTheSamePlan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorldPlan plan = plan(helper);
        NoisePlanning.Timed again = NoisePlanning.plan((NoiseBasedChunkGenerator) level.getChunkSource().getGenerator(),
                level.getChunkSource().randomState(), level.getSeed(), level, V01Sites.request(plan.playableArea()));
        helper.assertTrue(again.outcome().sites().equals(plan.sites()), "re-planning seed " + level.getSeed() + " gave " + again.outcome().sites()
                + " instead of " + plan.sites());
        helper.succeed();
    }

    // Negative control: a site planned on open sea (the pre-generated ocean ring north of the map) fails verification.
    @GameTest(maxTicks = 400)
    public void verificationFailsOnSea(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayableArea area = plan(helper).playableArea();
        BlockRect bounds = BlockRect.ofSize(0, area.minBlockZ() - 160, 96, 96);
        PlannedSite onSea = new PlannedSite("sea_control", SiteKind.POI, V01Sites.POI_CATALOG_ID,
                new SiteRules(V01Sites.POI_FOOTPRINT, Waterfront.NONE, V01Sites.POI_TERRAIN, 0), bounds, bounds.inflate(16), Optional.empty(),
                new SiteMetrics(1.0, 0, 70, 0.0, Optional.empty()));
        TerrainGrid grid = TerrainGrid.sample(new GeneratedTerrainSampler((cx, cz) -> level.getChunk(cx, cz)), SiteVerifier.sampledArea(onSea));
        SiteMetrics generated = SiteEvaluator.measure(grid, bounds, null);
        List<String> problems = SiteVerifier.judge(onSea, generated);
        helper.assertTrue(generated.land() == 0.0, "control: the ring north of the map is open sea, but measured " + generated.describe());
        helper.assertFalse(problems.isEmpty(), "verification must fail a site on open sea");
        helper.succeed();
    }

    /*
      The multi-seed survey: plans many seeds of rpg_overworld_v2 from noise alone and reports how often each fallback
      step was needed and where sites land. A normal build plans SURVEY_DEFAULT seeds; -PplannerSeeds=N plans N seeds
      for each waterfront setting and writes a CSV. Every seed must get both sites (the last resort guarantees it),
      and in the default build every surveyed seed must be planned without falling back past looser terrain.
     */
    private static final int SURVEY_DEFAULT = 6;

    /** One seed's plan; for a seed that needed a fallback step, whether each site alone already failed the strict rules. */
    private record Row(long seed, Waterfront mode, SitePlanner.Outcome outcome, long samplingMillis, long planningMillis,
                       boolean townAloneFails, boolean poiAloneFails) {
    }

    @GameTest(maxTicks = 20000)
    public void plannerSurvey(GameTestHelper helper) throws Exception {
        String property = System.getProperty(SURVEY_SEEDS_PROPERTY);
        int count = property == null ? SURVEY_DEFAULT : Integer.parseInt(property);
        boolean full = property != null;
        long[] seeds = surveySeeds(count);
        Waterfront[] modes = full ? Waterfront.values() : new Waterfront[]{V01Sites.TOWN_WATERFRONT};

        ServerLevel level = helper.getLevel();
        Holder<NoiseGeneratorSettings> settings = level.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(RpgWorlds.NOISE_SETTINGS_V2);
        HolderGetter<NormalNoise> noises = level.registryAccess().lookupOrThrow(Registries.NOISE);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(level.getChunkSource().getGenerator().getBiomeSource(), settings);
        LevelHeightAccessor heights = LevelHeightAccessor.create(-64, 384);

        long started = System.currentTimeMillis();
        List<Row> rows = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        try {
            List<Future<List<Row>>> futures = new ArrayList<>();
            for (long seed : seeds) {
                futures.add(pool.submit(() -> {
                    RandomState random = RandomState.create(noises, seed, settings.value());
                    List<Row> perSeed = new ArrayList<>();
                    for (Waterfront mode : modes) {
                        PlanRequest request = V01Sites.request(PlayableArea.V0_1, mode);
                        NoisePlanning.Timed timed = NoisePlanning.plan(generator, random, seed, heights, request);
                        boolean townFails = false;
                        boolean poiFails = false;
                        if (timed.outcome().run().relaxation() > SitePlanner.STRICT) {
                            SitePlanner.SpawnOrigin spawn = NoisePlanning.spawnOrigin(generator, random);
                            townFails = SitePlanner.plan(request.with(List.of(request.sites().get(0)), List.of()), timed.grid(), seed, spawn).run().relaxation() > 0;
                            poiFails = SitePlanner.plan(request.with(List.of(request.sites().get(1)), List.of()), timed.grid(), seed, spawn).run().relaxation() > 0;
                        }
                        perSeed.add(new Row(seed, mode, timed.outcome(), timed.samplingMillis(), timed.planningMillis(), townFails, poiFails));
                    }
                    return perSeed;
                }));
            }
            for (Future<List<Row>> future : futures) {
                rows.addAll(future.get());
            }
        } finally {
            pool.shutdownNow();
        }
        long millis = System.currentTimeMillis() - started;

        for (Row row : rows) {
            helper.assertTrue(row.outcome().sites().size() == 2, "seed " + row.seed() + " must get both sites");
            if (row.mode() == V01Sites.TOWN_WATERFRONT && !full) {
                helper.assertTrue(row.outcome().run().relaxation() <= SitePlanner.LOOSER_TERRAIN,
                        "seed " + row.seed() + " needed fallback step " + row.outcome().run().relaxation());
            }
        }
        for (Waterfront mode : modes) {
            LOGGER.info("planner survey, town waterfront {}: {}", mode.getSerializedName(), summary(rows.stream().filter(r -> r.mode() == mode).toList()));
        }
        LOGGER.info("planner survey: {} seeds x {} settings in {} ms ({} worker threads)", seeds.length, modes.length, millis,
                Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        if (full) {
            writeCsv(rows);
        }
        helper.succeed();
    }

    // The island tests' six seeds, then arbitrary 64-bit seeds like the ones a new world gets.
    private static long[] surveySeeds(int count) {
        long[] seeds = new long[count];
        Random random = new Random(20261112L);
        for (int k = 0; k < count; k++) {
            seeds[k] = k < IslandGameTests.SEEDS.length ? IslandGameTests.SEEDS[k] : random.nextLong();
        }
        return seeds;
    }

    private static String summary(List<Row> rows) {
        int[] steps = new int[SitePlanner.LAST_RESORT + 1];
        Map<Direction, Integer> facing = new EnumMap<>(Direction.class);
        int inland = 0;
        double[] townFromCentre = new double[rows.size()];
        double[] apart = new double[rows.size()];
        double[] gap = new double[rows.size()];
        double[] poiFromCentre = new double[rows.size()];
        int[] quadrant = new int[4];
        int townAlone = 0;
        int poiAlone = 0;
        int together = 0;
        long sampling = 0;
        long planning = 0;
        for (int k = 0; k < rows.size(); k++) {
            Row row = rows.get(k);
            steps[row.outcome().run().relaxation()]++;
            if (row.outcome().run().relaxation() > SitePlanner.STRICT) {
                if (row.townAloneFails()) townAlone++;
                if (row.poiAloneFails()) poiAlone++;
                if (!row.townAloneFails() && !row.poiAloneFails()) together++;
            }
            PlannedSite town = row.outcome().sites().get(0);
            PlannedSite poi = row.outcome().sites().get(1);
            if (town.waterfront().isPresent()) {
                facing.merge(town.waterfront().get(), 1, Integer::sum);
            } else {
                inland++;
            }
            townFromCentre[k] = Math.hypot(town.bounds().centerX(), town.bounds().centerZ());
            poiFromCentre[k] = Math.hypot(poi.bounds().centerX(), poi.bounds().centerZ());
            apart[k] = town.bounds().centerDistanceTo(poi.bounds());
            gap[k] = town.region().gapTo(poi.region());
            quadrant[(town.bounds().centerX() >= 0 ? 1 : 0) + (town.bounds().centerZ() >= 0 ? 2 : 0)]++;
            sampling += row.samplingMillis();
            planning += row.planningMillis();
        }
        return String.format(Locale.ROOT,
                "%d seeds; fallback steps strict/looser terrain/looser layout/last resort = %s (of the fallbacks: town alone fails strict %d, POI alone %d, only together %d); town waterfront facing %s, inland %d; "
                        + "town quadrants NW/NE/SW/SE = %s; town centre from map centre %s; POI centre from map centre %s; "
                        + "town-POI centre distance %s; wilderness gap %s; mean sampling %d ms, planning %d ms per seed",
                rows.size(), Arrays.toString(steps), townAlone, poiAlone, together, facing, inland, Arrays.toString(quadrant), stats(townFromCentre), stats(poiFromCentre),
                stats(apart), stats(gap), rows.isEmpty() ? 0 : sampling / rows.size(), rows.isEmpty() ? 0 : planning / rows.size());
    }

    private static String stats(double[] values) {
        if (values.length == 0) {
            return "-";
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return String.format(Locale.ROOT, "min %.0f / p10 %.0f / median %.0f / p90 %.0f / max %.0f", sorted[0], sorted[(int) (0.1 * (sorted.length - 1))],
                sorted[(sorted.length - 1) / 2], sorted[(int) Math.ceil(0.9 * (sorted.length - 1))], sorted[sorted.length - 1]);
    }

    private static void writeCsv(List<Row> rows) throws IOException {
        File file = new File(System.getProperty("inhabited_world.dev.reportDir", "."), "planner-survey.csv");
        file.getParentFile().mkdirs();
        try (PrintWriter out = new PrintWriter(file, StandardCharsets.UTF_8)) {
            out.println("seed,town_waterfront_setting,fallback_step,town_x,town_z,town_facing,town_land,town_relief,town_median_y,town_sea,"
                    + "poi_x,poi_z,poi_land,poi_relief,poi_median_y,centre_distance,wilderness_gap,candidates,sampling_ms,planning_ms");
            for (Row row : rows) {
                PlannedSite town = row.outcome().sites().get(0);
                PlannedSite poi = row.outcome().sites().get(1);
                out.println(String.format(Locale.ROOT, "%d,%s,%d,%d,%d,%s,%.3f,%d,%d,%s,%d,%d,%.3f,%d,%d,%.0f,%.0f,%d,%d,%d", row.seed(),
                        row.mode().getSerializedName(), row.outcome().run().relaxation(), town.centerX(), town.centerZ(),
                        town.waterfront().map(Direction::getName).orElse("inland"), town.predicted().land(), town.predicted().relief(),
                        town.predicted().medianY(), town.predicted().sea().map(s -> String.format(Locale.ROOT, "%.3f", s)).orElse(""),
                        poi.centerX(), poi.centerZ(), poi.predicted().land(), poi.predicted().relief(), poi.predicted().medianY(),
                        town.bounds().centerDistanceTo(poi.bounds()), town.region().gapTo(poi.region()), row.outcome().run().candidates(),
                        row.samplingMillis(), row.planningMillis()));
            }
        }
        LOGGER.info("planner survey written to {}", file.getAbsolutePath());
    }

}
