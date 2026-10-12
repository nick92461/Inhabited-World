package io.github.nick92461.inhabitedworld.mixin;

import com.mojang.serialization.Lifecycle;

import io.github.nick92461.inhabitedworld.worldgen.RpgWorlds;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsMixin {
    @ModifyArg(
            method = "bake",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/MappedRegistry;<init>(Lnet/minecraft/resources/ResourceKey;Lcom/mojang/serialization/Lifecycle;)V"),
            index = 1)
    private Lifecycle inhabitedWorld$stableRegistryIfOurs(Lifecycle original) {
        LevelStem overworld = ((WorldDimensions) (Object)this).dimensions().get(LevelStem.OVERWORLD);
        return overworld != null && RpgWorlds.isRpg(overworld.generator()) ? Lifecycle.stable() : original;
    }

    @Inject(method = "checkStability", at = @At("HEAD"), cancellable = true)
    private static void inhabitedWorld$stableStemIfOurs(ResourceKey<LevelStem> key, LevelStem stem, CallbackInfoReturnable<Lifecycle> cir) {
        if (RpgWorlds.isRpg(stem.generator())) {
            cir.setReturnValue(Lifecycle.stable());
        }
    }
}
