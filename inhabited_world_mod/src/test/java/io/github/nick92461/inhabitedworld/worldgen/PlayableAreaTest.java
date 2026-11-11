package io.github.nick92461.inhabitedworld.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

// Bounds math of the finite map (canon 02 section 6, 09 section 3).
class PlayableAreaTest {
    private final PlayableArea map = PlayableArea.V0_1;

    @Test
    void v01MapIs64By64ChunksAroundTheOrigin() {
        assertEquals(1024, this.map.size());
        assertEquals(-512, this.map.minBlockX());
        assertEquals(511, this.map.maxBlockX());
        assertEquals(-512, this.map.minBlockZ());
        assertEquals(511, this.map.maxBlockZ());
        assertEquals(-32, this.map.minChunkX());
        assertEquals(31, this.map.maxChunkX());
        assertEquals(64, this.map.chunksPerSide());
        assertEquals(4096, this.map.chunkCount());
    }

    @Test
    void containsExactlyItsOwnBlocksAndChunks() {
        assertTrue(this.map.contains(-512, -512));
        assertTrue(this.map.contains(511, 511));
        assertTrue(this.map.contains(0, 0));
        assertFalse(this.map.contains(-513, 0));
        assertFalse(this.map.contains(512, 0));
        assertFalse(this.map.contains(0, 512));
        assertTrue(this.map.containsChunk(-32, 31));
        assertFalse(this.map.containsChunk(32, 0));
        assertFalse(this.map.containsChunk(0, -33));
    }

    @Test
    void offCentreAreasStayChunkAligned() {
        PlayableArea area = new PlayableArea(160, -320, 512);
        assertEquals(-96, area.minBlockX());
        assertEquals(415, area.maxBlockX());
        assertEquals(-6, area.minChunkX());
        assertEquals(25, area.maxChunkX());
        assertEquals(-36, area.minChunkZ());
        assertEquals(1024, area.chunkCount());
    }

    @Test
    void rejectsAreasThatAreNotWholeChunks() {
        assertThrows(IllegalArgumentException.class, () -> new PlayableArea(0, 0, 1000));
        assertThrows(IllegalArgumentException.class, () -> new PlayableArea(8, 0, 1024));
        assertThrows(IllegalArgumentException.class, () -> new PlayableArea(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlayableArea(0, 0, 16));
    }

    @Test
    void codecRoundTripsAndValidates() {
        var json = PlayableArea.CODEC.encodeStart(JsonOps.INSTANCE, this.map).getOrThrow();
        assertEquals(this.map, PlayableArea.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());

        var bad = json.deepCopy().getAsJsonObject();
        bad.addProperty("size", 1000);
        assertTrue(PlayableArea.CODEC.parse(JsonOps.INSTANCE, bad).isError(), "a saved area that isn't whole chunks is refused");
    }
}
