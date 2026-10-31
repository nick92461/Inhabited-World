package io.github.nick92461.inhabitedworld.worldstate;

import java.util.UUID;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;
import io.github.nick92461.inhabitedworld.worldgen.RpgWorlds;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;

import org.jspecify.annotations.Nullable;

/*
  Making and reading the WorldPlan (canon 02 section 7). The plan is made exactly once: while an Inhabited World is
  being created, after its overworld exists and before its first chunk generates (vanilla's spawn search is the
  first thing to generate chunks). Opening an existing world only reads the saved plan. A world created before
  WorldPlans existed has none, and is left as it was: no plan, no border, no pre-generation.
 */
public final class WorldPlans {
    private WorldPlans() {
    }

    /** Whether world creation should make a plan: only for an Inhabited World that has none yet. */
    public static boolean shouldCreate(boolean rpgWorld, boolean hasPlan) {
        return rpgWorld && !hasPlan;
    }

    public static @Nullable WorldPlan get(MinecraftServer server) {
        WorldPlanData data = server.getDataStorage().get(WorldPlanData.TYPE);
        return data == null ? null : data.plan();
    }

    /** Called once, during world creation (MinecraftServerMixin). Does nothing for a Default world. */
    public static void createAtWorldCreation(MinecraftServer server, ServerLevel overworld) {
        boolean rpg = RpgWorlds.isRpg(overworld.getChunkSource().getGenerator());
        if (!shouldCreate(rpg, get(server) != null)) {
            return;
        }

        WorldPlan plan = WorldPlan.create(UUID.randomUUID(), PlayableArea.V0_1);
        server.getDataStorage().set(WorldPlanData.TYPE, new WorldPlanData(plan));
        applyBorder(overworld.getWorldBorder(), plan.playableArea());
        InhabitedWorld.LOGGER.info("[{}] created the WorldPlan: world {}, playable area {} blocks centred on {}, {}",
                InhabitedWorld.MOD_ID, plan.worldId(), plan.playableArea().size(), plan.playableArea().centerX(), plan.playableArea().centerZ());
    }

    /*
      The world border is the hard technical boundary (02 section 6), set once here. Vanilla saves it with the level;
      /worldborder (an operator command) can still change it afterwards, and the plan stays as it was.
     */
    static void applyBorder(WorldBorder border, PlayableArea area) {
        border.setCenter(area.centerX(), area.centerZ());
        border.setSize(area.size());
    }
}
