package io.github.nick92461.inhabitedworld.worldgen.planner;

import java.util.function.BiFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/*
  Terrain as it was actually generated (canon 02 section 8: once the map is pre-generated, facts are verified
  against real terrain). Reads loaded chunks:

  - surface: the top of the MOTION_BLOCKING_NO_LEAVES heightmap (ground, water or a log; never leaves)
  - water: the top block holds water (sea, lake, river or aquifer water alike), or is ice over water, or is ice or
    snow in an ocean biome (an iceberg). A frozen sea is still sea; the noise sampler can't see ice or icebergs,
    which features add later.
  - biome: the chunk's stored biome at the surface

  The chunk lookup is supplied by the caller (SiteVerifier loads them), keyed by chunk coordinates.
 */
public final class GeneratedTerrainSampler implements TerrainSampler {
    private final BiFunction<Integer, Integer, ChunkAccess> chunks;

    public GeneratedTerrainSampler(BiFunction<Integer, Integer, ChunkAccess> chunks) {
        this.chunks = chunks;
    }

    @Override
    public Column sample(int x, int z) {
        ChunkAccess chunk = this.chunks.apply(x >> 4, z >> 4);
        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, top, z);
        Ground ground = NoiseTerrainSampler.classify(chunk.getNoiseBiome(x >> 2, top >> 2, z >> 2));
        BlockState state = chunk.getBlockState(pos);
        boolean frozen = state.is(BlockTags.ICE) || state.is(Blocks.SNOW_BLOCK);
        boolean water = chunk.getFluidState(pos).is(FluidTags.WATER)
                || (frozen && chunk.getFluidState(pos.below()).is(FluidTags.WATER))
                || (frozen && ground == Ground.SEA);
        return new Column(top + 1, water, ground);
    }
}
