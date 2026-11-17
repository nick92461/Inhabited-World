package io.github.nick92461.inhabitedworld.threat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/*
  A controlled hostile hotspot inside a POI (canon 06 sections 17.3 "guaranteed hostile regions", 17.7 "controlled
  ambient spawning"; 09 DoD 17): a box where the POI's own hostiles are kept present on purpose, so a dangerous place
  is not empty because vanilla's random spawn rolls didn't land there. It spawns the profile's listed hostiles by
  their weights, at spots that meet the profile's spawn conditions, found by its own search of the box.

    {"id": "workings", "anchor": {"vertical": "surface"}, "radius": 16, "below": 24, "above": 6,
     "target": 4, "cap": 6, "interval_ticks": 40, "activation_radius": 40}

  - id: unique within its profile ([a-z0-9_]); its mobs carry the tag inhabited_world.hotspot.<region>.<id>
  - anchor, radius, below, above: the box, anchor x/z +- radius, from below to above blocks around the anchor's y
  - target: the hotspot spawns while fewer of the profile's hostiles than this stand inside its box
  - cap: the most living mobs it has spawned that may exist at once, wherever they wandered, so mobs that walk out of
    the box are not endlessly replaced
  - interval_ticks, spawns_per_run: how often it tries and how many it may add per try (throttle)
  - activation_radius: it runs only while a player is this close to its anchor
  - min_player_distance: it never spawns a mob closer than this to a player
 */
public record HotspotDefinition(String id, SiteAnchor anchor, int radius, int below, int above, int target, int cap,
                                int intervalTicks, int spawnsPerRun, int activationRadius, int minPlayerDistance) {
    public static final Codec<HotspotDefinition> CODEC = RecordCodecBuilder.<HotspotDefinition>create(i -> i.group(
                    Codec.STRING.fieldOf("id").forGetter(HotspotDefinition::id),
                    SiteAnchor.CODEC.optionalFieldOf("anchor", SiteAnchor.CENTRE_SURFACE).forGetter(HotspotDefinition::anchor),
                    Codec.intRange(1, 64).fieldOf("radius").forGetter(HotspotDefinition::radius),
                    Codec.intRange(0, 128).optionalFieldOf("below", 8).forGetter(HotspotDefinition::below),
                    Codec.intRange(0, 128).optionalFieldOf("above", 4).forGetter(HotspotDefinition::above),
                    Codec.intRange(1, 64).fieldOf("target").forGetter(HotspotDefinition::target),
                    Codec.intRange(1, 64).fieldOf("cap").forGetter(HotspotDefinition::cap),
                    Codec.intRange(1, 12_000).optionalFieldOf("interval_ticks", 40).forGetter(HotspotDefinition::intervalTicks),
                    Codec.intRange(1, 16).optionalFieldOf("spawns_per_run", 1).forGetter(HotspotDefinition::spawnsPerRun),
                    Codec.intRange(1, 128).optionalFieldOf("activation_radius", 40).forGetter(HotspotDefinition::activationRadius),
                    Codec.intRange(0, 64).optionalFieldOf("min_player_distance", 8).forGetter(HotspotDefinition::minPlayerDistance))
            .apply(i, HotspotDefinition::new))
            .validate(HotspotDefinition::check);

    private static DataResult<HotspotDefinition> check(HotspotDefinition h) {
        if (!h.id().matches("[a-z0-9_]+")) {
            return DataResult.error(() -> "hotspot id '" + h.id() + "' must be lower-case letters, digits and _");
        }
        if (h.cap() < h.target()) {
            return DataResult.error(() -> "hotspot " + h.id() + ": cap " + h.cap() + " below target " + h.target());
        }
        if (h.minPlayerDistance() > h.activationRadius() + h.radius()) {
            return DataResult.error(() -> "hotspot " + h.id() + ": min_player_distance " + h.minPlayerDistance() + " leaves no spot for an active hotspot");
        }
        return DataResult.success(h);
    }

    public String describe() {
        return this.id + ": box +-" + this.radius + " around the anchor, " + this.below + " below to " + this.above + " above; target " + this.target
                + ", cap " + this.cap + "; every " + this.intervalTicks + " ticks up to " + this.spawnsPerRun + " while a player is within "
                + this.activationRadius + " (never closer than " + this.minPlayerDistance + ")";
    }
}
