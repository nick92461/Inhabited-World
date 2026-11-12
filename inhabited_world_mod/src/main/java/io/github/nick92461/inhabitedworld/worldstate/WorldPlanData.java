package io.github.nick92461.inhabitedworld.worldstate;

import io.github.nick92461.inhabitedworld.InhabitedWorld;

import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/*
  Saves the WorldPlan in the world's own data folder (data/inhabited_world/world_plan.dat), one file per domain
  (08 section 49). The plan is written when the world is created; the only later writes are verification after
  pre-generation (once) and a migration from an older data version (once, at the next save after it is read).
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
            WorldPlan.STORED_CODEC.flatXmap(
                    stored -> WorldPlan.upgrade(stored).map(plan -> new WorldPlanData(plan, stored.dataVersion() != plan.dataVersion())),
                    data -> WorldPlan.checkWritable(data.plan())),
            DataFixTypes.SAVED_DATA_STOPWATCHES);

    private WorldPlan plan;

    public WorldPlanData(WorldPlan plan) {
        this(plan, false);
    }

    /**
     * @param migrated whether the plan was read from an older data version; it is then written back as the current
     *                 version at the next save, so a migration's result is fixed once made (canon 02 section 7)
     */
    private WorldPlanData(WorldPlan plan, boolean migrated) {
        this.plan = plan;
        if (migrated) {
            InhabitedWorld.LOGGER.info("[{}] migrated the WorldPlan of world {} to data version {}; it is saved as such at the next save",
                    InhabitedWorld.MOD_ID, plan.worldId(), plan.dataVersion());
            this.setDirty();
        }
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
