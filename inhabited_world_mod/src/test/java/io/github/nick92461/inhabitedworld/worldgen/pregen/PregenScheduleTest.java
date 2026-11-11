package io.github.nick92461.inhabitedworld.worldgen.pregen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;

import net.minecraft.world.level.ChunkPos;

import org.junit.jupiter.api.Test;

// The pre-generation schedule and its saved progress (canon 02 section 6; ocean ring: user decision 2026-11-11).
class PregenScheduleTest {
    // Same numbers as Pregeneration.OCEAN_RING_CHUNKS and BAND_ROWS (that class registers a ticket type on load).
    private static final int RING = 12;
    private final PlayableArea map = PlayableArea.V0_1;
    private final PregenSchedule full = PregenSchedule.covering(this.map, RING, 4);

    @Test
    void coversTheMapAndTheOceanRingExactlyOnce() {
        Set<ChunkPos> seen = new HashSet<>();
        int inside = 0;
        for (int band = 0; band < this.full.bandCount(); band++) {
            for (ChunkPos pos : this.full.band(band)) {
                assertTrue(seen.add(pos), pos + " is scheduled twice");
                if (this.map.containsChunk(pos.x(), pos.z())) {
                    inside++;
                }
            }
        }
        assertEquals(88 * 88, seen.size(), "64 x 64 chunks of map and 12 chunks of ocean on every side");
        assertEquals(4096, inside, "every chunk of the map is included");
        assertEquals(seen.size(), this.full.totalChunks());
        assertEquals(22, this.full.bandCount());
    }

    @Test
    void theRingReachesTwelveChunksPastTheBorderAndNoFurther() {
        assertEquals(-44, this.full.minChunkX());
        assertEquals(43, this.full.maxChunkZ());
        assertTrue(this.full.contains(new ChunkPos(-44, 43)), "the far corner of the ring");
        assertFalse(this.full.contains(new ChunkPos(-45, 0)), "13 chunks past the border is not pre-generated");
        assertFalse(this.full.contains(new ChunkPos(0, 44)));
    }

    @Test
    void bandsRunNorthToSouthAndCountUp() {
        List<ChunkPos> first = this.full.band(0);
        assertEquals(352, first.size());
        assertEquals(new ChunkPos(-44, -44), first.getFirst());
        assertEquals(new ChunkPos(43, -41), first.getLast());
        assertEquals(new ChunkPos(43, 43), this.full.band(21).getLast());
        assertEquals(0, this.full.chunksBefore(0));
        assertEquals(352, this.full.chunksBefore(1));
        assertEquals(7744, this.full.chunksBefore(22));
        assertEquals(new ChunkPos(-1, -43), this.full.bandCenter(0));
    }

    // The development limit keeps the full width and takes rows from the north: ring first, then the map's edge.
    @Test
    void firstRowsKeepsTheNorthernStrip() {
        PregenSchedule strip = this.full.firstRows(14);
        assertEquals(88, strip.width());
        assertEquals(14, strip.rows());
        assertEquals(4, strip.bandCount(), "bands of 4, 4, 4 and 2 rows");
        assertEquals(176, strip.band(3).size());
        assertEquals(1232, strip.chunksBefore(4));
        assertTrue(strip.contains(new ChunkPos(0, -33)), "the ring's last row");
        assertTrue(strip.contains(new ChunkPos(0, -31)), "and two rows of the map itself");
        assertFalse(strip.contains(new ChunkPos(0, -30)));
        assertThrows(IndexOutOfBoundsException.class, () -> strip.band(4));
    }

    @Test
    void progressCompletesInOrderAndSurvivesSaving() {
        PregenState state = PregenState.start(this.full);
        assertFalse(state.isComplete());
        assertThrows(IllegalStateException.class, () -> state.completeBand(1), "bands can't be skipped");
        state.completeBand(0);
        state.completeBand(1);
        assertEquals(2, state.nextBand());
        assertTrue(state.isDirty());

        Codec<PregenState> codec = PregenState.codec();
        PregenState reloaded = codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, state).getOrThrow()).getOrThrow();
        assertEquals(2, reloaded.nextBand());
        assertEquals(this.full, reloaded.schedule());

        for (int band = 2; band < this.full.bandCount(); band++) {
            reloaded.completeBand(band);
        }
        assertTrue(reloaded.isComplete());
    }

    @Test
    void aBrokenSavedScheduleIsRefused() {
        JsonObject json = PregenSchedule.CODEC.encodeStart(JsonOps.INSTANCE, this.full).getOrThrow().getAsJsonObject();
        json.addProperty("band_rows", 0);
        assertTrue(PregenSchedule.CODEC.parse(JsonOps.INSTANCE, json).isError());
    }
}
