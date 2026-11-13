package io.github.nick92461.inhabitedworld.worldgen.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

// The deterministic planner (canon 02 sections 6 to 8) on synthetic terrain, so every rule can be checked exactly.
class SitePlannerTest {
    private static final PlayableArea AREA = PlayableArea.V0_1;
    private static final SitePlanner.SpawnOrigin SPAWN = new SitePlanner.SpawnOrigin(8, 8);

    /*
      A square island like rpg_overworld_v2's: land within `half` blocks of the centre on both axes, gently rolling
      (a few blocks of relief over a town's width), a beach ring, open sea beyond.
     */
    static TerrainSampler island(int half) {
        return (x, z) -> {
            int edge = Math.max(Math.abs(x), Math.abs(z));
            if (edge >= half) {
                return new TerrainSampler.Column(40, true, Ground.SEA);
            }
            if (edge >= half - 16) {
                return new TerrainSampler.Column(63, false, Ground.BEACH);
            }
            int y = 72 + (int) Math.round(4 * Math.sin(x / 70.0) * Math.cos(z / 90.0));
            return new TerrainSampler.Column(y, false, Ground.LAND);
        };
    }

    static TerrainGrid grid(TerrainSampler sampler) {
        return TerrainGrid.sample(sampler, SitePlanner.samplingArea(V01Sites.request(AREA)));
    }

    /*
      The test island's coast lies 384 blocks from the centre: a build's edge may reach 447 (the 480-block usable
      area minus the 32-block blend), so the Harbour's 112-block strip can reach 4 of its 7 chunk columns into the
      sea, enough for its strip-sea rule. A coast at 400 would leave only 3 of 7 (see V01Sites on the margins).
     */
    static final int ISLAND_HALF = 384;
    private static final TerrainGrid ISLAND = grid(island(ISLAND_HALF));

    private static SitePlanner.Outcome plan(TerrainGrid grid, long seed, Waterfront waterfront) {
        return SitePlanner.plan(V01Sites.request(AREA, waterfront), grid, seed, SPAWN);
    }

    @Test
    void theSameSeedAlwaysGivesTheSamePlan() {
        for (long seed : new long[]{0L, 1L, 20261029L, -7L}) {
            assertEquals(plan(ISLAND, seed, Waterfront.PREFERRED), plan(ISLAND, seed, Waterfront.PREFERRED), "seed " + seed);
        }
    }

    // 02 section 6: "their position and relationship may vary between seeds", even on the same terrain.
    @Test
    void sitesVaryBetweenSeeds() {
        Set<BlockRect> towns = new HashSet<>();
        Set<BlockRect> pois = new HashSet<>();
        for (long seed = 0; seed < 20; seed++) {
            SitePlanner.Outcome outcome = plan(ISLAND, seed, Waterfront.PREFERRED);
            towns.add(outcome.sites().get(0).bounds());
            pois.add(outcome.sites().get(1).bounds());
        }
        assertTrue(towns.size() >= 5, "20 seeds gave only " + towns.size() + " town sites");
        assertTrue(pois.size() >= 5, "20 seeds gave only " + pois.size() + " POI sites");
    }

    @Test
    void sitesStayInsideTheMapWithTheirMarginsAndOnChunks() {
        PlanRequest request = V01Sites.request(AREA);
        for (long seed = 0; seed < 10; seed++) {
            SitePlanner.Outcome outcome = plan(ISLAND, seed, Waterfront.PREFERRED);
            assertEquals(SitePlanner.STRICT, outcome.run().relaxation(), "the island gives a strict plan for seed " + seed);
            assertEquals(2, outcome.sites().size());
            for (PlannedSite site : outcome.sites()) {
                SiteRequirement asked = request.sites().stream().filter(s -> s.id().equals(site.id())).findFirst().orElseThrow();
                Footprint footprint = asked.rules().footprint();
                assertTrue(request.usableArea().contains(site.region()), site.id() + " region " + site.region());
                assertEquals(site.bounds().inflate(footprint.blend()), site.region());
                assertEquals(PlannedSite.rotatedSizeX(footprint, site.rotation()), site.bounds().sizeX(), "the footprint, turned");
                assertEquals(PlannedSite.rotatedSizeZ(footprint, site.rotation()), site.bounds().sizeZ(), "the footprint, turned");
                assertEquals(0, Math.floorMod(site.bounds().minX(), 16), "chunk-aligned");
                assertEquals(0, Math.floorMod(site.bounds().minZ(), 16), "chunk-aligned");
                assertEquals(asked.kind(), site.kind());
                assertEquals(asked.catalogId(), site.catalogId());
                assertTrue(SiteEvaluator.meets(site.predicted(), site.rules()), site.id() + " meets its own rules: " + site.predicted().describe());
            }
        }
    }

