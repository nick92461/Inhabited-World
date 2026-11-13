package io.github.nick92461.inhabitedworld.worldgen.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

// Terrain measurement shared by planning and verification, and the geometry it rests on.
class SiteEvaluatorTest {
    // Land west of x = 0 at heights 60..90 by column, sea east of it; a river along z = 40..55.
    private static final TerrainSampler HALF = (x, z) -> {
        if (x >= 0) {
            return new TerrainSampler.Column(40, true, Ground.SEA);
        }
        if (z >= 40 && z < 56) {
            return new TerrainSampler.Column(60, true, Ground.RIVER);
        }
        return new TerrainSampler.Column(64 + Math.floorMod(x >> 4, 4) * 8, false, x < -96 ? Ground.MOUNTAIN : Ground.LAND);
    };
    private static final TerrainGrid GRID = TerrainGrid.sample(HALF, new BlockRect(-256, -128, 127, 127));

    @Test
    void measuresLandReliefHeightAndMountain() {
        // 8 x 8 chunks: x -128..-1, z -64..63: the river row (z 40..55) is one of 8 rows.
        SiteMetrics m = SiteEvaluator.measure(GRID, BlockRect.ofSize(-128, -64, 128, 128), null, 48);
        assertEquals(7 / 8.0, m.land(), 1e-9);
        assertEquals(24, m.relief(), "heights 64, 72, 80, 88 by column: p10 64, p90 88");
        assertEquals(72, m.medianY());
        assertEquals(14 / 64.0, m.mountain(), 1e-9, "dry columns at x < -96 are mountain (2 columns, 7 rows)");
        assertTrue(m.sea().isEmpty());
        assertTrue(m.stripSea().isEmpty());
    }

    @Test
    void aWaterfrontSideLeavesItsStripOutAndCountsTheSeaBeyond() {
        BlockRect bounds = BlockRect.ofSize(-128, -128, 128, 96);
        SiteMetrics east = SiteEvaluator.measure(GRID, bounds, Direction.EAST, 48);
        assertEquals(1.0, east.sea().orElseThrow(), 1e-9, "beyond the east side lies only sea");
        assertEquals(1.0, east.land(), 1e-9, "the build's dry part, without its 3-chunk waterfront strip");
        assertEquals(0.0, east.stripSea().orElseThrow(), 1e-9, "the strip itself (x -48..-1) is land");
        SiteMetrics west = SiteEvaluator.measure(GRID, bounds, Direction.WEST, 48);
        assertEquals(0.0, west.sea().orElseThrow(), 1e-9, "beyond the west side lies land");
        assertTrue(SiteEvaluator.meets(east, V01Sites.POI_TERRAIN.relaxed(0, 0, 0, 0.5)));
        assertFalse(SiteEvaluator.meets(east, V01Sites.POI_TERRAIN.relaxed(0, 0, 0, 0.5), 0.5), "a harbour strip on land fails a strip-sea rule");
        assertFalse(SiteEvaluator.meets(west, TerrainLimits.ANY), "a waterfront without sea never meets the rules");
    }

    // The waterfront depth is the site's own: a 3-chunk strip and a 1-chunk strip measure different things.
    @Test
    void theWaterfrontDepthIsPerSite() {
        // x -80..47: dry land west of x = 0, sea from x = 0 on (no river in z -128..-33).
        BlockRect bounds = BlockRect.ofSize(-80, -128, 128, 96);
        SiteMetrics deep = SiteEvaluator.measure(GRID, bounds, Direction.EAST, 48);
        assertEquals(1.0, deep.stripSea().orElseThrow(), 1e-9, "the 48-block strip x 0..47 is all sea");
        assertEquals(1.0, deep.land(), 1e-9, "x -80..-1 is all dry");
        assertTrue(SiteEvaluator.meets(deep, V01Sites.POI_TERRAIN.relaxed(0, 0, 0, 0.5), 0.5));
        SiteMetrics shallow = SiteEvaluator.measure(GRID, bounds, Direction.EAST, 16);
        assertEquals(1.0, shallow.stripSea().orElseThrow(), 1e-9);
        assertEquals(5 / 7.0, shallow.land(), 1e-9, "with a 1-chunk strip, two sea columns (x 0..31) count as the build's land part");
        assertThrows(IllegalArgumentException.class, () -> SiteEvaluator.measure(GRID, bounds, Direction.EAST, 128), "a strip as deep as the build");
        assertThrows(IllegalArgumentException.class, () -> SiteEvaluator.measure(GRID, bounds, Direction.EAST, 40), "strips are whole chunks");
    }

