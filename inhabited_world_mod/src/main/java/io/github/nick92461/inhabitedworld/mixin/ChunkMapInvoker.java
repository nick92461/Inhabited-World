package io.github.nick92461.inhabitedworld.mixin;

import java.util.function.BooleanSupplier;

import net.minecraft.server.level.ChunkMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// Pre-generation runs before the first level tick, so it drives chunk unloading and saving itself.
@Mixin(ChunkMap.class)
public interface ChunkMapInvoker {
    @Invoker("tick")
    void inhabitedWorld$tick(BooleanSupplier haveTime);
}
