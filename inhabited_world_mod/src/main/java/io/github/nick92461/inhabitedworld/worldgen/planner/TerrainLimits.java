package io.github.nick92461.inhabitedworld.worldgen.planner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/*
  What "valid terrain" means for one site (canon 02 section 6: both sites "occupy valid terrain"), measured on the
  planner's chunk lattice inside the site's build bounds (SiteMetrics):

  - minLand: the share of sampled columns that are dry land (not sea, river, lake or flooded)
  - maxRelief: the spread of dry-land surface heights, from the 10th to the 90th percentile, in blocks
  - minSurfaceY / maxSurfaceY: the allowed median dry-land surface height (keeps sites off peaks)
  - maxMountain: the share of columns in mountain biomes (peaks, slopes, meadows)
 */
public record TerrainLimits(double minLand, int maxRelief, int minSurfaceY, int maxSurfaceY, double maxMountain) {
    public static final Codec<TerrainLimits> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.DOUBLE.fieldOf("min_land").forGetter(TerrainLimits::minLand),
                    Codec.INT.fieldOf("max_relief").forGetter(TerrainLimits::maxRelief),
                    Codec.INT.fieldOf("min_surface_y").forGetter(TerrainLimits::minSurfaceY),
                    Codec.INT.fieldOf("max_surface_y").forGetter(TerrainLimits::maxSurfaceY),
                    Codec.DOUBLE.fieldOf("max_mountain").forGetter(TerrainLimits::maxMountain))
            .apply(i, TerrainLimits::new));

    /** No terrain limit at all: the planner's last resort (SitePlanner). */
    public static final TerrainLimits ANY = new TerrainLimits(0.0, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, 1.0);

    /** Looser limits by the given amounts (the planner's first fallback step; verification's tolerance). */
    public TerrainLimits relaxed(double land, int relief, int surfaceY, double mountain) {
        return new TerrainLimits(Math.max(0.0, this.minLand - land), saturatingAdd(this.maxRelief, relief),
                saturatingAdd(this.minSurfaceY, -surfaceY), saturatingAdd(this.maxSurfaceY, surfaceY), Math.min(1.0, this.maxMountain + mountain));
    }

    private static int saturatingAdd(int a, int b) {
        long sum = (long) a + b;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, sum));
    }
}
