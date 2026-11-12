package io.github.nick92461.inhabitedworld.worldgen.planner;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/*
  Terrain before any chunk exists (canon 02 section 8): the chunk generator's own answers, from the same noise router
  and climate sampler (RandomState) it generates chunks with.

  - surface: NoiseBasedChunkGenerator.getBaseHeight with the OCEAN_FLOOR_WG heightmap, the height of the solid
    terrain the generator's density function produces (before surface rules, carvers and features)
  - water: that surface lies below sea level
  - biome: the biome source's answer from the climate sampler, at the surface (or sea level)
 */
public final class NoiseTerrainSampler implements TerrainSampler {
    private final NoiseBasedChunkGenerator generator;
    private final RandomState random;
    private final BiomeResolver biomes;
    private final LevelHeightAccessor heights;
    private final int seaLevel;

    public NoiseTerrainSampler(NoiseBasedChunkGenerator generator, RandomState random, LevelHeightAccessor heights) {
        this.generator = generator;
        this.random = random;
        this.biomes = generator.getBiomeSource().createUncachedResolver(random);
        this.heights = heights;
        this.seaLevel = generator.getSeaLevel();
    }

    @Override
    public Column sample(int x, int z) {
        int surface = this.generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, this.heights, this.random);
        Holder<Biome> biome = this.biomes.getNoiseBiome(x >> 2, Math.max(surface, this.seaLevel) >> 2, z >> 2);
        return new Column(surface, surface < this.seaLevel, classify(biome));
    }

    /** The planner's coarse ground class for a biome, from vanilla's biome tags. */
    public static Ground classify(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) {
            return Ground.SEA;
        }
        if (biome.is(BiomeTags.IS_BEACH)) {
            return Ground.BEACH;
        }
        if (biome.is(BiomeTags.IS_RIVER)) {
            return Ground.RIVER;
        }
        if (biome.is(BiomeTags.IS_MOUNTAIN)) {
            return Ground.MOUNTAIN;
        }
        return Ground.LAND;
    }
}
