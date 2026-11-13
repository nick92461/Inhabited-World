package io.github.nick92461.inhabitedworld.worldgen.planner;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Direction;

/*
  The rules one site is planned under (a planned site records the rules it was actually placed under, after any
  fallback step, so verification and later readers judge it by those, not by whatever a newer mod version would ask
  for: canon 02 section 7):

  - footprint: the build's size in its own (template) orientation, plus its blend border
  - waterfront: whether the site needs one side on the sea
  - seaSide: which side of the build, in its own orientation, holds its waterfront (Drachenfels' Harbour: east). The
    planner rotates the build so that side faces the sea, and records the rotation. Empty: the build has no fixed
    sea side, so any of its sides may face the sea without rotating it (the square placeholder of planner version 1).
  - waterfrontDepth: how deep the build's waterfront strip is (along its sea side, inside the build), and how far
    beyond that side the sea is checked. Planner version 1 used 48 for every site.
  - minStripSea: the least share of open sea inside the waterfront strip itself. A harbour build whose strip is its
    sea part (quay, piers, ships) needs water there; a plain shore strip (0) does not care.
  - limits: the terrain it accepts under the rest of the build
  - spawnClearance: how far its semantic region must stay from vanilla's spawn search origin (0 = no rule)

  The three waterfront details are read with planner version 1's values when absent, so plans saved before they
  existed read exactly as they were made.
 */
public record SiteRules(Footprint footprint, Waterfront waterfront, Optional<Direction> seaSide, int waterfrontDepth, double minStripSea,
                        TerrainLimits limits, int spawnClearance) {
    /** The waterfront depth planner version 1 used for every site. */
    public static final int LEGACY_WATERFRONT_DEPTH = 48;

    public static final Codec<SiteRules> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Footprint.CODEC.fieldOf("footprint").forGetter(SiteRules::footprint),
                    Waterfront.CODEC.fieldOf("waterfront").forGetter(SiteRules::waterfront),
                    Direction.CODEC.optionalFieldOf("sea_side").forGetter(SiteRules::seaSide),
                    Codec.INT.optionalFieldOf("waterfront_depth", LEGACY_WATERFRONT_DEPTH).forGetter(SiteRules::waterfrontDepth),
                    Codec.DOUBLE.optionalFieldOf("min_strip_sea", 0.0).forGetter(SiteRules::minStripSea),
                    TerrainLimits.CODEC.fieldOf("terrain").forGetter(SiteRules::limits),
                    Codec.INT.fieldOf("spawn_clearance").forGetter(SiteRules::spawnClearance))
            .apply(i, SiteRules::new));

    public SiteRules {
        String problem = problem(seaSide, waterfrontDepth, minStripSea);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
    }

    /** Rules with planner version 1's waterfront details: no fixed sea side, a 48-block strip, no sea needed in it. */
    public SiteRules(Footprint footprint, Waterfront waterfront, TerrainLimits limits, int spawnClearance) {
        this(footprint, waterfront, Optional.empty(), LEGACY_WATERFRONT_DEPTH, 0.0, limits, spawnClearance);
    }

    private static String problem(Optional<Direction> seaSide, int waterfrontDepth, double minStripSea) {
        if (seaSide.isPresent() && !seaSide.get().getAxis().isHorizontal()) {
            return "a build's sea side is a horizontal direction, not " + seaSide.get();
        }
        if (waterfrontDepth <= 0 || waterfrontDepth % TerrainGrid.STEP != 0) {
            return "the waterfront depth must be a positive whole number of chunks (multiple of 16), got " + waterfrontDepth;
        }
        if (minStripSea < 0.0 || minStripSea > 1.0) {
            return "the least share of sea in the waterfront strip must lie between 0 and 1, got " + minStripSea;
        }
        return null;
    }

    public SiteRules withLimits(TerrainLimits limits) {
        return new SiteRules(this.footprint, this.waterfront, this.seaSide, this.waterfrontDepth, this.minStripSea, limits, this.spawnClearance);
    }

    public SiteRules withWaterfront(Waterfront waterfront) {
        return new SiteRules(this.footprint, waterfront, this.seaSide, this.waterfrontDepth, this.minStripSea, this.limits, this.spawnClearance);
    }

    public SiteRules withSpawnClearance(int spawnClearance) {
        return new SiteRules(this.footprint, this.waterfront, this.seaSide, this.waterfrontDepth, this.minStripSea, this.limits, spawnClearance);
    }
}
