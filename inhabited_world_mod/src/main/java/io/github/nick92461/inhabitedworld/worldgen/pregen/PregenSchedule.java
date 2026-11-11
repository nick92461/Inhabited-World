package io.github.nick92461.inhabitedworld.worldgen.pregen;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;

import net.minecraft.world.level.ChunkPos;

/*
  What pre-generation generates and in which order (canon 02 section 6): a rectangle of chunks (min and max are
  inclusive), worked through in bands of whole chunk rows, north to south. Only one band is held at full status at
  a time. Each band borders the next, so the partly generated chunks a band leaves along its southern edge are the
  ones the next band needs first, and little work is thrown away when the previous band unloads.
 */
public record PregenSchedule(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int bandRows) {
    private record Fields(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int bandRows) {
    }

    // A saved schedule is checked like a new one; a bad one is a decoding error, not a crash.
    public static final Codec<PregenSchedule> CODEC = RecordCodecBuilder.<Fields>create(i -> i.group(
                    Codec.INT.fieldOf("min_chunk_x").forGetter(Fields::minChunkX),
                    Codec.INT.fieldOf("min_chunk_z").forGetter(Fields::minChunkZ),
                    Codec.INT.fieldOf("max_chunk_x").forGetter(Fields::maxChunkX),
                    Codec.INT.fieldOf("max_chunk_z").forGetter(Fields::maxChunkZ),
                    Codec.INT.fieldOf("band_rows").forGetter(Fields::bandRows))
            .apply(i, Fields::new))
            .flatXmap(
                    f -> f.maxChunkX() >= f.minChunkX() && f.maxChunkZ() >= f.minChunkZ() && f.bandRows() > 0
                            ? DataResult.success(new PregenSchedule(f.minChunkX(), f.minChunkZ(), f.maxChunkX(), f.maxChunkZ(), f.bandRows()))
                            : DataResult.error(() -> "not a valid pre-generation schedule: " + f),
                    s -> DataResult.success(new Fields(s.minChunkX(), s.minChunkZ(), s.maxChunkX(), s.maxChunkZ(), s.bandRows())));

    public PregenSchedule {
        if (bandRows <= 0) {
            throw new IllegalArgumentException("a band needs at least one chunk row, got " + bandRows);
        }
        if (maxChunkX < minChunkX || maxChunkZ < minChunkZ) {
            throw new IllegalArgumentException("empty chunk rectangle " + minChunkX + ", " + minChunkZ + " to " + maxChunkX + ", " + maxChunkZ);
        }
    }

    /** The playable area plus a ring of {@code ringChunks} chunks beyond it on every side. */
    public static PregenSchedule covering(PlayableArea area, int ringChunks, int bandRows) {
        return new PregenSchedule(area.minChunkX() - ringChunks, area.minChunkZ() - ringChunks,
                area.maxChunkX() + ringChunks, area.maxChunkZ() + ringChunks, bandRows);
    }

    /** Only the first (northernmost) {@code rows} rows of this schedule. Used to shorten development test runs. */
    public PregenSchedule firstRows(int rows) {
        return new PregenSchedule(this.minChunkX, this.minChunkZ, this.maxChunkX, Math.min(this.maxChunkZ, this.minChunkZ + rows - 1), this.bandRows);
    }

    public int width() {
        return this.maxChunkX - this.minChunkX + 1;
    }

    public int rows() {
        return this.maxChunkZ - this.minChunkZ + 1;
    }

    public boolean contains(ChunkPos pos) {
        return pos.x() >= this.minChunkX && pos.x() <= this.maxChunkX && pos.z() >= this.minChunkZ && pos.z() <= this.maxChunkZ;
    }

    public int bandCount() {
        return Math.ceilDiv(this.rows(), this.bandRows);
    }

    public int totalChunks() {
        return this.width() * this.rows();
    }

    // Number of chunks in all bands before this one.
    public int chunksBefore(int band) {
        return Math.min(band * this.bandRows, this.rows()) * this.width();
    }

    public List<ChunkPos> band(int band) {
        if (band < 0 || band >= this.bandCount()) {
            throw new IndexOutOfBoundsException("band " + band + " of " + this.bandCount());
        }
        int firstRow = this.minChunkZ + band * this.bandRows;
        int lastRow = Math.min(firstRow + this.bandRows - 1, this.maxChunkZ);
        List<ChunkPos> chunks = new ArrayList<>((lastRow - firstRow + 1) * this.width());
        for (int z = firstRow; z <= lastRow; z++) {
            for (int x = this.minChunkX; x <= this.maxChunkX; x++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    // The middle of a band, where the loading screen's chunk map looks while that band generates.
    public ChunkPos bandCenter(int band) {
        int firstRow = this.minChunkZ + band * this.bandRows;
        int lastRow = Math.min(firstRow + this.bandRows - 1, this.maxChunkZ);
        return new ChunkPos(Math.floorDiv(this.minChunkX + this.maxChunkX, 2), Math.floorDiv(firstRow + lastRow, 2));
    }
}
