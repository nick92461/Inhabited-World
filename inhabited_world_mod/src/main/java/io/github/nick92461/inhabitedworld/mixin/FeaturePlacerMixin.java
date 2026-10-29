package io.github.nick92461.inhabitedworld.mixin;

import io.github.nick92461.inhabitedworld.worldgen.FeatureSuppression;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
  Feature suppression (canon 06 section 15). In 26.3 chunk decoration calls FeaturePlacer.placeWithBiomeCheck
  directly, and PlacedFeature.place delegates to FeaturePlacer.place, so both meet in this private method.
  A placer is made per chunk decoration (and per nested placement), so the suppressed set is looked up once
  per placer.
 */
@Mixin(FeaturePlacer.class)
public abstract class FeaturePlacerMixin {
    @Shadow
    @Final
    private WorldGenLevel level;

    @Shadow
    @Final
    private ChunkGenerator generator;

    @Unique
    private HolderSet<PlacedFeature> inhabitedWorld$suppressed;

    @Inject(
            method = "place(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void inhabitedWorld$suppressUncontrolledFeatures(PlacedFeature feature, RandomSource random, BlockPos origin, boolean biomeCheck,
                                                             CallbackInfoReturnable<Boolean> cir) {
        if (this.inhabitedWorld$suppressed == null) {
            this.inhabitedWorld$suppressed = FeatureSuppression.forGenerator(this.generator, this.level.registryAccess());
        }
        if (FeatureSuppression.isSuppressed(this.inhabitedWorld$suppressed, feature)) {
            cir.setReturnValue(false);
        }
    }
}
