package io.github.nick92461.inhabitedworld.region;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.worldgen.RpgWorlds;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlan;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlans;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.jspecify.annotations.Nullable;

/*
  Connects the WorldPlan's saved regions to the running world (canon 06 section 17.6: "the authoritative region
  information originates from persisted world data such as the WorldPlan, but runtime checks should not repeatedly
  perform expensive disk or generation operations").

  The overworld's index is built from the plan once, before the first chunk generates or the first mob spawns:
  when a new world's plan is made (WorldPlans.createAtWorldCreation), and when an existing world is opened (the start
  of level preparation, MinecraftServerMixin). A level without one (a Default world, or an Inhabited World made
  before the planner existed) is all wilderness.
 */
public final class Regions {
    private Regions() {
    }

    /** Builds the overworld's index from the saved plan, or clears it when there is no planned world. */
    public static void install(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        WorldPlan plan = RpgWorlds.isRpg(overworld) ? WorldPlans.get(server) : null;
        RegionIndex index = plan == null || plan.regions().isEmpty() ? null : plan.buildRegionIndex();
        ((RegionIndexHolder) overworld).inhabitedWorld$setRegionIndex(index);
        if (index != null) {
            InhabitedWorld.LOGGER.info("[{}] semantic regions ready: {} regions over {} chunk columns, the rest is wilderness", InhabitedWorld.MOD_ID,
                    index.regions().size(), index.gridCells());
            for (SemanticRegion region : index.regions()) {
                InhabitedWorld.LOGGER.info("[{}]   {}", InhabitedWorld.MOD_ID, region.describe());
            }
        }
    }

    public static @Nullable RegionIndex index(ServerLevel level) {
        return ((RegionIndexHolder) level).inhabitedWorld$regionIndex();
    }

    /**
     * The region at a block of a level, allocation-free. Null when the level has no regions at all (every position
     * is then ordinary wilderness, see SpawnGovernance).
     */
    public static @Nullable SemanticRegion at(ServerLevel level, int x, int y, int z) {
        RegionIndex index = index(level);
        return index == null ? null : index.regionAt(x, y, z);
    }
}
