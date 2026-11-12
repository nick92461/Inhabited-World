package io.github.nick92461.inhabitedworld.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import io.github.nick92461.inhabitedworld.region.Regions;
import io.github.nick92461.inhabitedworld.worldgen.pregen.Pregeneration;
import io.github.nick92461.inhabitedworld.worldstate.SiteVerifier;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlans;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.LevelLoadListener;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
  World creation and level preparation (canon 02 sections 6 and 7):

  1. createLevels, only for a world being created (vanilla's "not initialized yet" branch), just before vanilla's
     spawn search generates the first chunks: make the WorldPlan (the planner chooses the sites) and set the world
     border.
  2. prepareLevels, at the start: build the semantic-region index from the saved plan (Regions), pre-generate the
     playable area if the plan says so and it isn't finished, then
     verify the planned sites against the generated terrain once pre-generation is complete.
  3. If pre-generation reported progress as the LOAD_INITIAL_CHUNKS stage, vanilla doesn't start that stage again
     (a second start would push the loading bar past 100%). Vanilla still loads its own initial chunks; for a new
     world there are none.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Shadow
    @Final
    private LevelLoadListener levelLoadListener;

    @Unique
    private boolean inhabitedWorld$pregenerationStartedInitialChunks;

    @Inject(
            method = "createLevels",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;setInitialSpawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/storage/ServerLevelData;ZZLnet/minecraft/server/level/progress/LevelLoadListener;)V"))
    private void inhabitedWorld$planBeforeFirstChunk(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        WorldPlans.createAtWorldCreation(server, server.overworld());
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"))
    private void inhabitedWorld$pregenerate(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        // An opened world gets its semantic regions before any chunk generates or loads and before the first mob spawns.
        Regions.install(server);
        this.inhabitedWorld$pregenerationStartedInitialChunks = Pregeneration.runIfNeeded(server, this.levelLoadListener);
        SiteVerifier.verifyIfNeeded(server);
    }

    @WrapWithCondition(
            method = "prepareLevels",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/progress/LevelLoadListener;start(Lnet/minecraft/server/level/progress/LevelLoadListener$Stage;I)V"))
    private boolean inhabitedWorld$startInitialChunksOnce(LevelLoadListener listener, LevelLoadListener.Stage stage, int totalChunks) {
        return !this.inhabitedWorld$pregenerationStartedInitialChunks;
    }
}