    @Test
    void shortfallsNameEveryFailedRule() {
        SiteMetrics bad = new SiteMetrics(0.5, 50, 150, 0.9, java.util.Optional.of(0.1), java.util.Optional.of(0.2));
        assertEquals(6, SiteEvaluator.shortfalls(bad, V01Sites.TOWN_TERRAIN, SiteEvaluator.MIN_SEA, V01Sites.TOWN_MIN_STRIP_SEA).size());
        SiteMetrics good = new SiteMetrics(1.0, 10, 70, 0.0, java.util.Optional.of(0.9), java.util.Optional.of(0.8));
        assertTrue(SiteEvaluator.shortfalls(good, V01Sites.TOWN_TERRAIN, SiteEvaluator.MIN_SEA, V01Sites.TOWN_MIN_STRIP_SEA).isEmpty());
        assertTrue(SiteEvaluator.meets(good, V01Sites.townRules(Waterfront.REQUIRED)));
        assertTrue(SiteEvaluator.score(good) < SiteEvaluator.score(bad));
        SiteMetrics dryHarbour = new SiteMetrics(1.0, 10, 70, 0.0, java.util.Optional.of(0.9), java.util.Optional.of(0.3));
        assertFalse(SiteEvaluator.meets(dryHarbour, V01Sites.townRules(Waterfront.REQUIRED)), "the Harbour's strip needs sea");
        assertTrue(SiteEvaluator.score(good) < SiteEvaluator.score(dryHarbour), "more sea in the strip scores better");
    }

    @Test
    void rectanglesMeasureGapsAndContainment() {
        BlockRect a = BlockRect.ofSize(0, 0, 16, 16);
        assertEquals(new BlockRect(0, 0, 15, 15), a);
        assertEquals(8.0, a.centerX());
        assertEquals(0.0, a.gapTo(BlockRect.ofSize(16, 0, 16, 16)), "touching rectangles have no gap");
        assertEquals(10.0, a.gapTo(BlockRect.ofSize(26, 0, 4, 4)));
        assertEquals(5.0, a.gapTo(BlockRect.ofSize(19, 20, 4, 4)), "3-4-5 diagonal");
        assertTrue(a.intersects(BlockRect.ofSize(15, 15, 4, 4)));
        assertFalse(a.intersects(BlockRect.ofSize(16, 0, 4, 4)));
        assertTrue(a.inflate(1).contains(a));
        assertEquals(0.0, a.distanceTo(5, 5));
        assertEquals(4.0, a.distanceTo(20, 8));
    }

    @Test
    void gridsCoverWholeChunksAndSampleTheirMiddles() {
        TerrainGrid grid = TerrainGrid.sample((x, z) -> new TerrainSampler.Column(x, false, Ground.LAND), new BlockRect(-32, 0, 31, 15));
        assertEquals(4, grid.columnCount());
        assertEquals(-24, grid.column(0, 0).surfaceY(), "column 0 samples block x -32 + 8");
        assertEquals(null, grid.column(4, 0));
        assertThrows(IllegalArgumentException.class,
                () -> TerrainGrid.sample(HALF, new BlockRect(-30, 0, 31, 15)), "grids start on a chunk edge");
    }

    @Test
    void theV01RequestFitsTheMap() {
        PlanRequest request = V01Sites.request(io.github.nick92461.inhabitedworld.worldgen.PlayableArea.V0_1);
        assertEquals(2, request.sites().size());
        assertEquals(SiteKind.SETTLEMENT, request.sites().get(0).kind());
        assertEquals(SiteKind.POI, request.sites().get(1).kind());
        assertEquals(V01Sites.TOWN_WATERFRONT, request.sites().get(0).rules().waterfront());
        BlockRect usable = request.usableArea();
        assertEquals(new BlockRect(-480, -480, 479, 479), usable);
        int needed = V01Sites.TOWN_FOOTPRINT.sizeX() + 2 * V01Sites.TOWN_FOOTPRINT.blend() + V01Sites.MIN_WILDERNESS_GAP
                + V01Sites.POI_FOOTPRINT.sizeX() + 2 * V01Sites.POI_FOOTPRINT.blend();
        assertTrue(needed <= usable.sizeX(), "town, gap and POI fit side by side across the map");
    }

