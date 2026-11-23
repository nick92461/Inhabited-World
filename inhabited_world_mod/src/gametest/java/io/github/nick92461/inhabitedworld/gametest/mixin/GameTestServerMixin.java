package io.github.nick92461.inhabitedworld.gametest.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldOptions;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
  Vanilla places the game tests at a random spot up to 15 million blocks out. An Inhabited World is finite: out
  there is beyond the world border, where players can't use items and entities don't tick. So the tests
  run inside the playable area instead, from (-384, 4, -384) towards the centre, still deep underground.

  Vanilla's game-test world always has seed 0. -PgameTestSeed=<seed> (system property inhabited_world.dev.gameTestSeed)
  creates it with another seed instead, to measure world creation on the test launch's seed 20261029.
 */
@Mixin(GameTestServer.class)
public abstract class GameTestServerMixin {
    @Unique
    private static final int TEST_ORIGIN = -384;

    @Shadow
    @Final
    @Mutable
    private static WorldOptions WORLD_OPTIONS;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void inhabitedWorldTest$seed(CallbackInfo ci) {
        String seed = System.getProperty("inhabited_world.dev.gameTestSeed");
        if (seed != null && !seed.isBlank()) {
            WORLD_OPTIONS = new WorldOptions(Long.parseLong(seed.trim()), false, false);
        }
    }

    @Redirect(method = "startTests", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextIntBetweenInclusive(II)I"))
    private int inhabitedWorldTest$insidePlayableArea(RandomSource random, int min, int max) {
        return TEST_ORIGIN;
    }
}
