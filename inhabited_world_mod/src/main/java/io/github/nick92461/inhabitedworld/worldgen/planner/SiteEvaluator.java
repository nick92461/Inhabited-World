package io.github.nick92461.inhabitedworld.worldgen.planner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

/*
  Measures and judges the terrain under one site on a TerrainGrid. Planning and verification both use it, on grids
  from different samplers (noise before generation, chunks after), so a site is judged the same way both times.

  Waterfront sites: the strip of the build nearest the sea (WATERFRONT_DEPTH deep, where a harbour or shore goes) is
  left out of the land measurements, and the same depth just beyond that side must be mostly open sea.

  These numbers are part of the planning algorithm: changing them changes which sites a seed gets, so a change
  needs a new SitePlanner.VERSION.
 */
public final class SiteEvaluator {
    /** The depth of the waterfront strip inside the build, and of the sea checked beyond it: three chunks. */
    public static final int WATERFRONT_DEPTH = 48;

    /** The share of open sea required just beyond a waterfront side. */
    public static final double MIN_SEA = 0.6;

    private SiteEvaluator() {
    }

    /** Terrain metrics for a build at `bounds` (chunk-aligned, inside the grid), with an optional waterfront side. */
    public static SiteMetrics measure(TerrainGrid grid, BlockRect bounds, @Nullable Direction waterfront) {
        int i0 = grid.cellX(bounds.minX());
        int j0 = grid.cellZ(bounds.minZ());
        int cellsX = bounds.sizeX() / TerrainGrid.STEP;
        int cellsZ = bounds.sizeZ() / TerrainGrid.STEP;
        int strip = WATERFRONT_DEPTH / TerrainGrid.STEP;

        int[] dryHeights = new int[cellsX * cellsZ];
        int dry = 0;
        int counted = 0;
        int mountain = 0;
        for (int dj = 0; dj < cellsZ; dj++) {
            for (int di = 0; di < cellsX; di++) {
                if (waterfront != null && inWaterfrontStrip(di, dj, cellsX, cellsZ, strip, waterfront)) {
                    continue;
                }
                TerrainSampler.Column column = grid.column(i0 + di, j0 + dj);
                if (column == null) {
                    throw new IllegalArgumentException("site " + bounds + " reaches outside the terrain grid " + grid.bounds());
                }
                counted++;
                if (column.dryLand()) {
                    dryHeights[dry++] = column.surfaceY();
                }
                if (column.ground() == Ground.MOUNTAIN) {
                    mountain++;
                }
            }
        }

        int[] heights = Arrays.copyOf(dryHeights, dry);
        Arrays.sort(heights);
        int relief = dry < 2 ? SiteMetrics.UNMEASURABLE_RELIEF : percentile(heights, 0.9, true) - percentile(heights, 0.1, false);
        int median = dry == 0 ? Integer.MIN_VALUE : heights[(dry - 1) / 2];

        Optional<Double> sea = Optional.empty();
        if (waterfront != null) {
            sea = Optional.of(seaBeyond(grid, i0, j0, cellsX, cellsZ, strip, waterfront));
        }
        return new SiteMetrics(counted == 0 ? 0.0 : dry / (double) counted, relief, median, counted == 0 ? 0.0 : mountain / (double) counted, sea);
    }

    /** Whether the metrics meet the limits (and, for a waterfront site, the sea rule). */
    public static boolean meets(SiteMetrics metrics, TerrainLimits limits) {
        return metrics.land() >= limits.minLand()
                && metrics.relief() <= limits.maxRelief()
                && metrics.medianY() >= limits.minSurfaceY()
                && metrics.medianY() <= limits.maxSurfaceY()
                && metrics.mountain() <= limits.maxMountain()
                && metrics.sea().map(s -> s >= MIN_SEA).orElse(true);
    }

