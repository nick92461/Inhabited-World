package io.github.nick92461.inhabitedworld.town;

import java.io.IOException;
import java.io.InputStream;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/*
  The town's terrain mask (columns.nbt, written by the exporter): one entry per column of the template box, index
  z * width + x, heights in template space.

    zone   0 BLEND (nothing stored: generated terrain is blended here), 1 MOLD (the build), 2 SUPPORT (ground the build
           stands in), 3 SEA (open water at sea level)
    floor  lowest stored y; below it the placer guarantees solid ground
    top    highest stored y; above it is air
    ground the terrain the column stands on (the seabed for water)
 */
public record TownColumns(int width, int depth, byte[] zone, int[] floor, int[] top, int[] ground) {
    public static final byte BLEND = 0;
    public static final byte MOLD = 1;
    public static final byte SUPPORT = 2;
    public static final byte SEA = 3;

    public boolean stored(int x, int z) {
        return zone[z * width + x] != BLEND;
    }

    public byte zone(int x, int z) {
        return zone[z * width + x];
    }

    public int floor(int x, int z) {
        return floor[z * width + x];
    }

    public int top(int x, int z) {
        return top[z * width + x];
    }

    public int ground(int x, int z) {
        return ground[z * width + x];
    }

    public static TownColumns load(ResourceManager resources, Identifier id) {
        try (InputStream in = resources.open(id)) {
            return of(NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read town columns " + id, e);
        }
    }

    static TownColumns of(CompoundTag tag) {
        int width = tag.getIntOr("width", 0);
        int depth = tag.getIntOr("depth", 0);
        byte[] zone = tag.getByteArray("zone").orElseThrow();
        int[] floor = tag.getIntArray("floor").orElseThrow();
        int[] top = tag.getIntArray("top").orElseThrow();
        int[] ground = tag.getIntArray("ground").orElseThrow();
        if (zone.length != width * depth || floor.length != zone.length || top.length != zone.length || ground.length != zone.length) {
            throw new IllegalStateException("town columns: array sizes do not match " + width + " x " + depth);
        }
        return new TownColumns(width, depth, zone, floor, top, ground);
    }
}
