package io.github.nick92461.inhabitedworld.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/*
  The finite map's playable area (canon 02 section 6, 09 section 3): a square of whole chunks, given by its centre
  and side length in blocks. It covers blocks [center - size/2, center + size/2) on both axes, which is exactly
  what a vanilla world border with the same centre and size encloses.
 */
public record PlayableArea(int centerX, int centerZ, int size) {
    /*
      The Version 0.1 map: canon asks for "approximately 1024 x 1024 playable blocks" (09 section 3). This is
      exactly 64 x 64 chunks centred on the world origin, which is where vanilla's spawn search starts.
     */
    public static final PlayableArea V0_1 = new PlayableArea(0, 0, 1024);

    private record Fields(int centerX, int centerZ, int size) {
    }

    // Saved areas are checked like new ones; a bad one is a decoding error, not a crash.
    public static final Codec<PlayableArea> CODEC = RecordCodecBuilder.<Fields>create(i -> i.group(
                    Codec.INT.fieldOf("center_x").forGetter(Fields::centerX),
                    Codec.INT.fieldOf("center_z").forGetter(Fields::centerZ),
                    Codec.INT.fieldOf("size").forGetter(Fields::size))
            .apply(i, Fields::new))
            .flatXmap(
                    f -> {
                        String problem = problem(f.centerX(), f.centerZ(), f.size());
                        return problem == null ? DataResult.success(new PlayableArea(f.centerX(), f.centerZ(), f.size())) : DataResult.error(() -> problem);
                    },
                    area -> DataResult.success(new Fields(area.centerX(), area.centerZ(), area.size())));

    public PlayableArea {
        String problem = problem(centerX, centerZ, size);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
    }

    // Null when the area is valid: a positive whole number of chunks per side, with chunk-aligned edges.
    private static String problem(int centerX, int centerZ, int size) {
        if (size <= 0 || size % 32 != 0) {
            return "playable area size must be a positive multiple of 32 blocks (an even number of chunks), got " + size;
        }
        if (Math.floorMod(centerX - size / 2, 16) != 0 || Math.floorMod(centerZ - size / 2, 16) != 0) {
            return "playable area edges must lie on chunk boundaries (centre " + centerX + ", " + centerZ + ", size " + size + ")";
        }
        return null;
    }

    public int minBlockX() {
        return this.centerX - this.size / 2;
    }

    public int minBlockZ() {
        return this.centerZ - this.size / 2;
    }

    // Inclusive.
    public int maxBlockX() {
        return this.centerX + this.size / 2 - 1;
    }

    // Inclusive.
    public int maxBlockZ() {
        return this.centerZ + this.size / 2 - 1;
    }

    public int minChunkX() {
        return this.minBlockX() >> 4;
    }

    public int minChunkZ() {
        return this.minBlockZ() >> 4;
    }

    // Inclusive.
    public int maxChunkX() {
        return this.maxBlockX() >> 4;
    }

    // Inclusive.
    public int maxChunkZ() {
        return this.maxBlockZ() >> 4;
    }

    public int chunksPerSide() {
        return this.size / 16;
    }

    public int chunkCount() {
        return this.chunksPerSide() * this.chunksPerSide();
    }

    public boolean contains(int blockX, int blockZ) {
        return blockX >= this.minBlockX() && blockX <= this.maxBlockX() && blockZ >= this.minBlockZ() && blockZ <= this.maxBlockZ();
    }

    public boolean containsChunk(int chunkX, int chunkZ) {
        return chunkX >= this.minChunkX() && chunkX <= this.maxChunkX() && chunkZ >= this.minChunkZ() && chunkZ <= this.maxChunkZ();
    }
}
