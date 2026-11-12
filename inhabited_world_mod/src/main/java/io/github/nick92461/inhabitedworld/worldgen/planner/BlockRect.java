package io.github.nick92461.inhabitedworld.worldgen.planner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/*
  A rectangle of block columns, inclusive on both ends: the 2D bounds of a planned site or of its semantic region.
  Phase 2B extends regions to 3D volumes; their footprint on the map stays one of these.
 */
public record BlockRect(int minX, int minZ, int maxX, int maxZ) {
    private record Fields(int minX, int minZ, int maxX, int maxZ) {
    }

    public static final Codec<BlockRect> CODEC = RecordCodecBuilder.<Fields>create(i -> i.group(
                    Codec.INT.fieldOf("min_x").forGetter(Fields::minX),
                    Codec.INT.fieldOf("min_z").forGetter(Fields::minZ),
                    Codec.INT.fieldOf("max_x").forGetter(Fields::maxX),
                    Codec.INT.fieldOf("max_z").forGetter(Fields::maxZ))
            .apply(i, Fields::new))
            .flatXmap(
                    f -> f.maxX() >= f.minX() && f.maxZ() >= f.minZ()
                            ? DataResult.success(new BlockRect(f.minX(), f.minZ(), f.maxX(), f.maxZ()))
                            : DataResult.error(() -> "empty rectangle " + f),
                    r -> DataResult.success(new Fields(r.minX(), r.minZ(), r.maxX(), r.maxZ())));

    public BlockRect {
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("empty rectangle: " + minX + ", " + minZ + " to " + maxX + ", " + maxZ);
        }
    }

    /** The rectangle of the given size whose north-west corner is (minX, minZ). */
    public static BlockRect ofSize(int minX, int minZ, int sizeX, int sizeZ) {
        return new BlockRect(minX, minZ, minX + sizeX - 1, minZ + sizeZ - 1);
    }

    public int sizeX() {
        return this.maxX - this.minX + 1;
    }

    public int sizeZ() {
        return this.maxZ - this.minZ + 1;
    }

    /** The centre, as doubles: a rectangle of even size has its centre on a block edge. */
    public double centerX() {
        return (this.minX + this.maxX + 1) / 2.0;
    }

    public double centerZ() {
        return (this.minZ + this.maxZ + 1) / 2.0;
    }

    public BlockRect inflate(int by) {
        return new BlockRect(this.minX - by, this.minZ - by, this.maxX + by, this.maxZ + by);
    }

    public boolean contains(int x, int z) {
        return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
    }

    public boolean contains(BlockRect other) {
        return other.minX >= this.minX && other.maxX <= this.maxX && other.minZ >= this.minZ && other.maxZ <= this.maxZ;
    }

    public boolean intersects(BlockRect other) {
        return other.minX <= this.maxX && other.maxX >= this.minX && other.minZ <= this.maxZ && other.maxZ >= this.minZ;
    }

    /** The straight-line gap between the two rectangles in blocks (0 when they touch or overlap). */
    public double gapTo(BlockRect other) {
        int dx = Math.max(0, Math.max(other.minX - this.maxX - 1, this.minX - other.maxX - 1));
        int dz = Math.max(0, Math.max(other.minZ - this.maxZ - 1, this.minZ - other.maxZ - 1));
        return Math.sqrt((double) dx * dx + (double) dz * dz);
    }

    /** The straight-line distance from a point to the nearest block of this rectangle (0 inside it). */
    public double distanceTo(double x, double z) {
        double dx = Math.max(0, Math.max(this.minX - x, x - (this.maxX + 1)));
        double dz = Math.max(0, Math.max(this.minZ - z, z - (this.maxZ + 1)));
        return Math.sqrt(dx * dx + dz * dz);
    }

    public double centerDistanceTo(BlockRect other) {
        return Math.hypot(this.centerX() - other.centerX(), this.centerZ() - other.centerZ());
    }

    @Override
    public String toString() {
        return "(" + this.minX + ", " + this.minZ + ") to (" + this.maxX + ", " + this.maxZ + ")";
    }
}
