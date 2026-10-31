package io.github.nick92461.inhabitedworld.gametest.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.util.RandomSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/*
  Vanilla places the game tests at a random spot up to 15 million blocks out. An Inhabited World is finite: out
  there is beyond the world border, where players can't use items and entities don't tick. So the tests
  run inside the playable area instead, from (-384, 4, -384) towards the centre, still deep underground.
 */
@Mixin(GameTestServer.class)
public abstract class GameTestServerMixin {
    @Unique
    private static final int TEST_ORIGIN = -384;

    @Redirect(method = "startTests", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextIntBetweenInclusive(II)I"))
    private int inhabitedWorldTest$insidePlayableArea(RandomSource random, int min, int max) {
        return TEST_ORIGIN;
    }
}
