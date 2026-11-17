package io.github.nick92461.inhabitedworld.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.encounter.EncounterRecord.Loaded;
import io.github.nick92461.inhabitedworld.encounter.EncounterRecord.State;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/*
  The guaranteed-encounter state machine and its store (canon 06 sections 17.7, 17.8; 08 section 49): not spawned ->
  active -> defeated, with "lost" back to not spawned; only the encounter's own mob counts; save and load round-trip
  in NBT (the save format) and JSON; the store belongs to one world ID.
 */
class EncounterRecordTest {
    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-00000000f00d");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    private static final BlockPos SPOT = new BlockPos(-240, 71, -240);
    private static final Identifier FOREMAN = InhabitedWorld.id("placeholder_mine_foreman");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aRequiredEnemyGoesFromNotSpawnedToActiveToDefeated() {
        EncounterRecord fresh = EncounterRecord.NEW;
        assertEquals(State.NOT_SPAWNED, fresh.state());
        EncounterRecord active = fresh.spawned(MOB, SPOT);
        assertEquals(State.ACTIVE, active.state());
        assertEquals(Optional.of(MOB), active.entity());
        assertEquals(1, active.spawns());
        EncounterRecord moved = active.seen(MOB, SPOT.east(3));
        assertEquals(Optional.of(SPOT.east(3)), moved.lastPos());
        EncounterRecord defeated = moved.defeated(MOB, "player");
        assertEquals(State.DEFEATED, defeated.state());
        assertEquals(Optional.empty(), defeated.entity());
        assertEquals(1, defeated.defeats());
        assertEquals(Optional.of("player"), defeated.defeatedBy());
    }

    @Test
    void itSpawnsOnlyFromNotSpawned() {
        EncounterRecord active = EncounterRecord.NEW.spawned(MOB, SPOT);
        assertThrows(IllegalStateException.class, () -> active.spawned(OTHER, SPOT), "an active encounter never gets a second mob");
        EncounterRecord defeated = active.defeated(MOB, "player");
        assertThrows(IllegalStateException.class, () -> defeated.spawned(OTHER, SPOT), "a defeated encounter is not spawned again");
    }

    @Test
    void onlyItsOwnMobChangesIt() {
        EncounterRecord active = EncounterRecord.NEW.spawned(MOB, SPOT);
        assertSame(active, active.defeated(OTHER, "player"), "a duplicate dying doesn't defeat it");
        assertSame(active, active.lost(OTHER), "a duplicate removed doesn't lose it");
        assertSame(active, active.seen(OTHER, SPOT.west(9)), "a duplicate's position isn't its position");
        assertEquals(Loaded.KEEP, active.judgeLoaded(MOB));
        assertEquals(Loaded.REMOVE, active.judgeLoaded(OTHER), "a second copy is removed, so it never duplicates");
    }

    @Test
    void aDefeatedEncounterStaysDefeatedUnlessItRespawns() {
        EncounterRecord defeated = EncounterRecord.NEW.spawned(MOB, SPOT).defeated(MOB, "minecraft:generic_kill");
        assertEquals(Loaded.REMOVE, defeated.judgeLoaded(MOB), "its corpse or an old copy loading doesn't bring it back");
        assertSame(defeated, defeated.rearm(false));
        assertSame(defeated, defeated.lost(MOB));
        assertSame(defeated, defeated.defeated(MOB, "again"));
        EncounterRecord rearmed = defeated.rearm(true);
        assertEquals(State.NOT_SPAWNED, rearmed.state());
        assertEquals(1, rearmed.defeats(), "counters survive");
        assertEquals(2, rearmed.spawned(OTHER, SPOT).spawns());
    }

    @Test
    void aMobThatLeavesWithoutDyingIsLostAndSpawnsAgain() {
        EncounterRecord lost = EncounterRecord.NEW.spawned(MOB, SPOT).lost(MOB);
        assertEquals(State.NOT_SPAWNED, lost.state());
        assertEquals(Loaded.REMOVE, lost.judgeLoaded(MOB), "if the lost mob turns up again it is removed");
        assertEquals(State.ACTIVE, lost.spawned(OTHER, SPOT).state());
        assertEquals(0, lost.defeats());
    }

