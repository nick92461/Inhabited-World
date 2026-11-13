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

  Waterfront sites: the strip of the build along its waterfront side (the site's waterfront depth deep, where its
  harbour or shore goes) is left out of the land measurements, and its share of open sea is measured on its own
  (SiteRules.minStripSea says how much a site needs there). The same depth just beyond that side must be mostly open
  sea (MIN_SEA).

  These numbers are part of the planning algorithm: changing them changes which sites a seed gets, so a change
  needs a new SitePlanner.VERSION.
 */
public final class SiteEvaluator {
    /** The share of open sea required just beyond a waterfront side. */
    public static final double MIN_SEA = 0.6;

    private SiteEvaluator() {
    }

    /** Terrain metrics for a site measured by its own rules: its bounds, waterfront side and waterfront depth. */
    public static SiteMetrics measure(TerrainGrid grid, PlannedSite site) {
        return measure(grid, site.bounds(), site.waterfront().orElse(null), site.rules().waterfrontDepth());
    }

    /**
     * Terrain metrics for a build at `bounds` (chunk-aligned, inside the grid), with an optional waterfront side whose
     * strip, and the sea checked beyond it, are `depth` blocks deep (a whole number of chunks).
     */
    public static SiteMetrics measure(TerrainGrid grid, BlockRect bounds, @Nullable Direction waterfront, int depth) {
        int i0 = grid.cellX(bounds.minX());
        int j0 = grid.cellZ(bounds.minZ());
        int cellsX = bounds.sizeX() / TerrainGrid.STEP;
        int cellsZ = bounds.sizeZ() / TerrainGrid.STEP;
        int strip = depth / TerrainGrid.STEP;
        if (waterfront != null && (depth <= 0 || depth % TerrainGrid.STEP != 0
                || strip >= (waterfront.getAxis() == Direction.Axis.X ? cellsX : cellsZ))) {
            throw new IllegalArgumentException("a " + depth + "-block waterfront strip does not fit a site " + bounds);
        }

        int[] dryHeights = new int[cellsX * cellsZ];
        int dry = 0;
        int counted = 0;
        int mountain = 0;
        int stripCells = 0;
        int stripSea = 0;
        for (int dj = 0; dj < cellsZ; dj++) {
            for (int di = 0; di < cellsX; di++) {
                TerrainSampler.Column column = grid.column(i0 + di, j0 + dj);
                if (column == null) {
                    throw new IllegalArgumentException("site " + bounds + " reaches outside the terrain grid " + grid.bounds());
                }
                if (waterfront != null && inWaterfrontStrip(di, dj, cellsX, cellsZ, strip, waterfront)) {
                    stripCells++;
                    if (column.sea()) {
                        stripSea++;
                    }
                    continue;
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
        Optional<Double> inStrip = Optional.empty();
        if (waterfront != null) {
            sea = Optional.of(seaBeyond(grid, i0, j0, cellsX, cellsZ, strip, waterfront));
            inStrip = Optional.of(stripSea / (double) stripCells);
        }
        return new SiteMetrics(counted == 0 ? 0.0 : dry / (double) counted, relief, median, counted == 0 ? 0.0 : mountain / (double) counted, sea, inStrip);
    }

    /** Whether the metrics meet the limits (and, for a waterfront site, the sea rule beyond it; no rule inside its strip). */
    public static boolean meets(SiteMetrics metrics, TerrainLimits limits) {
        return meets(metrics, limits, 0.0);
    }

    /** Whether the metrics meet a site's rules. */
    public static boolean meets(SiteMetrics metrics, SiteRules rules) {
        return meets(metrics, rules.limits(), rules.minStripSea());
    }

    /** Whether the metrics meet the limits and, for a waterfront site, both sea rules: inside its strip and beyond. */
    public static boolean meets(SiteMetrics metrics, TerrainLimits limits, double minStripSea) {
        return metrics.land() >= limits.minLand()
                && metrics.relief() <= limits.maxRelief()
                && metrics.medianY() >= limits.minSurfaceY()
                && metrics.medianY() <= limits.maxSurfaceY()
                && metrics.mountain() <= limits.maxMountain()
                && metrics.sea().map(s -> s >= MIN_SEA).orElse(true)
                && metrics.stripSea().map(s -> s >= minStripSea).orElse(true);
    }

    /**
     * What falls short of the limits, in words (empty when nothing does); minSea (beyond the waterfront side) and
     * minStripSea (inside the waterfront strip) apply to waterfront sites.
     */
    public static List<String> shortfalls(SiteMetrics metrics, TerrainLimits limits, double minSea, double minStripSea) {
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
        metrics.stripSea().filter(s -> s < minStripSea).ifPresent(s ->
                problems.add(String.format(Locale.ROOT, "only %.0f%% open sea in the waterfront strip (needs %.0f%%)", s * 100, minStripSea * 100)));
        metrics.sea().filter(s -> s < minSea).ifPresent(s ->
                problems.add(String.format(Locale.ROOT, "only %.0f%% open sea beyond the waterfront (needs %.0f%%)", s * 100, minSea * 100)));
        return problems;
    }

    /**
     * Lower is better: dry, flat, low, non-mountain ground, and for a waterfront open sea in its strip and in front.
     * Water under the land part weighs most: relief can be terraced, but a build can't stand in a lake (this matters
     * most for the last resort).
     */
    public static double score(SiteMetrics metrics) {
        double score = Math.min(metrics.relief(), 200) + 150.0 * (1.0 - metrics.land()) + 40.0 * metrics.mountain();
        if (metrics.medianY() != Integer.MIN_VALUE) {
            score += 0.5 * Math.max(0, metrics.medianY() - 100);
        }
        score += metrics.sea().map(s -> 20.0 * (1.0 - s)).orElse(0.0);
        score += metrics.stripSea().map(s -> 20.0 * (1.0 - s)).orElse(0.0);
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
