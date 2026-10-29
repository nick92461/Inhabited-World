package io.github.nick92461.inhabitedworld.mixin;

import java.util.List;
import java.util.concurrent.Executor;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.spawn.VillageSpawners;
import io.github.nick92461.inhabitedworld.worldgen.RpgWorlds;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Drops the wandering-trader and village-siege spawners from an Inhabited World level (see VillageSpawners).
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Shadow
    @Final
    @Mutable
    private List<CustomSpawner> customSpawners;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void inhabitedWorld$dropVillageSpawners(MinecraftServer server, Executor executor, LevelStorageSource.LevelStorageAccess levelStorage,
                                                   ServerLevelData levelData, ResourceKey<Level> dimension, LevelStem levelStem, boolean isDebug,
                                                   long biomeZoomSeed, List<CustomSpawner> spawners, boolean tickTime, CallbackInfo ci) {
        if (RpgWorlds.isRpg(levelStem.generator()) && this.customSpawners.stream().anyMatch(VillageSpawners::isVillageSpawner)) {
            this.customSpawners = VillageSpawners.without(this.customSpawners);
            InhabitedWorld.LOGGER.info("[{}] level {}: removed the wandering-trader and village-siege spawners", InhabitedWorld.MOD_ID,
                    dimension.identifier());
        }
    }
}
