package io.github.nick92461.inhabitedworld.worldgen.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        SiteMetrics m = SiteEvaluator.measure(GRID, BlockRect.ofSize(-128, -64, 128, 128), null);
        assertEquals(7 / 8.0, m.land(), 1e-9);
        assertEquals(24, m.relief(), "heights 64, 72, 80, 88 by column: p10 64, p90 88");
        assertEquals(72, m.medianY());
        assertEquals(14 / 64.0, m.mountain(), 1e-9, "dry columns at x < -96 are mountain (2 columns, 7 rows)");
        assertTrue(m.sea().isEmpty());
    }

    @Test
    void aWaterfrontSideLeavesItsStripOutAndCountsTheSeaBeyond() {
        BlockRect bounds = BlockRect.ofSize(-128, -128, 128, 96);
        SiteMetrics east = SiteEvaluator.measure(GRID, bounds, Direction.EAST);
        assertEquals(1.0, east.sea().orElseThrow(), 1e-9, "beyond the east side lies only sea");
        assertEquals(1.0, east.land(), 1e-9, "the build's dry part, without its 3-chunk waterfront strip");
        SiteMetrics west = SiteEvaluator.measure(GRID, bounds, Direction.WEST);
        assertEquals(0.0, west.sea().orElseThrow(), 1e-9, "beyond the west side lies land");
        assertTrue(SiteEvaluator.meets(east, V01Sites.POI_TERRAIN.relaxed(0, 0, 0, 0.5)));
        assertFalse(SiteEvaluator.meets(west, TerrainLimits.ANY), "a waterfront without sea never meets the rules");
    }

    @Test
    void shortfallsNameEveryFailedRule() {
        SiteMetrics bad = new SiteMetrics(0.5, 50, 150, 0.9, java.util.Optional.of(0.1));
        assertEquals(5, SiteEvaluator.shortfalls(bad, V01Sites.TOWN_TERRAIN, SiteEvaluator.MIN_SEA).size());
        SiteMetrics good = new SiteMetrics(1.0, 10, 70, 0.0, java.util.Optional.of(0.9));
        assertTrue(SiteEvaluator.shortfalls(good, V01Sites.TOWN_TERRAIN, SiteEvaluator.MIN_SEA).isEmpty());
        assertTrue(SiteEvaluator.meets(good, V01Sites.TOWN_TERRAIN));
        assertTrue(SiteEvaluator.score(good) < SiteEvaluator.score(bad));
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
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
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
}
