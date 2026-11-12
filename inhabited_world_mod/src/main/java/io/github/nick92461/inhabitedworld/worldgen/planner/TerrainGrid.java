package io.github.nick92461.inhabitedworld.worldgen.planner;

/*
  Terrain sampled once per chunk, at each chunk's middle column (block 8, 8 of the chunk), over a chunk-aligned
  rectangle. The planner evaluates every candidate site from this lattice, so the generator is asked about each
  column once, however many candidate sites overlap it.
 */
public final class TerrainGrid {
    /** Blocks between lattice columns: one per chunk. */
    public static final int STEP = 16;

    private final int originX;
    private final int originZ;
    private final int width;
    private final int depth;
    private final int[] surfaceY;
    private final boolean[] water;
    private final Ground[] ground;

    private TerrainGrid(int originX, int originZ, int width, int depth) {
        this.originX = originX;
        this.originZ = originZ;
        this.width = width;
        this.depth = depth;
        this.surfaceY = new int[width * depth];
        this.water = new boolean[width * depth];
        this.ground = new Ground[width * depth];
    }

    /** Samples every chunk of a chunk-aligned rectangle (row by row, north to south, in a fixed order). */
    public static TerrainGrid sample(TerrainSampler sampler, BlockRect chunks) {
        if (Math.floorMod(chunks.minX(), STEP) != 0 || Math.floorMod(chunks.minZ(), STEP) != 0
                || chunks.sizeX() % STEP != 0 || chunks.sizeZ() % STEP != 0) {
            throw new IllegalArgumentException("terrain grids cover whole chunks, not " + chunks);
        }
        TerrainGrid grid = new TerrainGrid(chunks.minX(), chunks.minZ(), chunks.sizeX() / STEP, chunks.sizeZ() / STEP);
        for (int j = 0; j < grid.depth; j++) {
            for (int i = 0; i < grid.width; i++) {
                TerrainSampler.Column column = sampler.sample(grid.columnX(i), grid.columnZ(j));
                int index = j * grid.width + i;
                grid.surfaceY[index] = column.surfaceY();
                grid.water[index] = column.water();
                grid.ground[index] = column.ground();
            }
        }
        return grid;
    }

    public BlockRect bounds() {
        return BlockRect.ofSize(this.originX, this.originZ, this.width * STEP, this.depth * STEP);
    }

    public int columnCount() {
        return this.width * this.depth;
    }

    /** The block x of lattice column i. */
    public int columnX(int i) {
        return this.originX + i * STEP + STEP / 2;
    }

    public int columnZ(int j) {
        return this.originZ + j * STEP + STEP / 2;
    }

    /** The lattice column for the chunk containing block x (may be outside the grid). */
    public int cellX(int blockX) {
        return Math.floorDiv(blockX - this.originX, STEP);
    }

    public int cellZ(int blockZ) {
        return Math.floorDiv(blockZ - this.originZ, STEP);
    }

    public boolean inside(int i, int j) {
        return i >= 0 && j >= 0 && i < this.width && j < this.depth;
    }

    /** The sampled column, or null outside the grid. */
    public TerrainSampler.Column column(int i, int j) {
        if (!this.inside(i, j)) {
            return null;
        }
        int index = j * this.width + i;
        return new TerrainSampler.Column(this.surfaceY[index], this.water[index], this.ground[index]);
    }
}
