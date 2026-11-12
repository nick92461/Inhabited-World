package io.github.nick92461.inhabitedworld.worldstate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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
  - sites: every required site, resolved: kind, catalog ID, rules, build bounds and semantic region (02 section 6)
  - verification: what the check after pre-generation found, recorded once

  Data versions (08 section 49):
  - 1 (P2A step 4): world ID and playable area only. Such a world was generated before the planner existed, so it
    is read as version 2 with no planner and no sites; its terrain is never re-planned.
  - 2 (P2A step 5): adds planner, sites and verification.

  Later phases add semantic regions in 3D, structure placement and geography facts, each with a new data version.
 */
public record WorldPlan(int dataVersion, UUID worldId, PlayableArea playableArea, Optional<PlannerRun> planner, List<PlannedSite> sites,
                        Optional<SiteVerification> verification) {
    public static final int DATA_VERSION = 2;

    /** The step-4 plan: no planner, no sites. */
    public static final int DATA_VERSION_WITHOUT_SITES = 1;

    public static final Codec<WorldPlan> CODEC = RecordCodecBuilder.<WorldPlan>create(i -> i.group(
                    Codec.INT.fieldOf("data_version").forGetter(WorldPlan::dataVersion),
                    UUIDUtil.STRING_CODEC.fieldOf("world_id").forGetter(WorldPlan::worldId),
                    PlayableArea.CODEC.fieldOf("playable_area").forGetter(WorldPlan::playableArea),
                    PlannerRun.CODEC.optionalFieldOf("planner").forGetter(WorldPlan::planner),
                    PlannedSite.CODEC.listOf().optionalFieldOf("sites", List.of()).forGetter(WorldPlan::sites),
                    SiteVerification.CODEC.optionalFieldOf("verification").forGetter(WorldPlan::verification))
            .apply(i, WorldPlan::new))
            .flatXmap(WorldPlan::upgrade, WorldPlan::checkWritable);

    public WorldPlan {
        sites = List.copyOf(sites);
    }

    // Reading: version 1 becomes version 2 with no planner and no sites; unknown versions are refused, so a save
    // written by a newer mod version is never read as if it were this version's.
    private static DataResult<WorldPlan> upgrade(WorldPlan plan) {
        if (plan.dataVersion() == DATA_VERSION_WITHOUT_SITES) {
            if (plan.planner().isPresent() || !plan.sites().isEmpty() || plan.verification().isPresent()) {
                return DataResult.error(() -> "a version 1 WorldPlan has no planner, sites or verification");
            }
            return DataResult.success(new WorldPlan(DATA_VERSION, plan.worldId(), plan.playableArea(), Optional.empty(), List.of(), Optional.empty()));
        }
        if (plan.dataVersion() != DATA_VERSION) {
            return DataResult.error(() -> "unsupported WorldPlan data version " + plan.dataVersion() + " (this mod reads up to " + DATA_VERSION + ")");
        }
        if (plan.sites().isEmpty() != plan.planner().isEmpty()) {
            return DataResult.error(() -> "a WorldPlan has a planner record exactly when it has planned sites");
        }
        return DataResult.success(plan);
    }

    private static DataResult<WorldPlan> checkWritable(WorldPlan plan) {
        return plan.dataVersion() == DATA_VERSION
                ? DataResult.success(plan)
                : DataResult.error(() -> "only data version " + DATA_VERSION + " is written, not " + plan.dataVersion());
    }

    public static WorldPlan create(UUID worldId, PlayableArea playableArea, PlannerRun planner, List<PlannedSite> sites) {
        if (sites.isEmpty()) {
            throw new IllegalArgumentException("a planned world has at least one site");
        }
        return new WorldPlan(DATA_VERSION, worldId, playableArea, Optional.of(planner), sites, Optional.empty());
    }

    public WorldPlan withVerification(SiteVerification verification) {
        return new WorldPlan(this.dataVersion, this.worldId, this.playableArea, this.planner, this.sites, Optional.of(verification));
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
}
