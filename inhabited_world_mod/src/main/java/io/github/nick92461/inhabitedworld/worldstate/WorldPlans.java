package io.github.nick92461.inhabitedworld.worldstate;

import java.util.UUID;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.region.Regions;
import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;
import io.github.nick92461.inhabitedworld.worldgen.RpgWorlds;
import io.github.nick92461.inhabitedworld.worldgen.planner.NoisePlanning;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;
import io.github.nick92461.inhabitedworld.worldgen.planner.V01Sites;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import org.jspecify.annotations.Nullable;

/*
  Making and reading the WorldPlan (canon 02 section 7). The plan is made exactly once: while an Inhabited World is
  being created, after its overworld exists and before its first chunk generates (vanilla's spawn search is the
  first thing to generate chunks). The planner chooses the required sites then, from the generator's own noise
  (02 sections 6 and 8). Opening an existing world only reads the saved plan. A world created before WorldPlans
  existed has none, and is left as it was: no plan, no border, no pre-generation; one created before the planner
  existed keeps its plan without sites.
 */
public final class WorldPlans {
    private WorldPlans() {
    }

    /** Whether world creation should make a plan: only for an Inhabited World that has none yet. */
    public static boolean shouldCreate(boolean rpgWorld, boolean hasPlan) {
        return rpgWorld && !hasPlan;
    }

    public static @Nullable WorldPlan get(MinecraftServer server) {
        WorldPlanData data = data(server);
        return data == null ? null : data.plan();
    }

    static @Nullable WorldPlanData data(MinecraftServer server) {
        return server.getDataStorage().get(WorldPlanData.TYPE);
    }

    /** Called once, during world creation (MinecraftServerMixin). Does nothing for a Default world. */
    public static void createAtWorldCreation(MinecraftServer server, ServerLevel overworld) {
        boolean rpg = RpgWorlds.isRpg(overworld.getChunkSource().getGenerator());
        if (!shouldCreate(rpg, get(server) != null)) {
            return;
        }

        PlayableArea area = PlayableArea.V0_1;
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) overworld.getChunkSource().getGenerator();
        NoisePlanning.Timed planning = NoisePlanning.plan(generator, overworld.getChunkSource().randomState(), overworld.getSeed(), overworld, V01Sites.request(area));

        WorldPlan plan = WorldPlan.create(UUID.randomUUID(), area, planning.outcome().run(), planning.outcome().sites());
        server.getDataStorage().set(WorldPlanData.TYPE, new WorldPlanData(plan));
        applyBorder(overworld.getWorldBorder(), plan.playableArea());
        InhabitedWorld.LOGGER.info("[{}] created the WorldPlan: world {}, playable area {} blocks centred on {}, {}",
                InhabitedWorld.MOD_ID, plan.worldId(), plan.playableArea().size(), plan.playableArea().centerX(), plan.playableArea().centerZ());
        InhabitedWorld.LOGGER.info("[{}] planned {} sites with {} (terrain sampling {} ms over {} columns, planning {} ms)", InhabitedWorld.MOD_ID,
                plan.sites().size(), planning.outcome().run().describe(), planning.samplingMillis(), planning.grid().columnCount(), planning.planningMillis());
        for (PlannedSite site : plan.sites()) {
            InhabitedWorld.LOGGER.info("[{}]   {} ({} {}): centre {}, {}; bounds {}; region {}{}; predicted {}", InhabitedWorld.MOD_ID,
                    site.id(), site.kind().getSerializedName(), site.catalogId(), site.centerX(), site.centerZ(), site.bounds(), site.region(),
                    site.waterfront().map(d -> "; waterfront faces " + d.getName()).orElse("") + "; rotation " + site.rotation().getSerializedName(),
                    site.predicted().describe());
        }
        if (planning.outcome().run().relaxation() > 0) {
            InhabitedWorld.LOGGER.warn("[{}] this seed needed the planner's fallback step {} ({}): see the plan's recorded rules",
                    InhabitedWorld.MOD_ID, planning.outcome().run().relaxation(), planning.outcome().run().describe());
        }
        // The semantic regions apply from the first chunk on (vanilla's spawn search generates chunks next).
        Regions.install(server);
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
