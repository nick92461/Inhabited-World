package io.github.nick92461.inhabitedworld.worldgen.planner;

/*
  One source of terrain facts for the planner (canon 02 section 8: "the same sampling mechanism should support both"
  planning and later geographic analysis). Two implementations exist:

  - NoiseTerrainSampler reads the chunk generator's own noise and climate before any chunk exists (planning)
  - GeneratedTerrainSampler reads generated chunks after pre-generation (verification)
 */
@FunctionalInterface
public interface TerrainSampler {
    /** The column at block (x, z). */
    Column sample(int x, int z);

    /**
     * One column: the height of its solid surface (the y just above the top solid block), whether water covers it,
     * and the coarse class of its biome.
     */
    record Column(int surfaceY, boolean water, Ground ground) {
        /** Dry ground a building can stand on: not under water, not a river or the sea. */
        public boolean dryLand() {
            return !this.water && this.ground != Ground.RIVER && this.ground != Ground.SEA;
        }

        /** Open sea: water over a sea or beach biome (rivers and lakes don't count). */
        public boolean sea() {
            return this.water && (this.ground == Ground.SEA || this.ground == Ground.BEACH);
        }
    }
}
