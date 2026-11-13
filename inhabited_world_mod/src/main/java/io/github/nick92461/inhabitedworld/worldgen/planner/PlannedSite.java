package io.github.nick92461.inhabitedworld.worldgen.planner;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

/*
  One site as the planner resolved it, saved in the WorldPlan (canon 02 sections 6 and 7, 08 section 49):

  - id, kind, catalogId: what the site is (SiteRequirement)
  - rules: the rules it was placed under, after any fallback step
  - bounds: where the build goes, in world blocks, already turned by the rotation (Phase 3 places it here)
  - region: the site's authoritative semantic boundary, the build plus its blend border: the settlement boundary of
    a town, the semantic region of a POI (02 section 6). Its 3D semantic regions (SemanticRegion, from SiteRegions)
    stand on this footprint.
  - waterfront: which side faces the sea, for a waterfront site
  - predicted: the terrain the planner measured from the generator's noise
  - rotation: how the build is turned from its own (template) orientation, as a structure template rotation. A
    rotation of 90 degrees either way swaps the footprint's x and z sizes in the bounds. For a build with a sea side,
    the rotation turns that side to the waterfront. Phase 3 rotates the template by it and moves it so that it fills
    the bounds exactly. Plans made by planner version 1 never rotated a build, and read as NONE.
 */
public record PlannedSite(String id, SiteKind kind, Identifier catalogId, SiteRules rules, BlockRect bounds, BlockRect region,
                          Optional<Direction> waterfront, SiteMetrics predicted, Rotation rotation) {
    public static final Codec<PlannedSite> CODEC = RecordCodecBuilder.<PlannedSite>create(i -> i.group(
                    Codec.STRING.fieldOf("id").forGetter(PlannedSite::id),
                    SiteKind.CODEC.fieldOf("kind").forGetter(PlannedSite::kind),
                    Identifier.CODEC.fieldOf("catalog_id").forGetter(PlannedSite::catalogId),
                    SiteRules.CODEC.fieldOf("rules").forGetter(PlannedSite::rules),
                    BlockRect.CODEC.fieldOf("bounds").forGetter(PlannedSite::bounds),
                    BlockRect.CODEC.fieldOf("region").forGetter(PlannedSite::region),
                    Direction.CODEC.optionalFieldOf("waterfront").forGetter(PlannedSite::waterfront),
                    SiteMetrics.CODEC.fieldOf("predicted_terrain").forGetter(PlannedSite::predicted),
                    Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(PlannedSite::rotation))
            .apply(i, PlannedSite::new))
            .validate(PlannedSite::check);

    /** A site whose build is not rotated. */
    public PlannedSite(String id, SiteKind kind, Identifier catalogId, SiteRules rules, BlockRect bounds, BlockRect region,
                       Optional<Direction> waterfront, SiteMetrics predicted) {
        this(id, kind, catalogId, rules, bounds, region, waterfront, predicted, Rotation.NONE);
    }

    private static DataResult<PlannedSite> check(PlannedSite site) {
        if (!site.region().contains(site.bounds())) {
            return DataResult.error(() -> "site " + site.id() + ": its region " + site.region() + " must contain its bounds " + site.bounds());
        }
        if (site.waterfront().isPresent() && !site.waterfront().get().getAxis().isHorizontal()) {
            return DataResult.error(() -> "site " + site.id() + ": a waterfront faces a horizontal direction");
        }
        Footprint footprint = site.rules().footprint();
        if (site.bounds().sizeX() != rotatedSizeX(footprint, site.rotation()) || site.bounds().sizeZ() != rotatedSizeZ(footprint, site.rotation())) {
            return DataResult.error(() -> "site " + site.id() + ": its bounds " + site.bounds() + " are not its " + footprint.sizeX() + " x "
                    + footprint.sizeZ() + " footprint turned " + site.rotation().getSerializedName());
        }
        if (site.waterfront().isPresent() && site.rules().seaSide().isPresent()
                && site.rotation().rotate(site.rules().seaSide().get()) != site.waterfront().get()) {
            return DataResult.error(() -> "site " + site.id() + ": rotation " + site.rotation().getSerializedName() + " turns the build's "
                    + site.rules().seaSide().get().getName() + " sea side away from its " + site.waterfront().get().getName() + " waterfront");
        }
        return DataResult.success(site);
    }

    /** Whether a rotation turns a build a quarter turn, swapping its x and z sizes. */
    public static boolean quarterTurn(Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
    }

    /** The footprint's x size in the world once the build is turned by the rotation. */
    public static int rotatedSizeX(Footprint footprint, Rotation rotation) {
        return quarterTurn(rotation) ? footprint.sizeZ() : footprint.sizeX();
    }

    public static int rotatedSizeZ(Footprint footprint, Rotation rotation) {
        return quarterTurn(rotation) ? footprint.sizeX() : footprint.sizeZ();
    }

    public int centerX() {
        return (int) Math.floor(this.bounds.centerX());
    }

    public int centerZ() {
        return (int) Math.floor(this.bounds.centerZ());
    }
}
