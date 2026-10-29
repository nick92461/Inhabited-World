package io.github.nick92461.inhabitedworld.spawn;

import java.util.List;

import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;

/*
  The overworld's custom spawners (MinecraftServer.createLevels) include two that bring vanilla village life:
  WanderingTraderSpawner (traders and their llamas, canon 06 section 13) and VillageSiege (zombie sieges of
  villages). They don't go through the spawn check, so an Inhabited World drops them. The phantom, patrol and
  cat spawners stay: phantoms are ordinary monsters, patrols are off through the dimension type, and cats are
  animals.
 */
public final class VillageSpawners {
    private VillageSpawners() {
    }

    public static boolean isVillageSpawner(CustomSpawner spawner) {
        return spawner instanceof WanderingTraderSpawner || spawner instanceof VillageSiege;
    }

    public static List<CustomSpawner> without(List<CustomSpawner> spawners) {
        return spawners.stream().filter(spawner -> !isVillageSpawner(spawner)).toList();
    }
}
