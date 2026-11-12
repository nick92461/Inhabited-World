package io.github.nick92461.inhabitedworld.worldgen.planner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/*
  The rules one site is planned under: its footprint, whether it needs a waterfront, the terrain it accepts, and how
  far its semantic region must stay from vanilla's spawn search origin (0 = no rule). A planned site records the
  rules it was actually placed under (after any fallback step), so verification and later readers judge it by
  those, not by whatever a newer mod version would ask for (canon 02 section 7).
 */
public record SiteRules(Footprint footprint, Waterfront waterfront, TerrainLimits limits, int spawnClearance) {
    public static final Codec<SiteRules> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Footprint.CODEC.fieldOf("footprint").forGetter(SiteRules::footprint),
                    Waterfront.CODEC.fieldOf("waterfront").forGetter(SiteRules::waterfront),
                    TerrainLimits.CODEC.fieldOf("terrain").forGetter(SiteRules::limits),
                    Codec.INT.fieldOf("spawn_clearance").forGetter(SiteRules::spawnClearance))
            .apply(i, SiteRules::new));

    public SiteRules withLimits(TerrainLimits limits) {
        return new SiteRules(this.footprint, this.waterfront, limits, this.spawnClearance);
    }

    public SiteRules withWaterfront(Waterfront waterfront) {
        return new SiteRules(this.footprint, waterfront, this.limits, this.spawnClearance);
    }

    public SiteRules withSpawnClearance(int spawnClearance) {
        return new SiteRules(this.footprint, this.waterfront, this.limits, spawnClearance);
    }
}
