package io.github.nick92461.inhabitedworld.mixin;

import io.github.nick92461.inhabitedworld.worldgen.RpgWorlds;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @ModifyArg(
        method = "createState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;createForNormal(Lnet/minecraft/world/level/levelgen/RandomState;JLnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/core/HolderLookup;)Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;"),
    index = 4)

    private HolderLookup<StructureSet>inhabitedWorld$allowlistStructures(HolderLookup<StructureSet> structureSets) {
        return RpgWorlds.isRpg((ChunkGenerator) (Object) this) ? RpgWorlds.allowlisted(structureSets) : structureSets;
    }

}
