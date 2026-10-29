package io.github.nick92461.inhabitedworld.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import io.github.nick92461.inhabitedworld.spawn.SpawnGovernance;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/*
  Spawn governance hook (canon 06 sections 13, 17.5, 17.6). checkSpawnRules is the shared check for natural and
  chunk-generation spawning, spawner blocks, trial spawners and zombie reinforcements, and it runs before the
  entity is built. @ModifyReturnValue (not a cancellable @Inject) keeps it allocation-free.
 */
@Mixin(SpawnPlacements.class)
public abstract class SpawnPlacementsMixin {
    @ModifyReturnValue(method = "checkSpawnRules", at = @At("RETURN"))
    private static boolean inhabitedWorld$governSpawn(boolean original, EntityType<?> type, ServerLevelAccessor level,
                                                      EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return original && SpawnGovernance.allows(type, level, reason, pos);
    }
}
