package io.github.nick92461.inhabitedworld.region;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/*
  The in-memory semantic-region lookup used on the spawn path (canon 06 section 17.6: in-memory region data,
  deterministic lookup, minimal allocation, plain Java). Built once from the WorldPlan's regions when the world opens;
  immutable afterwards, so chunk-generation threads may read it too.

  Layout: a grid of chunk columns covering the regions' combined footprint. Each cell lists the regions whose
  footprint touches that chunk column, most specific first (06 section 17.4: POI over settlement over wilderness;
  among regions of one kind the smaller volume wins, then the ID, so the order is deterministic). A lookup is:
  - outside the grid, or a cell with no regions: the wilderness, after one bounds check and one array read. Every
    chunk that no region touches skips region checks entirely.
  - otherwise: the first listed region whose 3D box contains the position, else the wilderness.
  No step allocates. The grid grows with the area the regions span, not with their number, so a large world with
  many settlements (implementation plan, long-term direction) costs one int per chunk column.
 */
public final class RegionIndex {
    private static final Comparator<SemanticRegion> PRECEDENCE = Comparator
            .comparingInt((SemanticRegion r) -> -r.kind().specificity())
            .thenComparingLong(r -> r.volume().blockCount())
            .thenComparing(SemanticRegion::id);

    private final SemanticRegion wilderness;
    private final SemanticRegion[] regions;
    private final int[] minX;
    private final int[] minY;
    private final int[] minZ;
    private final int[] maxX;
    private final int[] maxY;
    private final int[] maxZ;

    private final int gridMinChunkX;
    private final int gridMinChunkZ;
    private final int gridSizeX;
    private final int gridSizeZ;
    // Cell c lists cellRegions[cellStart[c]] to cellRegions[cellStart[c + 1] - 1], indices into regions.
    private final int[] cellStart;
    private final int[] cellRegions;

    private RegionIndex(SemanticRegion wilderness, List<SemanticRegion> regions) {
        if (!wilderness.isWilderness()) {
            throw new IllegalArgumentException("the fallback region must be the wilderness, got " + wilderness.id());
        }
        this.wilderness = wilderness;
        List<SemanticRegion> sorted = new ArrayList<>(regions);
        sorted.sort(PRECEDENCE);
        int count = sorted.size();
        this.regions = sorted.toArray(new SemanticRegion[0]);
        this.minX = new int[count];
        this.minY = new int[count];
        this.minZ = new int[count];
        this.maxX = new int[count];
        this.maxY = new int[count];
        this.maxZ = new int[count];
        int lowCX = Integer.MAX_VALUE;
        int lowCZ = Integer.MAX_VALUE;
        int highCX = Integer.MIN_VALUE;
        int highCZ = Integer.MIN_VALUE;
        for (int r = 0; r < count; r++) {
            SemanticRegion region = this.regions[r];
            if (region.isWilderness()) {
                throw new IllegalArgumentException("the wilderness is not indexed as a region");
            }
            RegionVolume v = region.volume();
            this.minX[r] = v.minX();
            this.minY[r] = v.minY();
            this.minZ[r] = v.minZ();
            this.maxX[r] = v.maxX();
            this.maxY[r] = v.maxY();
            this.maxZ[r] = v.maxZ();
            lowCX = Math.min(lowCX, v.minX() >> 4);
            lowCZ = Math.min(lowCZ, v.minZ() >> 4);
            highCX = Math.max(highCX, v.maxX() >> 4);
            highCZ = Math.max(highCZ, v.maxZ() >> 4);
        }
        if (count == 0) {
            this.gridMinChunkX = 0;
            this.gridMinChunkZ = 0;
            this.gridSizeX = 0;
            this.gridSizeZ = 0;
            this.cellStart = new int[1];
            this.cellRegions = new int[0];
            return;
        }
        this.gridMinChunkX = lowCX;
        this.gridMinChunkZ = lowCZ;
        this.gridSizeX = highCX - lowCX + 1;
        this.gridSizeZ = highCZ - lowCZ + 1;
        int cells = Math.multiplyExact(this.gridSizeX, this.gridSizeZ);

        // Two passes: count each cell's regions, then fill them in precedence order (regions are already sorted).
        int[] counts = new int[cells];
        for (int r = 0; r < count; r++) {
            forEachCell(r, cell -> counts[cell]++);
        }
        this.cellStart = new int[cells + 1];
        for (int c = 0; c < cells; c++) {
            this.cellStart[c + 1] = this.cellStart[c] + counts[c];
        }
        this.cellRegions = new int[this.cellStart[cells]];
        int[] fill = Arrays.copyOf(this.cellStart, cells);
        for (int r = 0; r < count; r++) {
            int region = r;
            forEachCell(r, cell -> this.cellRegions[fill[cell]++] = region);
        }
    }