    @Test
    void townAndPoiKeepTheirDistanceRange() {
        for (long seed = 0; seed < 20; seed++) {
            List<PlannedSite> sites = plan(ISLAND, seed, Waterfront.PREFERRED).sites();
            PlannedSite town = sites.get(0);
            PlannedSite poi = sites.get(1);
            assertTrue(town.region().gapTo(poi.region()) >= V01Sites.MIN_WILDERNESS_GAP, "seed " + seed + ": gap " + town.region().gapTo(poi.region()));
            assertTrue(town.bounds().centerDistanceTo(poi.bounds()) <= V01Sites.MAX_CENTER_DISTANCE, "seed " + seed);
            assertTrue(poi.region().distanceTo(SPAWN.x() + 0.5, SPAWN.z() + 0.5) >= V01Sites.POI_SPAWN_CLEARANCE, "seed " + seed + ": POI too near the spawn");
        }
    }

    // A tight distance cap is kept too: no plan places them further apart than asked.
    @Test
    void aTighterDistanceCapIsKept() {
        PlanRequest base = V01Sites.request(AREA);
        PlanRequest tight = base.with(base.sites(), List.of(new SiteSpacing(V01Sites.TOWN, V01Sites.EXTERNAL_POI, 64, 360)));
        for (long seed = 0; seed < 10; seed++) {
            SitePlanner.Outcome outcome = SitePlanner.plan(tight, ISLAND, seed, SPAWN);
            assertEquals(SitePlanner.STRICT, outcome.run().relaxation());
            assertTrue(outcome.sites().get(0).bounds().centerDistanceTo(outcome.sites().get(1).bounds()) <= 360);
        }
    }

    @Test
    void aWaterfrontTownFacesTheSea() {
        TerrainSampler terrain = island(ISLAND_HALF);
        for (long seed = 0; seed < 10; seed++) {
            for (Waterfront mode : new Waterfront[]{Waterfront.PREFERRED, Waterfront.REQUIRED}) {
                PlannedSite town = plan(ISLAND, seed, mode).sites().get(0);
                assertTrue(town.waterfront().isPresent(), "a " + mode + " waterfront town gets a waterfront side");
                Direction side = town.waterfront().get();
                BlockRect b = town.bounds();
                // Beyond the middle of the waterfront side lies open sea on the real terrain, and so does the outer part of
                // the harbour strip (at least half of it is sea, and this coast is straight).
                int x = (int) b.centerX() + side.getStepX() * (b.sizeX() / 2 + 40);
                int z = (int) b.centerZ() + side.getStepZ() * (b.sizeZ() / 2 + 40);
                assertTrue(terrain.sample(x, z).sea(), "seed " + seed + ": beyond the " + side.getName() + " side lies sea");
                int sx = (int) b.centerX() + side.getStepX() * (b.sizeX() / 2 - 24);
                int sz = (int) b.centerZ() + side.getStepZ() * (b.sizeZ() / 2 - 24);
                assertTrue(terrain.sample(sx, sz).sea(), "seed " + seed + ": the harbour strip's outer part lies on the sea");
                assertTrue(town.predicted().sea().orElseThrow() >= SiteEvaluator.MIN_SEA);
                assertTrue(town.predicted().stripSea().orElseThrow() >= V01Sites.TOWN_MIN_STRIP_SEA);
                assertEquals(side, town.rotation().rotate(V01Sites.TOWN_SEA_SIDE), "the rotation turns the Harbour's east side to the sea");
            }
            PlannedSite inland = plan(ISLAND, seed, Waterfront.NONE).sites().get(0);
            assertTrue(inland.waterfront().isEmpty(), "a town without a waterfront rule gets no waterfront side");
            assertTrue(inland.predicted().sea().isEmpty());
        }
    }

