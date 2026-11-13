package io.github.nick92461.inhabitedworld.worldgen.planner;

import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/*
  The terrain under one site, measured on the chunk lattice (SiteEvaluator). The same numbers are recorded twice in
  the WorldPlan: as the planner predicted them from the generator's noise, and as verification found them in the
  generated chunks.

  - land: share of dry-land columns under the build (for a waterfront site, under the build minus its waterfront strip)
  - relief: 10th-to-90th percentile spread of dry-land surface heights, in blocks
  - medianY: median dry-land surface height
  - mountain: share of mountain-biome columns
  - sea: for a waterfront site, the share of open sea just beyond its waterfront side
  - stripSea: for a waterfront site, the share of open sea inside its waterfront strip (absent in plans made by planner
    version 1, which did not measure it)
 */
public record SiteMetrics(double land, int relief, int medianY, double mountain, Optional<Double> sea, Optional<Double> stripSea) {
    public static final Codec<SiteMetrics> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.DOUBLE.fieldOf("land").forGetter(SiteMetrics::land),
                    Codec.INT.fieldOf("relief").forGetter(SiteMetrics::relief),
                    Codec.INT.fieldOf("median_y").forGetter(SiteMetrics::medianY),
                    Codec.DOUBLE.fieldOf("mountain").forGetter(SiteMetrics::mountain),
                    Codec.DOUBLE.optionalFieldOf("sea").forGetter(SiteMetrics::sea),
                    Codec.DOUBLE.optionalFieldOf("strip_sea").forGetter(SiteMetrics::stripSea))
            .apply(i, SiteMetrics::new));

    /** Metrics without a strip measurement (a site without a waterfront, or one measured by planner version 1). */
    public SiteMetrics(double land, int relief, int medianY, double mountain, Optional<Double> sea) {
        this(land, relief, medianY, mountain, sea, Optional.empty());
    }

    /** Relief reported when too few dry-land columns exist to measure it. */
    public static final int UNMEASURABLE_RELIEF = 9999;

    public String describe() {
        return String.format(Locale.ROOT, "land %.0f%%, relief %d, median y %d, mountain %.0f%%%s", this.land * 100, this.relief, this.medianY,
                this.mountain * 100, this.stripSea.map(s -> String.format(Locale.ROOT, ", sea in waterfront strip %.0f%%", s * 100)).orElse("")
                        + this.sea.map(s -> String.format(Locale.ROOT, ", sea beyond waterfront %.0f%%", s * 100)).orElse(""));
    }
}