    private interface CellAction {
        void apply(int cell);
    }

    private void forEachCell(int r, CellAction action) {
        for (int cz = (this.minZ[r] >> 4) - this.gridMinChunkZ; cz <= (this.maxZ[r] >> 4) - this.gridMinChunkZ; cz++) {
            for (int cx = (this.minX[r] >> 4) - this.gridMinChunkX; cx <= (this.maxX[r] >> 4) - this.gridMinChunkX; cx++) {
                action.apply(cz * this.gridSizeX + cx);
            }
        }
    }

    /**
     * @param wilderness what applies where no region does (SiteRegions.wilderness for a planned world)
     * @param regions    the world's settlement and POI regions, in any order
     */
    public static RegionIndex of(SemanticRegion wilderness, List<SemanticRegion> regions) {
        return new RegionIndex(wilderness, regions);
    }

    /** The region that applies at a block: the most specific one containing it, else the wilderness. Allocation-free. */
    public SemanticRegion regionAt(int x, int y, int z) {
        int cell = this.cell(x >> 4, z >> 4);
        if (cell < 0) {
            return this.wilderness;
        }
        for (int k = this.cellStart[cell], end = this.cellStart[cell + 1]; k < end; k++) {
            int r = this.cellRegions[k];
            if (x >= this.minX[r] && x <= this.maxX[r] && z >= this.minZ[r] && z <= this.maxZ[r] && y >= this.minY[r] && y <= this.maxY[r]) {
                return this.regions[r];
            }
        }
        return this.wilderness;
    }

    /** Whether no region touches this chunk column, so every position in it is wilderness without further checks. */
    public boolean isWildernessChunk(int chunkX, int chunkZ) {
        int cell = this.cell(chunkX, chunkZ);
        return cell < 0 || this.cellStart[cell] == this.cellStart[cell + 1];
    }

    // The grid cell of a chunk column, or -1 outside the grid.
    private int cell(int chunkX, int chunkZ) {
        int gx = chunkX - this.gridMinChunkX;
        int gz = chunkZ - this.gridMinChunkZ;
        if (gx < 0 || gz < 0 || gx >= this.gridSizeX || gz >= this.gridSizeZ) {
            return -1;
        }
        return gz * this.gridSizeX + gx;
    }

    /** Every region containing a block, the applying one first; empty in the wilderness. For diagnostics (allocates). */
    public List<SemanticRegion> regionsAt(int x, int y, int z) {
        List<SemanticRegion> found = new ArrayList<>();
        for (SemanticRegion region : this.regions) {
            if (region.volume().contains(x, y, z)) {
                found.add(region);
            }
        }
        return found;
    }

    /** The indexed regions, most specific first. */
    public List<SemanticRegion> regions() {
        return List.of(this.regions);
    }

    public SemanticRegion wilderness() {
        return this.wilderness;
    }

    /** Grid size in chunk columns (diagnostics and tests). */
    public int gridCells() {
        return this.gridSizeX * this.gridSizeZ;
    }
}
