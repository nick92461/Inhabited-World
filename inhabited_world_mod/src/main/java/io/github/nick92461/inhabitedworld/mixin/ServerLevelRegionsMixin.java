package io.github.nick92461.inhabitedworld.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import io.github.nick92461.inhabitedworld.region.RegionIndex;
import io.github.nick92461.inhabitedworld.region.RegionIndexHolder;
import io.github.nick92461.inhabitedworld.spawn.SpawnGovernance;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/*
  Semantic regions on the level (canon 06 sections 17.1 and 17.6):

  1. Each ServerLevel carries its region index (RegionIndexHolder). Volatile, because chunk-generation threads read it
     while spawning a new chunk's animals; it is set before the first chunk generates and never mutated after.
  2. Skeleton traps. In a thunderstorm vanilla sometimes turns a lightning strike into a skeleton-horse trap
     (ServerLevel.tickThunder, reason EVENT, outside SpawnPlacements.checkSpawnRules) whose four skeleton horsemen
     appear when a player comes close. That is weather making hostiles from nothing, ordinary hostile generation, so
     a region that suppresses it (a protected settlement) gets an ordinary lightning strike there instead, exactly as
     vanilla does when mob spawning is off. The wilderness keeps its traps.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelRegionsMixin implements RegionIndexHolder {
    @Unique
    private volatile @Nullable RegionIndex inhabitedWorld$regionIndex;

    @Override
    public @Nullable RegionIndex inhabitedWorld$regionIndex() {
        return this.inhabitedWorld$regionIndex;
    }

    @Override
    public void inhabitedWorld$setRegionIndex(@Nullable RegionIndex index) {
        this.inhabitedWorld$regionIndex = index;
    }

    // The trap's first condition is the spawn_mobs game rule (GameRules.get, the only game-rule read in tickThunder).
    @ModifyExpressionValue(method = "tickThunder", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object inhabitedWorld$noSkeletonTrapInProtectedRegions(Object spawnMobs, @Local BlockPos pos) {
        if (spawnMobs instanceof Boolean allowed && allowed && !SpawnGovernance.allowsSkeletonTrap((ServerLevel) (Object) this, pos)) {
            return Boolean.FALSE;
        }
        return spawnMobs;
    }
}
