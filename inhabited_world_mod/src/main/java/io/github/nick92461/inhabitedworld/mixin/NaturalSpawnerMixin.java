package io.github.nick92461.inhabitedworld.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import io.github.nick92461.inhabitedworld.spawn.SpawnGovernance;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/*
  A POI's own hostiles (canon 06 section 17.3: allowed and weighted hostile types). NaturalSpawner.mobsAt is where
  natural spawning picks what may spawn at a spot (the biome's list, or a nether fortress's); both the random pick and
  the later "may this type spawn here" check read it. Inside a region whose threat profile lists its own hostiles,
  the monster list is the profile's: cave spiders can then spawn naturally in an old mine, and nothing outside the
  list is ever picked there. Other categories (animals, bats, fish) keep the biome's lists. Allocation-free: the
  profile's list is built once.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    @ModifyReturnValue(method = "mobsAt", at = @At("RETURN"))
    private static WeightedList<MobSpawnSettings.SpawnerData> inhabitedWorld$profileMonsters(WeightedList<MobSpawnSettings.SpawnerData> original,
                                                                                             ServerLevel level, StructureManager structureManager,
                                                                                             ChunkGenerator generator, MobCategory category, BlockPos pos) {
        if (category != MobCategory.MONSTER) {
            return original;
        }
        WeightedList<MobSpawnSettings.SpawnerData> profile = SpawnGovernance.naturalMonstersAt(level, pos);
        return profile == null ? original : profile;
    }
}