    /*
      A coast only to the north: the Harbour, whose sea side is east, must be turned a quarter anticlockwise, so its
      272 x 256 footprint lies 256 wide and 272 deep, with its strip along the northern edge.
     */
    @Test
    void theHarbourIsTurnedToTheOnlyCoast() {
        TerrainGrid northCoast = grid((x, z) -> z < -200 ? new TerrainSampler.Column(40, true, Ground.SEA)
                : new TerrainSampler.Column(72 + (int) Math.round(3 * Math.sin(x / 50.0)), false, Ground.LAND));
        for (long seed = 0; seed < 6; seed++) {
            SitePlanner.Outcome outcome = plan(northCoast, seed, Waterfront.REQUIRED);
            assertEquals(SitePlanner.STRICT, outcome.run().relaxation(), "seed " + seed);
            PlannedSite town = outcome.sites().get(0);
            assertEquals(java.util.Optional.of(Direction.NORTH), town.waterfront());
            assertEquals(net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90, town.rotation());
            assertEquals(256, town.bounds().sizeX());
            assertEquals(272, town.bounds().sizeZ());
            assertEquals(town.bounds().inflate(32), town.region(), "the region of a turned build is its turned bounds plus the blend");
            // Strip rows sample z = minZ + 8 + 16k; the coast (z = -200) must cross the strip, with the land part all dry.
            assertTrue(town.bounds().minZ() >= -320 && town.bounds().minZ() <= -256, "the coast runs through the strip: " + town.bounds());
            assertTrue(town.predicted().stripSea().orElseThrow() >= V01Sites.TOWN_MIN_STRIP_SEA);
            assertEquals(1.0, town.predicted().land(), 1e-9, "the land part is all dry");
        }
    }

    // A map too rough for the strict rules still gets both sites, through a recorded fallback step.
    @Test
    void roughTerrainFallsBackButKeepsBothSites() {
        TerrainSampler base = island(ISLAND_HALF);
        TerrainSampler rough = (x, z) -> {
            TerrainSampler.Column c = base.sample(x, z);
            int bump = ((x >> 4) + (z >> 4)) % 2 == 0 ? 0 : 34;
            return new TerrainSampler.Column(c.surfaceY() + (c.dryLand() ? bump : 0), c.water(), c.ground());
        };
        PlanRequest request = V01Sites.request(AREA, Waterfront.PREFERRED);
        SitePlanner.Outcome outcome = SitePlanner.plan(request, grid(rough), 3L, SPAWN);
        assertEquals(SitePlanner.LOOSER_TERRAIN, outcome.run().relaxation(), "a 34-block checkerboard fails strict relief (32) but not looser (44)");
        assertEquals(2, outcome.sites().size());
        for (int k = 0; k < 2; k++) {
            assertEquals(SitePlanner.relax(request, SitePlanner.LOOSER_TERRAIN).sites().get(k).rules(), outcome.sites().get(k).rules(),
                    "each site records the rules it was placed under");
        }
    }

    // A gap the map has no room for: looser terrain can't help, the layout step halves it, and the half is kept.
    @Test
    void anImpossibleGapLoosensTheLayout() {
        PlanRequest base = V01Sites.request(AREA, Waterfront.PREFERRED);
        PlanRequest wide = base.with(base.sites(), List.of(new SiteSpacing(V01Sites.TOWN, V01Sites.EXTERNAL_POI, 600, 900)));
        SitePlanner.Outcome outcome = SitePlanner.plan(wide, ISLAND, 5L, SPAWN);
        assertEquals(SitePlanner.LOOSER_LAYOUT, outcome.run().relaxation());
        assertTrue(outcome.sites().get(0).waterfront().isEmpty(), "the layout step drops a preferred waterfront");
        assertTrue(outcome.sites().get(0).region().gapTo(outcome.sites().get(1).region()) >= 300, "the halved gap is kept");
    }

    // All sea: nothing meets any rule, and the last resort still places both sites, apart, inside the map.
    @Test
    void lastResortAlwaysPlacesEverySite() {
        TerrainGrid sea = grid((x, z) -> new TerrainSampler.Column(30, true, Ground.SEA));
        SitePlanner.Outcome outcome = plan(sea, 9L, Waterfront.REQUIRED);
        assertEquals(SitePlanner.LAST_RESORT, outcome.run().relaxation());
        assertEquals(2, outcome.sites().size());
        assertFalse(outcome.sites().get(0).region().intersects(outcome.sites().get(1).region()));
        assertEquals(Waterfront.NONE, outcome.sites().get(0).rules().waterfront(), "only the last resort drops a required waterfront");
        assertEquals(outcome, plan(sea, 9L, Waterfront.REQUIRED), "fallback is deterministic too");
    }

