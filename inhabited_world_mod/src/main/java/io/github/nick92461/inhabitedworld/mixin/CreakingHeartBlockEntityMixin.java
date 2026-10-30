package io.github.nick92461.inhabitedworld.mixin;

import io.github.nick92461.inhabitedworld.spawn.SpawnGovernance;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.level.block.entity.CreakingHeartBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
  Generated creaking hearts stay dormant in an Inhabited World (canon 06 section 15, user decision 2026-10-29).
  An awake heart summons its creaking in spawnProtector through SpawnUtil.trySpawnMob (reason SPAWNER), which
  skips SpawnPlacements.checkSpawnRules, so SpawnPlacementsMixin never sees it. Returning null is vanilla's own
  "no spot found" result: the heart simply tries again on its next update. This is not a hot path (once per
  heart update, every 20 to 24 ticks, and only while an awake heart without a creaking has a player within 32).
 */
@Mixin(CreakingHeartBlockEntity.class)
public abstract class CreakingHeartBlockEntityMixin {
    @Inject(method = "spawnProtector", at = @At("HEAD"), cancellable = true)
    private static void inhabitedWorld$governHeart(ServerLevel level, CreakingHeartBlockEntity heart,
                                                   CallbackInfoReturnable<Creaking> cir) {
        if (!SpawnGovernance.allowsCreakingHeart(level, heart.getBlockPos(), heart.getBlockState())) {
            cir.setReturnValue(null);
        }
    }
}
