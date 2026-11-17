package io.github.nick92461.inhabitedworld.town;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import io.github.nick92461.inhabitedworld.worldgen.planner.BlockRect;

import org.junit.jupiter.api.Test;

// Phase 3: the blend between a placed town's ground and the generated ground around its region (TownBlend).
class TownBlendTest {
    private static final BlockRect AREA = BlockRect.ofSize(100, -50, 40, 30);

    // A grid whose outer ring and an inner block are fixed by `edge`; everything else is free.
    private static TownBlend.Field solve(java.util.function.IntBinaryOperator edge, boolean innerBlock) {
        int w = AREA.sizeX();
        byte[] kind = new byte[w * AREA.sizeZ()];
        double[] h = new double[kind.length];
        for (int z = 0; z < AREA.sizeZ(); z++) {
            for (int x = 0; x < w; x++) {
                int i = z * w + x;
                boolean ring = x == 0 || z == 0 || x == w - 1 || z == AREA.sizeZ() - 1;
                boolean inner = innerBlock && x >= 15 && x < 25 && z >= 10 && z < 20;
                kind[i] = ring || inner ? TownBlend.FIXED : TownBlend.FREE;
                h[i] = ring || inner ? edge.applyAsInt(x, z) : 0;
            }
        }
        return TownBlend.solve(AREA, kind, h);
    }

    @Test
    void flatEdgesGiveFlatGround() {
        TownBlend.Field f = solve((x, z) -> 70, true);
        for (int z = AREA.minZ(); z <= AREA.maxZ(); z++) {
            for (int x = AREA.minX(); x <= AREA.maxX(); x++) {
                assertEquals(70, f.ground(x, z));
            }
        }
        assertEquals(0, TownBlend.steepestStep(f));
    }

    // A plane is harmonic: edges on a slope give exactly that slope (the quay-to-back-street climb along an edge).
    @Test
    void aSlopeStaysASlope() {
        TownBlend.Field f = solve((x, z) -> 60 + x, false);
        for (int z = AREA.minZ() + 1; z < AREA.maxZ(); z++) {
            for (int x = AREA.minX() + 1; x < AREA.maxX(); x++) {
                assertEquals(60 + (x - AREA.minX()), f.at(x, z), 0.05, "at " + x + ", " + z);
            }
        }
        assertEquals(1, TownBlend.steepestStep(f));
    }

    // No cliffs or walls of its own: every blended height lies between the lowest and highest edge (maximum principle).
    @Test
    void theBlendNeverLeavesTheRangeOfItsEdges() {
        Random random = new Random(20261029L);
        int[] edge = new int[AREA.sizeX() * AREA.sizeZ()];
        for (int i = 0; i < edge.length; i++) {
            edge[i] = 40 + random.nextInt(60);
        }
        TownBlend.Field f = solve((x, z) -> edge[z * AREA.sizeX() + x], true);
        for (int z = AREA.minZ(); z <= AREA.maxZ(); z++) {
            for (int x = AREA.minX(); x <= AREA.maxX(); x++) {
                assertTrue(f.ground(x, z) >= 40 && f.ground(x, z) <= 99, "height " + f.ground(x, z) + " at " + x + ", " + z);
            }
        }
        assertTrue(f.residual() <= TownBlend.TOLERANCE, "the solve converged: " + f.residual());
    }

    // Placement after a restart recomputes the blend: the same edges must give the same heights, bit for bit.
    @Test
    void theSameEdgesGiveTheSameHeights() {
        TownBlend.Field a = solve((x, z) -> 50 + (x * 7 + z * 3) % 20, true);
        TownBlend.Field b = solve((x, z) -> 50 + (x * 7 + z * 3) % 20, true);
        assertArrayEquals(a.heights(), b.heights());
        assertEquals(a.iterations(), b.iterations());
    }

    // A seabed edge meeting a land edge across a strip: the ground rises through the waterline without a step.
    @Test
    void seabedRisesToLandGradually() {
        TownBlend.Field f = solve((x, z) -> x < 20 ? 40 : 80, false);
        int steepest = TownBlend.steepestStep(f);
        assertTrue(steepest < 40, "the 40-block jump between edges is spread out, steepest step " + steepest);
    }

    // A single odd stored column (a pillar footing) doesn't set the edge height; blend columns aren't counted.
    @Test
    void edgeHeightsIgnoreASingleOddColumn() {
        int w = 7;
        byte[] zone = new byte[w * w];
        int[] ground = new int[w * w];
        java.util.Arrays.fill(zone, TownColumns.SUPPORT);
        java.util.Arrays.fill(ground, 50);
        ground[3 * w + 3] = 93;
        zone[3 * w + 4] = TownColumns.BLEND;
        ground[3 * w + 4] = -1;
        TownColumns columns = new TownColumns(w, w, zone, new int[w * w], new int[w * w], ground);
        assertEquals(50, TownBlend.edgeGround(columns, 3, 3));
        assertEquals(50, TownBlend.edgeGround(columns, 0, 0), "at a corner only the stored columns in reach count");
    }

    @Test
    void groundWithinOneBlockIsKept() {
        assertTrue(!TownBlend.needsRebuild(70, 70) && !TownBlend.needsRebuild(71, 70) && !TownBlend.needsRebuild(69, 70));
        assertTrue(TownBlend.needsRebuild(72, 70) && TownBlend.needsRebuild(40, 70));
    }

    @Test
    void aFreeCellOnTheGridsEdgeIsRefused() {
        byte[] kind = new byte[9];
        kind[0] = TownBlend.FREE;
        assertThrows(IllegalArgumentException.class, () -> TownBlend.solve(BlockRect.ofSize(0, 0, 3, 3), kind, new double[9]));
    }
}
