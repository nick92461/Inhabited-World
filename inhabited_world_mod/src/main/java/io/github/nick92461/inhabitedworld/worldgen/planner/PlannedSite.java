package io.github.nick92461.inhabitedworld.worldgen.planner;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/*
  One site as the planner resolved it, saved in the WorldPlan (canon 02 sections 6 and 7, 08 section 49):

  - id, kind, catalogId: what the site is (SiteRequirement)
  - rules: the rules it was placed under, after any fallback step
  - bounds: where the build goes (Phase 3 places it here)
  - region: the site's authoritative semantic boundary, the build plus its blend border: the settlement boundary of
    a town, the semantic region of a POI (02 section 6). It is 2D for now; Phase 2B gives it a vertical extent.
  - waterfront: which side faces the sea, for a waterfront site
  - predicted: the terrain the planner measured from the generator's noise
 */
public record PlannedSite(String id, SiteKind kind, Identifier catalogId, SiteRules rules, BlockRect bounds, BlockRect region,
                          Optional<Direction> waterfront, SiteMetrics predicted) {
    public static final Codec<PlannedSite> CODEC = RecordCodecBuilder.<PlannedSite>create(i -> i.group(
                    Codec.STRING.fieldOf("id").forGetter(PlannedSite::id),
                    SiteKind.CODEC.fieldOf("kind").forGetter(PlannedSite::kind),
                    Identifier.CODEC.fieldOf("catalog_id").forGetter(PlannedSite::catalogId),
                    SiteRules.CODEC.fieldOf("rules").forGetter(PlannedSite::rules),
                    BlockRect.CODEC.fieldOf("bounds").forGetter(PlannedSite::bounds),
                    BlockRect.CODEC.fieldOf("region").forGetter(PlannedSite::region),
                    Direction.CODEC.optionalFieldOf("waterfront").forGetter(PlannedSite::waterfront),
                    SiteMetrics.CODEC.fieldOf("predicted_terrain").forGetter(PlannedSite::predicted))
            .apply(i, PlannedSite::new))
            .validate(PlannedSite::check);

    private static DataResult<PlannedSite> check(PlannedSite site) {
        if (!site.region().contains(site.bounds())) {
            return DataResult.error(() -> "site " + site.id() + ": its region " + site.region() + " must contain its bounds " + site.bounds());
        }
        if (site.waterfront().isPresent() && !site.waterfront().get().getAxis().isHorizontal()) {
            return DataResult.error(() -> "site " + site.id() + ": a waterfront faces a horizontal direction");
        }
        return DataResult.success(site);
    }

    public int centerX() {
        return (int) Math.floor(this.bounds.centerX());
    }

    public int centerZ() {
        return (int) Math.floor(this.bounds.centerZ());
    }
}
