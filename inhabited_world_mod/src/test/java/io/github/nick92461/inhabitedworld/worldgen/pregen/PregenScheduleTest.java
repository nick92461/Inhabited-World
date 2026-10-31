package io.github.nick92461.inhabitedworld.worldgen.pregen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;

import net.minecraft.world.level.ChunkPos;

import org.junit.jupiter.api.Test;

// The pre-generation schedule and its saved progress (canon 02 section 6).
class PregenScheduleTest {
    private final PregenSchedule full = new PregenSchedule(PlayableArea.V0_1, 4);

    @Test
    void coversEveryChunkOfTheMapExactlyOnce() {
        Set<ChunkPos> seen = new HashSet<>();
        for (int band = 0; band < this.full.bandCount(); band++) {
            for (ChunkPos pos : this.full.band(band)) {
                assertTrue(PlayableArea.V0_1.containsChunk(pos.x(), pos.z()), pos + " lies outside the map");
                assertTrue(seen.add(pos), pos + " is scheduled twice");
            }
        }
        assertEquals(4096, seen.size());
        assertEquals(4096, this.full.totalChunks());
        assertEquals(16, this.full.bandCount());
    }

    @Test
    void bandsRunNorthToSouthAndCountUp() {
        List<ChunkPos> first = this.full.band(0);
        assertEquals(256, first.size());
        assertEquals(new ChunkPos(-32, -32), first.getFirst());
        assertEquals(new ChunkPos(31, -29), first.getLast());
        assertEquals(new ChunkPos(31, 31), this.full.band(15).getLast());
        assertEquals(0, this.full.chunksBefore(0));
        assertEquals(256, this.full.chunksBefore(1));
        assertEquals(4096, this.full.chunksBefore(16));
        assertEquals(new ChunkPos(-1, -31), this.full.bandCenter(0));
    }

    @Test
    void aShortLastBandIsHandled() {
        // 6 rows in bands of 4: one band of 4 rows, then one of 2.
        PregenSchedule schedule = new PregenSchedule(PlayableArea.V0_1.shrunkTo(3), 4);
        assertEquals(2, schedule.bandCount());
        assertEquals(24, schedule.band(0).size());
        assertEquals(12, schedule.band(1).size());
        assertEquals(36, schedule.chunksBefore(2));
        assertThrows(IndexOutOfBoundsException.class, () -> schedule.band(2));
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
}
