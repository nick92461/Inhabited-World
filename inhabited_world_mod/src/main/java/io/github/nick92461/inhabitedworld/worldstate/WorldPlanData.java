package io.github.nick92461.inhabitedworld.worldstate;

import io.github.nick92461.inhabitedworld.InhabitedWorld;

import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/*
  Saves the WorldPlan in the world's own data folder (data/inhabited_world/world_plan.dat), one file per domain
  (08 section 49). The plan is written when the world is created; the only later write records verification after
  pre-generation, once. A version 1 plan read from disk is upgraded in memory and written as version 2 only if
  something else marks it changed.
 */
public final class WorldPlanData extends SavedData {
    /*
      SavedDataType needs a vanilla data-fix type, applied only when a world is opened by a newer Minecraft.
      Stopwatch data has no fixers, and its fields don't overlap ours, so it is a safe no-op.
     */
    public static final SavedDataType<WorldPlanData> TYPE = new SavedDataType<>(
            InhabitedWorld.id("world_plan"),
            () -> {
                throw new IllegalStateException("a WorldPlan is only ever made when its world is created");
            },
            WorldPlan.CODEC.xmap(WorldPlanData::new, WorldPlanData::plan),
            DataFixTypes.SAVED_DATA_STOPWATCHES);

    private WorldPlan plan;

    public WorldPlanData(WorldPlan plan) {
        this.plan = plan;
    }

    public WorldPlan plan() {
        return this.plan;
    }

    /** Records verification after pre-generation. A plan is verified once; its sites never change. */
    public void recordVerification(SiteVerification verification) {
        if (this.plan.verification().isPresent()) {
            throw new IllegalStateException("this WorldPlan was already verified");
        }
        this.plan = this.plan.withVerification(verification);
        this.setDirty();
    }
}
