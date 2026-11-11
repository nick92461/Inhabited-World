package io.github.nick92461.inhabitedworld.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;
import io.github.nick92461.inhabitedworld.worldgen.RpgWorlds;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
  The island in its ocean (canon 02 section 6; shape chosen by the user 2026-11-11): rpg_overworld_v2 for six seeds,
  read straight from the generator (surface heights and biomes every 32 blocks across the map and 256 blocks of sea
  around it), so any seed can be checked without generating chunks.

  Seed 20261029 is the test launch's; seed 1 puts vanilla's middle mostly under the sea. The same checks run on the
  first version (rpg_overworld, vanilla terrain) as the negative control: there, land reaches past the border and
  seed 1's map is mostly sea, so each check can fail.
 */
public class IslandGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger("inhabited_world/gametest");
    static final long[] SEEDS = {20261029L, 0L, 1L, 42L, 1234567L, 987654321L};
    private static final int SEA_LEVEL = 63;
    private static final int STEP = 32;
    private static final int REACH = 768;
    private static final double MIN_LAND = 0.25;

    private record Terrain(double landInside, int landOutside, int nonOceanBiomeOutside, int samplesOutside) {
    }

    private static Terrain survey(ServerLevel level, ResourceKey<NoiseGeneratorSettings> version, long seed) {
        Holder<NoiseGeneratorSettings> settings = level.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(version);
        HolderGetter<NormalNoise> noises = level.registryAccess().lookupOrThrow(Registries.NOISE);
        BiomeSource biomes = level.getChunkSource().getGenerator().getBiomeSource();
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, settings);
        RandomState random = RandomState.create(noises, seed, settings.value());
        BiomeResolver biomeAt = biomes.createUncachedResolver(random);
        LevelHeightAccessor heights = LevelHeightAccessor.create(-64, 384);
        PlayableArea area = PlayableArea.V0_1;

        int inside = 0, landInside = 0, outside = 0, landOutside = 0, nonOcean = 0;
        for (int z = -REACH + STEP / 2; z < REACH; z += STEP) {
            for (int x = -REACH + STEP / 2; x < REACH; x += STEP) {
                int surface = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random);
                boolean land = surface >= SEA_LEVEL;
                if (area.contains(x, z)) {
                    inside++;
                    if (land) landInside++;
                } else {
                    outside++;
                    if (land) landOutside++;
                    Holder<Biome> biome = biomeAt.getNoiseBiome(x >> 2, Math.max(surface, SEA_LEVEL) >> 2, z >> 2);
                    if (!biome.is(BiomeTags.IS_OCEAN)) nonOcean++;
                }
            }
        }
        return new Terrain(landInside / (double) inside, landOutside, nonOcean, outside);
    }

    @GameTest(maxTicks = 2000)
    public void islandIsLandInsideAndOceanBeyondTheBorder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> fractions = new ArrayList<>();
        for (long seed : SEEDS) {
            Terrain island = survey(level, RpgWorlds.NOISE_SETTINGS_V2, seed);
            fractions.add(String.format(Locale.ROOT, "%d: %.0f%%", seed, island.landInside() * 100));
            helper.assertTrue(island.landInside() >= MIN_LAND,
                    "seed " + seed + ": only " + Math.round(island.landInside() * 100) + "% of the map is land");
            helper.assertTrue(island.landOutside() == 0, "seed " + seed + ": " + island.landOutside() + " of "
                    + island.samplesOutside() + " points beyond the border are land");
            helper.assertTrue(island.nonOceanBiomeOutside() == 0, "seed " + seed + ": " + island.nonOceanBiomeOutside()
                    + " points beyond the border have a non-ocean biome (biomes must agree with the terrain)");
        }
        LOGGER.info("island land inside the border (rpg_overworld_v2): {}", String.join(", ", fractions));
        helper.succeed();
    }

    // Negative control: vanilla terrain (v1) fails the checks above, so they are not passing vacuously.
    @GameTest(maxTicks = 2000)
    public void vanillaTerrainFailsTheIslandChecks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int seedsWithLandOutside = 0;
        int seedsWithLandBiomesOutside = 0;
        for (long seed : SEEDS) {
            Terrain vanilla = survey(level, RpgWorlds.NOISE_SETTINGS, seed);
            if (vanilla.landOutside() > 0) seedsWithLandOutside++;
            if (vanilla.nonOceanBiomeOutside() > 0) seedsWithLandBiomesOutside++;
        }
        helper.assertTrue(seedsWithLandOutside == SEEDS.length, "vanilla terrain should reach past the border on every seed, but did on " + seedsWithLandOutside);
        helper.assertTrue(seedsWithLandBiomesOutside == SEEDS.length, "and land biomes should lie past it");
        helper.assertTrue(survey(level, RpgWorlds.NOISE_SETTINGS, 1L).landInside() < MIN_LAND, "vanilla seed 1 should leave the map mostly sea");
        helper.succeed();
    }

    /*
      Vanilla's spawn search (the climate-based origin it starts from) lands on the island: inside the map, within its
      middle (the coast fades out from 300 blocks), and on dry land.
     */
    @GameTest(maxTicks = 2000)
    public void spawnSearchLandsOnTheIsland(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<NoiseGeneratorSettings> settings = level.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(RpgWorlds.NOISE_SETTINGS_V2);
        HolderGetter<NormalNoise> noises = level.registryAccess().lookupOrThrow(Registries.NOISE);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(level.getChunkSource().getGenerator().getBiomeSource(), settings);
        for (long seed : SEEDS) {
            RandomState random = RandomState.create(noises, seed, settings.value());
            ChunkPos origin = generator.getOrigin(random);
            int x = origin.getWorldPosition().getX() + 8, z = origin.getWorldPosition().getZ() + 8;
            helper.assertTrue(Math.abs(x) < 300 && Math.abs(z) < 300, "seed " + seed + ": spawn search starts at " + origin + ", outside the island's middle");
            int surface = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, LevelHeightAccessor.create(-64, 384), random);
            helper.assertTrue(surface >= SEA_LEVEL, "seed " + seed + ": spawn search starts in water (surface " + surface + ")");
        }
        helper.succeed();
    }
}
