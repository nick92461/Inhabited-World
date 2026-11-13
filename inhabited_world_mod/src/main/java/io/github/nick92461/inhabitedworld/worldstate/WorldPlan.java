package io.github.nick92461.inhabitedworld.worldstate;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.nick92461.inhabitedworld.region.RegionIndex;
import io.github.nick92461.inhabitedworld.region.SemanticRegion;
import io.github.nick92461.inhabitedworld.region.SiteRegions;
import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannerRun;

import net.minecraft.core.UUIDUtil;

import org.jspecify.annotations.Nullable;

/*
  The WorldPlan (canon 02 section 7): made once when an Inhabited World is created, saved with it, and never
  re-planned on load.

  - worldId: the permanent unique world ID that every WorldPlan carries (02 section 7, 08 section 49)
  - playableArea: the finite map's bounds, which set the world border and the pre-generated area
  - planner: which planner algorithm and version chose the sites, and the fallback step it needed (02 section 7)
  - sites: every required site, resolved: kind, catalog ID, rules, build bounds and semantic boundary (02 section 6)
  - regions: the semantic regions in 3D, with their spawn profiles (06 section 17; 08 section 49: the WorldPlan
    preserves "semantic regions, settlement boundaries, information needed for spawn/threat policy")
  - verification: what the check after pre-generation found, recorded once

  Regions are saved, not derived on every load: 06 section 17.12 asks for a "persisted semantic protected region",
  and 02 section 7 forbids existing worlds from picking up a newer algorithm. If the rules that turn a site into a
  region (SiteRegions) ever change, saved worlds keep the regions they were made with.

  Data versions (08 section 49):
  - 1 (P2A step 4): world ID and playable area only. Such a world was generated before the planner existed, so it
    is read with no planner, sites or regions; its terrain is never re-planned.
  - 2 (P2A step 5): adds planner, sites and verification.
  - 3 (P2B): adds regions. A version 2 plan is migrated once, explicitly (02 section 7 allows "an explicit migration"):
    its sites' regions are derived with SiteRegions, exactly as a new plan's are, and WorldPlanData writes the result
    back at the next save, so the migration never runs twice.
  - 4 (planner version 2, the Drachenfels Harbour): sites may record a rotation, and their rules a sea side, a
    waterfront depth and the sea needed in the waterfront strip; their metrics the sea measured in that strip. A
    version 2 or 3 plan has none of these, and reads with exactly the values its planner used (no rotation, a 48-block
    strip, no sea needed in it): its sites, regions and verification stay as they were, and only the version number
    it is saved with changes. A version 4 plan is refused by an older mod, which would misread a rotated site.

  Later phases add structure placement and geography facts, each with a new data version.
 */
