package io.github.nick92461.inhabitedworld.region;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.nick92461.inhabitedworld.spawn.SpawnProfile;

/*
  One semantic region (canon 06 section 17, 02 sections 6 and 10), saved in the WorldPlan (08 section 49):

  - id: its semantic identity, which spawn rules derive from (06 section 17.12 item 8), never coordinates
  - kind: settlement or POI (wilderness is never saved, see RegionKind)
  - site: the planned site that supplied it (06 section 17.12: "which POI/settlement supplied the rule", 10 section 60)
  - profile: its spawn profile
  - volume: the 3D space it covers

  Version 0.1 derives one region per planned site (SiteRegions). The model allows several per site, so Phase 3 can add
  nested ones (a protected building inside the town, a mine's tunnels under the wilderness).
 */
public record SemanticRegion(String id, RegionKind kind, Optional<String> site, SpawnProfile profile, RegionVolume volume) {
    public static final String WILDERNESS_ID = "wilderness";

    public static final Codec<SemanticRegion> CODEC = RecordCodecBuilder.<SemanticRegion>create(i -> i.group(
                    Codec.STRING.fieldOf("id").forGetter(SemanticRegion::id),
                    RegionKind.CODEC.fieldOf("kind").forGetter(SemanticRegion::kind),
                    Codec.STRING.optionalFieldOf("site").forGetter(SemanticRegion::site),
                    SpawnProfile.CODEC.fieldOf("spawn_profile").forGetter(SemanticRegion::profile),
                    RegionVolume.CODEC.fieldOf("volume").forGetter(SemanticRegion::volume))
            .apply(i, SemanticRegion::new))
            .validate(SemanticRegion::check);

    private static DataResult<SemanticRegion> check(SemanticRegion region) {
        if (region.kind() == RegionKind.WILDERNESS) {
            return DataResult.error(() -> "region " + region.id() + ": the wilderness is never saved as a region");
        }
        if (region.site().isEmpty()) {
            return DataResult.error(() -> "region " + region.id() + ": a saved region names the site that supplied it");
        }
        return DataResult.success(region);
    }

    /** The wilderness of a world: everything in it that no more specific region covers (06 section 17.2). */
    public static SemanticRegion wilderness(RegionVolume world) {
        return new SemanticRegion(WILDERNESS_ID, RegionKind.WILDERNESS, Optional.empty(), SpawnProfile.WILDERNESS, world);
    }

    public boolean isWilderness() {
        return this.kind == RegionKind.WILDERNESS;
    }

    public String describe() {
        return this.id + " (" + this.kind.getSerializedName() + this.site.map(s -> ", from site " + s).orElse("") + ", spawn profile "
                + this.profile.id() + ", volume " + this.volume + ")";
    }
}
