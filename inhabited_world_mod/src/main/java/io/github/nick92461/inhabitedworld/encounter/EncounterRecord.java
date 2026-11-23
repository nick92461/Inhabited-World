package io.github.nick92461.inhabitedworld.encounter;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;

/*
  The persistent state of one guaranteed encounter (canon 06 section 17.8: not yet spawned, currently active,
  defeated), as a small immutable state machine. Pure: Encounters applies it to the running world, and the transition
  rules are unit-tested on their own.

    NOT_SPAWNED --spawned(mob)--> ACTIVE --defeated(mob)--> DEFEATED
         ^                          |                          |
         +-------lost(mob)----------+                          |
         +------------------rearm() (respawning encounters)----+
    reset(): back to NOT_SPAWNED from anywhere (development)

  - entity: the UUID of the mob while ACTIVE. Only that mob counts: another mob carrying the encounter's tag is a
    duplicate (a copy from an old save, a mob that came back after being declared lost) and is removed.
  - lastPos: where the mob was last known to be (updated while it is loaded and when its chunk unloads), so the world
    can tell "its chunk isn't loaded" from "it is gone".
  - spawns, defeats: how often it has been spawned and defeated.
  - defeatedBy: what killed it last (a damage type, "player" when a player did).

  "lost" means the mob left the world without dying (removed by a command, by peaceful difficulty, through a portal, or
  missing from its loaded chunks): an ordinary removal must not end a required encounter, so it is spawned again the
  next time a player comes near (06 section 17.8: a required enemy should not disappear permanently by despawning).
 */
public record EncounterRecord(State state, Optional<UUID> entity, Optional<BlockPos> lastPos, int spawns, int defeats, Optional<String> defeatedBy) {
    public enum State implements StringRepresentable {
        NOT_SPAWNED,
        ACTIVE,
        DEFEATED;

        public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** What to do with a mob carrying this encounter's tag when it appears in the world (its chunk loads). */
    public enum Loaded {
        /** It is this encounter's mob. */
        KEEP,
        /** It is not (the encounter is defeated, not spawned, or has another mob): remove it, so nothing duplicates. */
        REMOVE
    }

    public static final EncounterRecord NEW = new EncounterRecord(State.NOT_SPAWNED, Optional.empty(), Optional.empty(), 0, 0, Optional.empty());

    public static final Codec<EncounterRecord> CODEC = RecordCodecBuilder.<EncounterRecord>create(i -> i.group(
                    State.CODEC.fieldOf("state").forGetter(EncounterRecord::state),
                    UUIDUtil.CODEC.optionalFieldOf("entity").forGetter(EncounterRecord::entity),
                    BlockPos.CODEC.optionalFieldOf("last_pos").forGetter(EncounterRecord::lastPos),
                    Codec.INT.optionalFieldOf("spawns", 0).forGetter(EncounterRecord::spawns),
                    Codec.INT.optionalFieldOf("defeats", 0).forGetter(EncounterRecord::defeats),
                    Codec.STRING.optionalFieldOf("defeated_by").forGetter(EncounterRecord::defeatedBy))
            .apply(i, EncounterRecord::new))
            .validate(EncounterRecord::check);

    private static DataResult<EncounterRecord> check(EncounterRecord r) {
        if ((r.state() == State.ACTIVE) != r.entity().isPresent()) {
            return DataResult.error(() -> "an encounter names its mob exactly while it is active, not when " + r.state().getSerializedName());
        }
        return DataResult.success(r);
    }

    public boolean isActive() {
        return this.state == State.ACTIVE;
    }

    /** Whether this mob is the encounter's current one. */
    public boolean is(UUID mob) {
        return this.entity.isPresent() && this.entity.get().equals(mob);
    }

    public EncounterRecord spawned(UUID mob, BlockPos pos) {
        this.require(State.NOT_SPAWNED, "spawn");
        return new EncounterRecord(State.ACTIVE, Optional.of(mob), Optional.of(pos.immutable()), this.spawns + 1, this.defeats, this.defeatedBy);
    }

    /** The active mob was seen at pos (no change if it is not this encounter's mob). */
    public EncounterRecord seen(UUID mob, BlockPos pos) {
        if (!this.isActive() || !this.is(mob) || this.lastPos.isPresent() && this.lastPos.get().equals(pos)) {
            return this;
        }
        return new EncounterRecord(State.ACTIVE, this.entity, Optional.of(pos.immutable()), this.spawns, this.defeats, this.defeatedBy);
    }

    /** The mob died. Only the encounter's own mob defeats it; a duplicate dying changes nothing. */
    public EncounterRecord defeated(UUID mob, String cause) {
        if (!this.isActive() || !this.is(mob)) {
            return this;
        }
        return new EncounterRecord(State.DEFEATED, Optional.empty(), this.lastPos, this.spawns, this.defeats + 1, Optional.of(cause));
    }

    /** The mob left the world without dying: the encounter waits to be spawned again. */
    public EncounterRecord lost(UUID mob) {
        if (!this.isActive() || !this.is(mob)) {
            return this;
        }
        return new EncounterRecord(State.NOT_SPAWNED, Optional.empty(), this.lastPos, this.spawns, this.defeats, this.defeatedBy);
    }

    /** A defeated encounter that respawns becomes spawnable again. */
    public EncounterRecord rearm(boolean respawns) {
        if (this.state != State.DEFEATED || !respawns) {
            return this;
        }
        return new EncounterRecord(State.NOT_SPAWNED, Optional.empty(), this.lastPos, this.spawns, this.defeats, this.defeatedBy);
    }

    /** Development: back to not spawned, counters kept. The caller removes a loaded mob. */
    public EncounterRecord reset() {
        return new EncounterRecord(State.NOT_SPAWNED, Optional.empty(), Optional.empty(), this.spawns, this.defeats, this.defeatedBy);
    }

    public Loaded judgeLoaded(UUID mob) {
        return this.isActive() && this.is(mob) ? Loaded.KEEP : Loaded.REMOVE;
    }

    private void require(State expected, String what) {
        if (this.state != expected) {
            throw new IllegalStateException("cannot " + what + " an encounter that is " + this.state.getSerializedName());
        }
    }

    public String describe() {
        return this.state.getSerializedName() + this.entity.map(u -> ", mob " + u).orElse("") + this.lastPos.map(p -> ", last seen at " + p.toShortString()).orElse("")
                + ", spawned " + this.spawns + "x, defeated " + this.defeats + "x" + this.defeatedBy.map(d -> " (last by " + d + ")").orElse("");
    }
}
