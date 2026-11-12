package io.github.nick92461.inhabitedworld.region;

import java.util.Locale;

import com.mojang.serialization.Codec;

import io.github.nick92461.inhabitedworld.worldgen.planner.SiteKind;

import net.minecraft.util.StringRepresentable;

/*
  What a semantic region is (canon 06 section 17, 09 Phase 2B: TOWN, WILDERNESS and EXTERNAL POI as real semantic
  regions). Its specificity orders overlapping regions: the more specific one applies (06 section 17.4: "specific
  POI region, then settlement region, then general wilderness").

  Wilderness is never saved as a volume: it is everything not covered by a more specific region.
 */
public enum RegionKind implements StringRepresentable {
    WILDERNESS(0),
    SETTLEMENT(1),
    POI(2);

    public static final Codec<RegionKind> CODEC = StringRepresentable.fromEnum(RegionKind::values);

    private final int specificity;

    RegionKind(int specificity) {
        this.specificity = specificity;
    }

    /** Higher wins where regions overlap. */
    public int specificity() {
        return this.specificity;
    }

    public static RegionKind of(SiteKind site) {
        return switch (site) {
            case SETTLEMENT -> SETTLEMENT;
            case POI -> POI;
        };
    }

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
