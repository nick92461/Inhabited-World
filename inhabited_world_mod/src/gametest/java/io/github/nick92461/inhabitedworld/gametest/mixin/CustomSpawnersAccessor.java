package io.github.nick92461.inhabitedworld.gametest.mixin;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CustomSpawner;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Test-only: lets game tests read a level's custom spawners.
@Mixin(ServerLevel.class)
public interface CustomSpawnersAccessor {
    @Accessor("customSpawners")
    List<CustomSpawner> inhabitedWorldTest$customSpawners();
}