    /*
      The Harbour's numbers (survey REPORT.md section 4), and the margins its sea check needs: a build stays
      EDGE_MARGIN plus its blend border inside the world border, so the sea checked beyond its waterfront reaches past
      the border by at most its waterfront depth minus that, which must stay inside the pre-generated ocean ring
      (12 chunks, Pregeneration.OCEAN_RING_CHUNKS; that class registers a ticket type on load, so the number is
      repeated here), so verification reads only pre-generated chunks. The planner's own grid covers it too.
     */
    @Test
    void theHarbourFitsTheMapAndItsSeaCheckStaysInThePreGeneratedArea() {
        PlanRequest request = V01Sites.request(io.github.nick92461.inhabitedworld.worldgen.PlayableArea.V0_1);
        SiteRules town = request.sites().get(0).rules();
        assertEquals(new Footprint(272, 256, 32), town.footprint(), "17 x 16 chunks plus the 32-block blend border");
        assertEquals(Waterfront.REQUIRED, town.waterfront());
        assertEquals(java.util.Optional.of(Direction.EAST), town.seaSide());
        assertEquals(112, town.waterfrontDepth());
        assertEquals(32, town.limits().maxRelief());
        assertEquals("inhabited_world:drachenfels/d1_harbour", request.sites().get(0).catalogId().toString());
        int ringBlocks = 12 * 16;
        for (SiteRequirement site : request.sites()) {
            int pastBorder = site.rules().waterfrontDepth() - request.edgeMargin() - site.rules().footprint().blend();
            assertTrue(pastBorder <= ringBlocks, site.id() + "'s sea check reaches " + pastBorder + " blocks past the border");
        }
        BlockRect map = new BlockRect(-512, -512, 511, 511);
        assertTrue(SitePlanner.samplingArea(request).contains(map.inflate(town.waterfrontDepth())), "the planner samples the sea beyond any waterfront");
    }

    // A build with a sea side is turned so that side faces each compass direction; the rotation is Minecraft's.
    @Test
    void aBuildWithASeaSideIsTurnedToFaceTheSea() {
        java.util.List<SitePlanner.Orientation> orientations = SitePlanner.orientations(V01Sites.townRules(Waterfront.REQUIRED));
        assertEquals(java.util.List.of(
                new SitePlanner.Orientation(net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90, Direction.NORTH),
                new SitePlanner.Orientation(net.minecraft.world.level.block.Rotation.NONE, Direction.EAST),
                new SitePlanner.Orientation(net.minecraft.world.level.block.Rotation.CLOCKWISE_90, Direction.SOUTH),
                new SitePlanner.Orientation(net.minecraft.world.level.block.Rotation.CLOCKWISE_180, Direction.WEST)), orientations);
        assertEquals(256, PlannedSite.rotatedSizeX(V01Sites.TOWN_FOOTPRINT, net.minecraft.world.level.block.Rotation.CLOCKWISE_90));
        assertEquals(272, PlannedSite.rotatedSizeZ(V01Sites.TOWN_FOOTPRINT, net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90));
        assertEquals(272, PlannedSite.rotatedSizeX(V01Sites.TOWN_FOOTPRINT, net.minecraft.world.level.block.Rotation.CLOCKWISE_180));
        assertEquals(2, SitePlanner.orientations(V01Sites.townRules(Waterfront.NONE)).size(), "an inland Harbour may lie either way round");
        assertEquals(1, SitePlanner.orientations(new SiteRules(V01Sites.POI_FOOTPRINT, Waterfront.NONE, V01Sites.POI_TERRAIN, 0)).size(), "a square POI is never turned");
        assertEquals(4, SitePlanner.orientations(new SiteRules(new Footprint(256, 256, 32), Waterfront.PREFERRED, V01Sites.TOWN_TERRAIN, 0)).stream()
                .filter(o -> o.rotation() == net.minecraft.world.level.block.Rotation.NONE).count(), "planner version 1's placeholder: any side, unrotated");
    }
}
