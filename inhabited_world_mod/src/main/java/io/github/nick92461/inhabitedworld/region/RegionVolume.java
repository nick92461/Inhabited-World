package io.github.nick92461.inhabitedworld.region;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.nick92461.inhabitedworld.worldgen.planner.BlockRect;

/*
  A box of blocks, inclusive on every side: the three-dimensional space of a semantic region (canon 06 section 17.4:
  regions are 3D, so a mine can lie under wilderness and a protected building inside a settlement). Its footprint on
  the map is a BlockRect.
 */
public record RegionVolume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    private record Fields(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    public static final Codec<RegionVolume> CODEC = RecordCodecBuilder.<Fields>create(i -> i.group(
                    Codec.INT.fieldOf("min_x").forGetter(Fields::minX),
                    Codec.INT.fieldOf("min_y").forGetter(Fields::minY),
                    Codec.INT.fieldOf("min_z").forGetter(Fields::minZ),
                    Codec.INT.fieldOf("max_x").forGetter(Fields::maxX),
                    Codec.INT.fieldOf("max_y").forGetter(Fields::maxY),
                    Codec.INT.fieldOf("max_z").forGetter(Fields::maxZ))
            .apply(i, Fields::new))
            .flatXmap(
                    f -> f.maxX() >= f.minX() && f.maxY() >= f.minY() && f.maxZ() >= f.minZ()
                            ? DataResult.success(new RegionVolume(f.minX(), f.minY(), f.minZ(), f.maxX(), f.maxY(), f.maxZ()))
                            : DataResult.error(() -> "empty region volume " + f),
                    v -> DataResult.success(new Fields(v.minX(), v.minY(), v.minZ(), v.maxX(), v.maxY(), v.maxZ())));

    public RegionVolume {
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            throw new IllegalArgumentException("empty region volume: " + minX + ", " + minY + ", " + minZ + " to " + maxX + ", " + maxY + ", " + maxZ);
        }
    }

    /** The footprint's columns from minY to maxY. */
    public static RegionVolume of(BlockRect footprint, int minY, int maxY) {
        return new RegionVolume(footprint.minX(), minY, footprint.minZ(), footprint.maxX(), maxY, footprint.maxZ());
    }

    public BlockRect footprint() {
        return new BlockRect(this.minX, this.minZ, this.maxX, this.maxZ);
    }

    public boolean contains(int x, int y, int z) {
        return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
    }

    public long blockCount() {
        return (long) (this.maxX - this.minX + 1) * (this.maxY - this.minY + 1) * (this.maxZ - this.minZ + 1);
    }

    @Override
    public String toString() {
        return "(" + this.minX + ", " + this.minY + ", " + this.minZ + ") to (" + this.maxX + ", " + this.maxY + ", " + this.maxZ + ")";
    }
}
