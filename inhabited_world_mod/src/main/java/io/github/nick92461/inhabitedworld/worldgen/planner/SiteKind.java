package io.github.nick92461.inhabitedworld.worldgen.planner;

import java.util.Locale;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/*
  What a planned site is (canon 02 section 6): a settlement gets an authoritative semantic settlement boundary, an
  external POI an authoritative semantic region. Version 0.1 plans one of each; the planner works on a list of
  sites, so a larger world plans several settlements the same way.
 */
public enum SiteKind implements StringRepresentable {
    SETTLEMENT,
    POI;

    public static final Codec<SiteKind> CODEC = StringRepresentable.fromEnum(SiteKind::values);

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