    @Test
    void sitesThatCannotFitAreAConfigurationError() {
        SiteRules huge = new SiteRules(new Footprint(640, 640, 0), Waterfront.NONE, TerrainLimits.ANY, 0);
        PlanRequest tooBig = new PlanRequest(AREA, 32, List.of(new SiteRequirement("a", SiteKind.SETTLEMENT, V01Sites.TOWN_CATALOG_ID, huge),
                new SiteRequirement("b", SiteKind.SETTLEMENT, V01Sites.TOWN_CATALOG_ID, huge)), List.of());
        assertThrows(IllegalStateException.class, () -> SitePlanner.plan(tooBig, ISLAND, 1L, SPAWN));
    }

    @Test
    void relaxationStepsLoosenInOrder() {
        PlanRequest preferred = V01Sites.request(AREA, Waterfront.PREFERRED);
        PlanRequest required = V01Sites.request(AREA, Waterfront.REQUIRED);
        assertEquals(preferred, SitePlanner.relax(preferred, SitePlanner.STRICT));
        assertEquals(Waterfront.PREFERRED, SitePlanner.relax(preferred, SitePlanner.LOOSER_TERRAIN).sites().get(0).rules().waterfront());
        assertEquals(Waterfront.NONE, SitePlanner.relax(preferred, SitePlanner.LOOSER_LAYOUT).sites().get(0).rules().waterfront());
        assertEquals(Waterfront.REQUIRED, SitePlanner.relax(required, SitePlanner.LOOSER_LAYOUT).sites().get(0).rules().waterfront());
        assertEquals(Waterfront.NONE, SitePlanner.relax(required, SitePlanner.LAST_RESORT).sites().get(0).rules().waterfront());
        assertEquals(V01Sites.MIN_WILDERNESS_GAP / 2, SitePlanner.relax(preferred, SitePlanner.LOOSER_LAYOUT).spacings().get(0).minGap());
        assertEquals(0, SitePlanner.relax(preferred, SitePlanner.LAST_RESORT).spacings().get(0).minGap());
        assertEquals(TerrainLimits.ANY, SitePlanner.relax(preferred, SitePlanner.LAST_RESORT).sites().get(1).rules().limits());
    }

    @Test
    void theGridMustCoverTheMap() {
        TerrainGrid small = TerrainGrid.sample(island(ISLAND_HALF), new BlockRect(-256, -256, 255, 255));
        assertThrows(IllegalArgumentException.class, () -> plan(small, 1L, Waterfront.NONE));
    }

    // The planner is settlement-count agnostic: a request with three sites plans three, all apart.
    @Test
    void anyNumberOfSitesCanBePlanned() {
        PlanRequest base = V01Sites.request(AREA, Waterfront.NONE);
        SiteRequirement second = new SiteRequirement("second_town", SiteKind.SETTLEMENT, V01Sites.TOWN_CATALOG_ID,
                new SiteRules(new Footprint(128, 128, 16), Waterfront.NONE, V01Sites.TOWN_TERRAIN, 0));
        PlanRequest three = base.with(List.of(base.sites().get(0), base.sites().get(1), second), base.spacings());
        List<PlannedSite> sites = SitePlanner.plan(three, ISLAND, 11L, SPAWN).sites();
        assertEquals(3, sites.size());
        for (int a = 0; a < 3; a++) {
            for (int b = a + 1; b < 3; b++) {
                assertFalse(sites.get(a).region().intersects(sites.get(b).region()));
            }
        }
        assertNotEquals(sites.get(0).id(), sites.get(2).id());
    }

    @Test
    void requestsRejectDuplicateAndUnknownSites() {
        PlanRequest base = V01Sites.request(AREA);
        assertThrows(IllegalArgumentException.class, () -> base.with(List.of(base.sites().get(0), base.sites().get(0)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> base.with(base.sites(), List.of(new SiteSpacing("town", "nowhere", 0, 10))));
        assertThrows(IllegalArgumentException.class, () -> new Footprint(100, 96, 0), "footprints are whole chunks");
    }
}
