package io.github.nick92461.inhabitedworld.worldgen.planner;

import java.util.Locale;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/*
  Whether a site needs one side on the sea (implementation plan, "Imported builds": Drachenfels grows into a coastal
  city whose waterfront faces the sea, so the planner must be able to ask for a coast-side site).

  - NONE: the site may be anywhere on land.
  - PREFERRED: a waterfront site is required while the planner can find one; if no plan exists with it, the planner's
    fallback drops it before giving up on anything else that matters more (see SitePlanner's relaxation steps).
  - REQUIRED: dropped only by the last-resort step that guarantees the site exists at all (canon 02 section 6).

  A waterfront site records which side faces the sea, so Phase 3 can rotate the build's waterfront to match.
 */
public enum Waterfront implements StringRepresentable {
    NONE,
    PREFERRED,
    REQUIRED;

    public static final Codec<Waterfront> CODEC = StringRepresentable.fromEnum(Waterfront::values);

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