    /** What falls short of the limits, in words (empty when nothing does); minSea applies to waterfront sites. */
    public static List<String> shortfalls(SiteMetrics metrics, TerrainLimits limits, double minSea) {
        List<String> problems = new ArrayList<>();
        if (metrics.land() < limits.minLand()) {
            problems.add(String.format(Locale.ROOT, "only %.0f%% dry land (needs %.0f%%)", metrics.land() * 100, limits.minLand() * 100));
        }
        if (metrics.relief() > limits.maxRelief()) {
            problems.add("relief " + metrics.relief() + " blocks (at most " + limits.maxRelief() + ")");
        }
        if (metrics.medianY() < limits.minSurfaceY() || metrics.medianY() > limits.maxSurfaceY()) {
            problems.add("median surface y " + (metrics.medianY() == Integer.MIN_VALUE ? "none" : String.valueOf(metrics.medianY()))
                    + " (allowed " + limits.minSurfaceY() + " to " + limits.maxSurfaceY() + ")");
        }
        if (metrics.mountain() > limits.maxMountain()) {
            problems.add(String.format(Locale.ROOT, "%.0f%% mountain biomes (at most %.0f%%)", metrics.mountain() * 100, limits.maxMountain() * 100));
        }
        metrics.sea().filter(s -> s < minSea).ifPresent(s ->
                problems.add(String.format(Locale.ROOT, "only %.0f%% open sea beyond the waterfront (needs %.0f%%)", s * 100, minSea * 100)));
        return problems;
    }

    /**
     * Lower is better: dry, flat, low, non-mountain ground, and for a waterfront open sea in front. Water weighs most:
     * relief can be terraced, but a build can't stand in a lake (this matters most for the last resort).
     */
    public static double score(SiteMetrics metrics) {
        double score = Math.min(metrics.relief(), 200) + 150.0 * (1.0 - metrics.land()) + 40.0 * metrics.mountain();
        if (metrics.medianY() != Integer.MIN_VALUE) {
            score += 0.5 * Math.max(0, metrics.medianY() - 100);
        }
        score += metrics.sea().map(s -> 20.0 * (1.0 - s)).orElse(0.0);
        return score;
    }

    private static boolean inWaterfrontStrip(int di, int dj, int cellsX, int cellsZ, int strip, Direction side) {
        return switch (side) {
            case NORTH -> dj < strip;
            case SOUTH -> dj >= cellsZ - strip;
            case WEST -> di < strip;
            case EAST -> di >= cellsX - strip;
            default -> throw new IllegalArgumentException("a waterfront faces a horizontal direction, not " + side);
        };
    }

    // The share of open sea in the strip just beyond the build's waterfront side, as wide as that side.
    private static double seaBeyond(TerrainGrid grid, int i0, int j0, int cellsX, int cellsZ, int strip, Direction side) {
        int sea = 0;
        int total = 0;
        int along = side.getAxis() == Direction.Axis.Z ? cellsX : cellsZ;
        for (int a = 0; a < along; a++) {
            for (int d = 1; d <= strip; d++) {
                int i;
                int j;
                switch (side) {
                    case NORTH -> {
                        i = i0 + a;
                        j = j0 - d;
                    }
                    case SOUTH -> {
                        i = i0 + a;
                        j = j0 + cellsZ - 1 + d;
                    }
                    case WEST -> {
                        i = i0 - d;
                        j = j0 + a;
                    }
                    case EAST -> {
                        i = i0 + cellsX - 1 + d;
                        j = j0 + a;
                    }
                    default -> throw new IllegalArgumentException(side.toString());
                }
                total++;
                TerrainSampler.Column column = grid.column(i, j);
                if (column != null && column.sea()) {
                    sea++;
                }
            }
        }
        return sea / (double) total;
    }

    private static int percentile(int[] sorted, double p, boolean up) {
        double index = p * (sorted.length - 1);
        return sorted[(int) (up ? Math.ceil(index) : Math.floor(index))];
    }
}
