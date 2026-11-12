package io.github.nick92461.inhabitedworld.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import io.github.nick92461.inhabitedworld.spawn.SpawnGovernance;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.levelgen.PhantomSpawner;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/*
  Phantoms bypass SpawnPlacements.checkSpawnRules: PhantomSpawner checks only that the spot 20 to 34 blocks above a
  sleepless player is empty, then spawns them with reason NATURAL. They are ordinary hostile generation, so they get
  the same decision as any natural spawn at that spot (canon 06 section 17.1): none over a protected settlement,
  vanilla over the wilderness. Runs at most once per player per spawn attempt (every 60 to 120 seconds).
 */
@Mixin(PhantomSpawner.class)
public abstract class PhantomSpawnerMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/NaturalSpawner;isValidEmptySpawnBlock(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/entity/EntityType;)Z"))
    private boolean inhabitedWorld$governPhantoms(boolean emptySpot, @Local(argsOnly = true) ServerLevel level,
                                                   @Local(ordinal = 1) BlockPos spawnPos) {
        return emptySpot && SpawnGovernance.allows(EntityTypes.PHANTOM, level, EntitySpawnReason.NATURAL, spawnPos);
    }
}
