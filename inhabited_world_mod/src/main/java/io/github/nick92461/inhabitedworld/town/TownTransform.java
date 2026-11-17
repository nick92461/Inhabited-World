package io.github.nick92461.inhabitedworld.town;

import io.github.nick92461.inhabitedworld.worldgen.planner.BlockRect;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

/*
  Where a town's template space lands in the world once the town is turned by a planned site's rotation (canon 02
  section 16: "when a structure is placed and rotated, these markers should resolve into world positions").

  Template space: x east, z south, origin at the box's north-west corner, template y 0 at world y baseY. The rotation
  is a structure-template rotation about template (0, 0); the turned box is then moved so that it fills the site's
  bounds exactly (PlannedSite: "Phase 3 rotates the template by it and moves it so that it fills the bounds
  exactly"). With W and D the template's x and z sizes and (mx, mz) the bounds' north-west corner:

    NONE                (x, z) -> (mx + x,         mz + z)
    CLOCKWISE_90        (x, z) -> (mx + D - 1 - z, mz + x)
    CLOCKWISE_180       (x, z) -> (mx + W - 1 - x, mz + D - 1 - z)
    COUNTERCLOCKWISE_90 (x, z) -> (mx + z,         mz + W - 1 - x)

  These are vanilla's StructureTemplate.transform (pivot 0) plus a shift, so a tile placed by vanilla at
  tileOrigin(offset) with the same rotation puts each of its blocks exactly where toWorld says. Yaw follows
  Minecraft's convention (0 faces south, 90 west, 180 north, 270 east): a clockwise quarter turn adds 90 degrees.

  Plain value maths, no world access: unit-tested in TownTransformTest.
 */
public record TownTransform(Rotation rotation, int sizeX, int sizeZ, int minX, int baseY, int minZ) {
    public TownTransform {
        if (sizeX <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("a town box has a positive size, got " + sizeX + " x " + sizeZ);
        }
    }

    /** The transform that puts a sizeX x sizeZ template into a site's bounds, turned by the site's rotation. */
    public static TownTransform forSite(PlannedSite site, int sizeX, int sizeZ, int baseY) {
        return of(site.bounds(), site.rotation(), sizeX, sizeZ, baseY);
    }

    public static TownTransform of(BlockRect bounds, Rotation rotation, int sizeX, int sizeZ, int baseY) {
        TownTransform t = new TownTransform(rotation, sizeX, sizeZ, bounds.minX(), baseY, bounds.minZ());
        if (!t.bounds().equals(bounds)) {
            throw new IllegalArgumentException("a " + sizeX + " x " + sizeZ + " town turned " + rotation.getSerializedName()
                    + " covers " + t.bounds().sizeX() + " x " + t.bounds().sizeZ() + ", not the site's bounds " + bounds);
        }
        return t;
    }

    /** The world rectangle the turned town covers. */
    public BlockRect bounds() {
        boolean quarter = PlannedSite.quarterTurn(this.rotation);
        return BlockRect.ofSize(this.minX, this.minZ, quarter ? this.sizeZ : this.sizeX, quarter ? this.sizeX : this.sizeZ);
    }

    public int worldX(int x, int z) {
        return this.minX + switch (this.rotation) {
            case NONE -> x;
            case CLOCKWISE_90 -> this.sizeZ - 1 - z;
            case CLOCKWISE_180 -> this.sizeX - 1 - x;
            case COUNTERCLOCKWISE_90 -> z;
        };
    }

    public int worldZ(int x, int z) {
        return this.minZ + switch (this.rotation) {
            case NONE -> z;
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> this.sizeZ - 1 - z;
            case COUNTERCLOCKWISE_90 -> this.sizeX - 1 - x;
        };
    }

    public int worldY(int y) {
        return this.baseY + y;
    }

    /** A template block position in the world. */
    public BlockPos toWorld(BlockPos template) {
        return new BlockPos(this.worldX(template.getX(), template.getZ()), this.worldY(template.getY()), this.worldZ(template.getX(), template.getZ()));
    }

    /** The template column a world column holds, as {x, z}, or null when it lies outside the town's bounds. */
    public int[] toTemplate(int wx, int wz) {
        int lx = wx - this.minX;
        int lz = wz - this.minZ;
        int x;
        int z;
        switch (this.rotation) {
            case NONE -> {
                x = lx;
                z = lz;
            }
            case CLOCKWISE_90 -> {
                x = lz;
                z = this.sizeZ - 1 - lx;
            }
            case CLOCKWISE_180 -> {
                x = this.sizeX - 1 - lx;
                z = this.sizeZ - 1 - lz;
            }
            default -> {
                x = this.sizeX - 1 - lz;
                z = lx;
            }
        }
        return x < 0 || z < 0 || x >= this.sizeX || z >= this.sizeZ ? null : new int[]{x, z};
    }

    /*
      Where vanilla must place a tile whose corner sits at the given template offset, with this rotation and pivot 0,
      so that its blocks land where toWorld says. toWorld(t) = S + rotate(t), with S = toWorld(0, 0); vanilla puts a
      tile's local block b at position + rotate(b); so position = S + rotate(offset).
     */
    public BlockPos tileOrigin(BlockPos offset) {
        int ox = offset.getX();
        int oz = offset.getZ();
        int rx;
        int rz;
        switch (this.rotation) {
            case NONE -> {
                rx = ox;
                rz = oz;
            }
            case CLOCKWISE_90 -> {
                rx = -oz;
                rz = ox;
            }
            case CLOCKWISE_180 -> {
                rx = -ox;
                rz = -oz;
            }
            default -> {
                rx = oz;
                rz = -ox;
            }
        }
        // the world image of template (0, 0) under rotate-then-shift; rotate(0) = 0, so it is the shift itself
        int sx = this.worldX(0, 0);
        int sz = this.worldZ(0, 0);
        return new BlockPos(sx + rx, this.worldY(offset.getY()), sz + rz);
    }

    /** A template yaw (degrees, Minecraft convention) turned by the rotation, in [0, 360). */
    public float yaw(float templateYaw) {
        float turned = templateYaw + switch (this.rotation) {
            case NONE -> 0f;
            case CLOCKWISE_90 -> 90f;
            case CLOCKWISE_180 -> 180f;
            case COUNTERCLOCKWISE_90 -> 270f;
        };
        float wrapped = turned % 360f;
        return wrapped < 0 ? wrapped + 360f : wrapped;
    }
}