public record WorldPlan(int dataVersion, UUID worldId, PlayableArea playableArea, Optional<PlannerRun> planner, List<PlannedSite> sites,
                        List<SemanticRegion> regions, Optional<SiteVerification> verification) {
    public static final int DATA_VERSION = 4;

    /** The step-4 plan: no planner, no sites. */
    public static final int DATA_VERSION_WITHOUT_SITES = 1;
    /** The step-5 plan: sites, but no 3D regions. */
    public static final int DATA_VERSION_WITHOUT_REGIONS = 2;
    /** The P2B plan: sites and regions, planned by planner version 1 (no rotation, no per-site waterfront rules). */
    public static final int DATA_VERSION_UNROTATED = 3;

    /** The plan as it is stored, any supported version, before migration. */
    public static final Codec<WorldPlan> STORED_CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("data_version").forGetter(WorldPlan::dataVersion),
                    UUIDUtil.STRING_CODEC.fieldOf("world_id").forGetter(WorldPlan::worldId),
                    PlayableArea.CODEC.fieldOf("playable_area").forGetter(WorldPlan::playableArea),
                    PlannerRun.CODEC.optionalFieldOf("planner").forGetter(WorldPlan::planner),
                    PlannedSite.CODEC.listOf().optionalFieldOf("sites", List.of()).forGetter(WorldPlan::sites),
                    SemanticRegion.CODEC.listOf().optionalFieldOf("regions", List.of()).forGetter(WorldPlan::regions),
                    SiteVerification.CODEC.optionalFieldOf("verification").forGetter(WorldPlan::verification))
            .apply(i, WorldPlan::new));

    /** Reads any supported version as the current one, and writes only the current one. */
    public static final Codec<WorldPlan> CODEC = STORED_CODEC.flatXmap(WorldPlan::upgrade, WorldPlan::checkWritable);

    public WorldPlan {
        sites = List.copyOf(sites);
        regions = List.copyOf(regions);
    }

    /*
      Reading: older versions become the current one; unknown versions are refused, so a save written by a newer mod
      version is never read as if it were this version's.
     */
    public static DataResult<WorldPlan> upgrade(WorldPlan plan) {
        if (plan.dataVersion() == DATA_VERSION_WITHOUT_SITES) {
            if (plan.planner().isPresent() || !plan.sites().isEmpty() || !plan.regions().isEmpty() || plan.verification().isPresent()) {
                return DataResult.error(() -> "a version 1 WorldPlan has no planner, sites, regions or verification");
            }
            return DataResult.success(new WorldPlan(DATA_VERSION, plan.worldId(), plan.playableArea(), Optional.empty(), List.of(), List.of(), Optional.empty()));
        }
        if (plan.dataVersion() == DATA_VERSION_WITHOUT_REGIONS) {
            if (!plan.regions().isEmpty()) {
                return DataResult.error(() -> "a version 2 WorldPlan has no regions");
            }
            WorldPlan migrated = new WorldPlan(DATA_VERSION, plan.worldId(), plan.playableArea(), plan.planner(), plan.sites(),
                    SiteRegions.derive(plan.sites()), plan.verification());
            return check(migrated);
        }
        if (plan.dataVersion() == DATA_VERSION_UNROTATED) {
            // Absent fields have already read as planner version 1's values; only the version number moves on.
            return check(new WorldPlan(DATA_VERSION, plan.worldId(), plan.playableArea(), plan.planner(), plan.sites(), plan.regions(), plan.verification()));
        }
        if (plan.dataVersion() != DATA_VERSION) {
            return DataResult.error(() -> "unsupported WorldPlan data version " + plan.dataVersion() + " (this mod reads up to " + DATA_VERSION + ")");
        }
        return check(plan);
    }

    private static DataResult<WorldPlan> check(WorldPlan plan) {
        if (plan.sites().isEmpty() != plan.planner().isEmpty()) {
            return DataResult.error(() -> "a WorldPlan has a planner record exactly when it has planned sites");
        }
        Set<String> siteIds = new HashSet<>();
        for (PlannedSite site : plan.sites()) {
            if (!siteIds.add(site.id())) {
                return DataResult.error(() -> "two sites are called " + site.id());
            }
        }
        Set<String> regionIds = new HashSet<>();
        Set<String> covered = new HashSet<>();
        for (SemanticRegion region : plan.regions()) {
            if (!regionIds.add(region.id())) {
                return DataResult.error(() -> "two regions are called " + region.id());
            }
            PlannedSite site = plan.site(region.site().orElse(""));
            if (site == null) {
                return DataResult.error(() -> "region " + region.id() + " names a site the plan doesn't have: " + region.site().orElse("none"));
            }
            if (!site.region().contains(region.volume().footprint())) {
                return DataResult.error(() -> "region " + region.id() + " reaches outside the semantic boundary of site " + site.id());
            }
            covered.add(site.id());
        }
        if (!covered.equals(siteIds)) {
            return DataResult.error(() -> "every planned site has a semantic region (sites " + siteIds + ", regions cover " + covered + ")");
        }
        return DataResult.success(plan);
    }

    static DataResult<WorldPlan> checkWritable(WorldPlan plan) {
        return plan.dataVersion() == DATA_VERSION
                ? DataResult.success(plan)
                : DataResult.error(() -> "only data version " + DATA_VERSION + " is written, not " + plan.dataVersion());
    }

    /** A new world's plan: its sites, and the semantic region of each (SiteRegions). */
    public static WorldPlan create(UUID worldId, PlayableArea playableArea, PlannerRun planner, List<PlannedSite> sites) {
        if (sites.isEmpty()) {
            throw new IllegalArgumentException("a planned world has at least one site");
        }
        WorldPlan plan = new WorldPlan(DATA_VERSION, worldId, playableArea, Optional.of(planner), sites, SiteRegions.derive(sites), Optional.empty());
        return check(plan).getOrThrow(IllegalArgumentException::new);
    }

    public WorldPlan withVerification(SiteVerification verification) {
        return new WorldPlan(this.dataVersion, this.worldId, this.playableArea, this.planner, this.sites, this.regions, Optional.of(verification));
    }

    /** Whether this world's sites were planned (false for a world made before the planner existed). */
    public boolean hasSites() {
        return !this.sites.isEmpty();
    }

    public @Nullable PlannedSite site(String id) {
        for (PlannedSite site : this.sites) {
            if (site.id().equals(id)) {
                return site;
            }
        }
        return null;
    }

    public @Nullable SemanticRegion region(String id) {
        for (SemanticRegion region : this.regions) {
            if (region.id().equals(id)) {
                return region;
            }
        }
        return null;
    }

    /** The in-memory lookup for these regions (06 section 17.6). Built when the world opens, not per lookup. */
    public RegionIndex buildRegionIndex() {
        return RegionIndex.of(SiteRegions.wilderness(this.playableArea), this.regions);
    }
}