    @Test
    void resetReturnsToNotSpawnedFromAnyState() {
        for (EncounterRecord r : new EncounterRecord[]{EncounterRecord.NEW, EncounterRecord.NEW.spawned(MOB, SPOT),
                EncounterRecord.NEW.spawned(MOB, SPOT).defeated(MOB, "player")}) {
            EncounterRecord reset = r.reset();
            assertEquals(State.NOT_SPAWNED, reset.state());
            assertTrue(reset.entity().isEmpty());
            assertEquals(Loaded.REMOVE, reset.judgeLoaded(MOB));
        }
    }

    @Test
    void recordsRoundTripAndRejectInconsistentState() {
        for (EncounterRecord r : new EncounterRecord[]{EncounterRecord.NEW, EncounterRecord.NEW.spawned(MOB, SPOT),
                EncounterRecord.NEW.spawned(MOB, SPOT).defeated(MOB, "player"), EncounterRecord.NEW.spawned(MOB, SPOT).lost(MOB)}) {
            Tag nbt = EncounterRecord.CODEC.encodeStart(NbtOps.INSTANCE, r).getOrThrow();
            assertEquals(r, EncounterRecord.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow(), "NBT " + r);
            assertEquals(r, EncounterRecord.CODEC.parse(JsonOps.INSTANCE, EncounterRecord.CODEC.encodeStart(JsonOps.INSTANCE, r).getOrThrow()).getOrThrow());
        }
        EncounterRecord broken = new EncounterRecord(State.ACTIVE, Optional.empty(), Optional.empty(), 1, 0, Optional.empty());
        assertTrue(EncounterRecord.CODEC.encodeStart(JsonOps.INSTANCE, broken).flatMap(j -> EncounterRecord.CODEC.parse(JsonOps.INSTANCE, j)).isError(),
                "an active encounter must name its mob");
    }

    @Test
    void theStoreRoundTripsWithItsDataVersionAndWorld() {
        UUID world = UUID.randomUUID();
        EncounterData data = new EncounterData();
        assertFalse(data.claim(world), "an unclaimed store is claimed");
        data.put(FOREMAN, EncounterRecord.NEW.spawned(MOB, SPOT));
        Tag nbt = EncounterData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        assertEquals(EncounterData.DATA_VERSION, ((net.minecraft.nbt.CompoundTag) nbt).getIntOr("data_version", -1));
        EncounterData loaded = EncounterData.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        assertEquals(world, loaded.worldId());
        assertEquals(data.get(FOREMAN), loaded.get(FOREMAN));
        assertEquals(EncounterRecord.NEW, loaded.get(InhabitedWorld.id("never_spawned")), "encounters without an entry are not spawned");
        assertFalse(loaded.claim(world), "its own world keeps it");
        assertEquals(State.ACTIVE, loaded.get(FOREMAN).state());
    }

    @Test
    void aStoreFromAnotherWorldIsNotTrusted() {
        EncounterData data = new EncounterData();
        data.claim(UUID.randomUUID());
        data.put(FOREMAN, EncounterRecord.NEW.spawned(MOB, SPOT).defeated(MOB, "player"));
        assertTrue(data.claim(UUID.randomUUID()), "another world's store is emptied");
        assertEquals(State.NOT_SPAWNED, data.get(FOREMAN).state());
    }

    @Test
    void aNewerDataVersionIsRefused() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("data_version", EncounterData.DATA_VERSION + 1);
        assertTrue(EncounterData.CODEC.parse(NbtOps.INSTANCE, tag).isError());
    }

    @Test
    void tagsWorkInSelectorsUnquoted() {
        String tag = EncounterRuntime.tagOf(InhabitedWorld.id("mine/foreman"));
        assertEquals("inhabited_world.encounter.inhabited_world.mine.foreman", tag);
        assertTrue(tag.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-'));
    }
}
