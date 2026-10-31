package io.github.nick92461.inhabitedworld.worldgen.pregen;

import java.util.ArrayList;
import java.util.List;

import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;

import net.minecraft.world.level.ChunkPos;

/*
  The order in which pre-generation works through the playable area (canon 02 section 6): bands of whole chunk
  rows, north to south. Only one band is held at full status at a time. Each band borders the next, so the
  partly generated chunks a band leaves along its southern edge are the ones the next band needs first, and
  little work is thrown away when the previous band unloads.
 */
public record PregenSchedule(PlayableArea area, int bandRows) {
    public PregenSchedule {
        if (bandRows <= 0) {
            throw new IllegalArgumentException("a band needs at least one chunk row, got " + bandRows);
        }
    }

    public int bandCount() {
        return Math.ceilDiv(this.area.chunksPerSide(), this.bandRows);
    }

    public int totalChunks() {
        return this.area.chunkCount();
    }

    // Number of chunks in all bands before this one.
    public int chunksBefore(int band) {
        return Math.min(band * this.bandRows, this.area.chunksPerSide()) * this.area.chunksPerSide();
    }

    public List<ChunkPos> band(int band) {
        if (band < 0 || band >= this.bandCount()) {
            throw new IndexOutOfBoundsException("band " + band + " of " + this.bandCount());
        }
        int firstRow = this.area.minChunkZ() + band * this.bandRows;
        int lastRow = Math.min(firstRow + this.bandRows - 1, this.area.maxChunkZ());
        List<ChunkPos> chunks = new ArrayList<>((lastRow - firstRow + 1) * this.area.chunksPerSide());
        for (int z = firstRow; z <= lastRow; z++) {
            for (int x = this.area.minChunkX(); x <= this.area.maxChunkX(); x++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    // The middle of a band, where the loading screen's chunk map looks while that band generates.
    public ChunkPos bandCenter(int band) {
        int firstRow = this.area.minChunkZ() + band * this.bandRows;
        int lastRow = Math.min(firstRow + this.bandRows - 1, this.area.maxChunkZ());
        return new ChunkPos((this.area.minChunkX() + this.area.maxChunkX()) >> 1, (firstRow + lastRow) >> 1);
    }
}
