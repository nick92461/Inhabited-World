package io.github.nick92461.inhabitedworld.worldgen.planner;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/*
  Runs the planner on a generator before any chunk exists (canon 02 sections 6 and 8): samples the generator's
  terrain over the map once (one column per chunk, NoiseTerrainSampler), then plans. Used at world creation and by
  the multi-seed survey, which builds a RandomState per seed.
 */
public final class NoisePlanning {
    private NoisePlanning() {
    }

    /** The planner's result plus how long sampling and planning took, for the log. */
    public record Timed(SitePlanner.Outcome outcome, TerrainGrid grid, long samplingMillis, long planningMillis) {
    }

    public static Timed plan(NoiseBasedChunkGenerator generator, RandomState random, long seed, LevelHeightAccessor heights, PlanRequest request) {
        long started = System.nanoTime();
        TerrainGrid grid = TerrainGrid.sample(new NoiseTerrainSampler(generator, random, heights), SitePlanner.samplingArea(request));
        long sampled = System.nanoTime();
        SitePlanner.Outcome outcome = SitePlanner.plan(request, grid, seed, spawnOrigin(generator, random));
        long planned = System.nanoTime();
        return new Timed(outcome, grid, (sampled - started) / 1_000_000, (planned - sampled) / 1_000_000);
    }

    /** Where vanilla's spawn search starts: the middle of the generator's climate-based origin chunk. */
    public static SitePlanner.SpawnOrigin spawnOrigin(NoiseBasedChunkGenerator generator, RandomState random) {
        ChunkPos origin = generator.getOrigin(random);
        return new SitePlanner.SpawnOrigin(origin.getMinBlockX() + 8, origin.getMinBlockZ() + 8);
    }
}
