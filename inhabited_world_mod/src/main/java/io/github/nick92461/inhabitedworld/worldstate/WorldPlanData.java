package io.github.nick92461.inhabitedworld.worldstate;

import io.github.nick92461.inhabitedworld.InhabitedWorld;

import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/*
  Saves the WorldPlan in the world's own data folder (data/inhabited_world/world_plan.dat), one file per domain
  (08 section 49). The plan is immutable, so this is written once, when the world is created.
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

    private final WorldPlan plan;

    public WorldPlanData(WorldPlan plan) {
        this.plan = plan;
    }

    public WorldPlan plan() {
        return this.plan;
    }
}
