package io.github.nick92461.inhabitedworld.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import io.github.nick92461.inhabitedworld.InhabitedWorld;

import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

/*
  A semantic region's spawn (threat) profile: what ambient hostile spawning does inside it (canon 06 sections 17.1
  to 17.3). Every semantic region names its profile by ID, saved with the region in the WorldPlan (08 section 49:
  the plan preserves "information needed for spawn/threat policy"), so a world keeps the rules it was made with.

  Version 0.1 has three:
  - wilderness: vanilla-style hostile ecology, night and cave danger kept (06 section 17.2)
  - protected_settlement: ordinary hostile generation never happens inside; hostiles may still walk in (06 section 17.1)
  - poi_placeholder: the external POI's profile until Phase 3. It behaves like the wilderness for now. Phase 3 replaces
    it with the reusable minimal POI threat profile (06 section 17.3: allowed, blocked and weighted hostile types,
    local caps, encounter zones, safe areas) and the guaranteed encounters beside it (06 sections 17.7, 17.8).
    SpawnPolicy.decide is the one place a profile is consulted, so that is where Phase 3 plugs in.

  Saved as its identifier (inhabited_world:protected_settlement), so Phase 3's data-driven profiles can use the same
  field. An unknown ID is a decoding error: it can only come from a newer mod version, whose plans are refused anyway.
 */
public enum SpawnProfile {
    WILDERNESS("wilderness", false),
    PROTECTED_SETTLEMENT("protected_settlement", true),
    POI_PLACEHOLDER("poi_placeholder", false);

    public static final Codec<SpawnProfile> CODEC = Identifier.CODEC.comapFlatMap(
            id -> {
                SpawnProfile profile = byId(id);
                return profile != null ? DataResult.success(profile) : DataResult.error(() -> "unknown spawn profile " + id);
            },
            SpawnProfile::id);

    private final Identifier id;
    private final boolean suppressesAmbientHostiles;

    SpawnProfile(String path, boolean suppressesAmbientHostiles) {
        this.id = InhabitedWorld.id(path);
        this.suppressesAmbientHostiles = suppressesAmbientHostiles;
    }

    public Identifier id() {
        return this.id;
    }

    /** Whether ordinary hostile generation (SpawnPolicy.Origin.AMBIENT, monster category) is refused inside. */
    public boolean suppressesAmbientHostiles() {
        return this.suppressesAmbientHostiles;
    }

    public static @Nullable SpawnProfile byId(Identifier id) {
        for (SpawnProfile profile : values()) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        return null;
    }
}
