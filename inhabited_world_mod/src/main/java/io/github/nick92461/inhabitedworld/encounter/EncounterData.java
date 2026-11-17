package io.github.nick92461.inhabitedworld.encounter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.nick92461.inhabitedworld.InhabitedWorld;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/*
  The guaranteed-encounter store (canon 06 section 17.8; 08 section 49: mutable state in its own domain store, apart
  from the immutable WorldPlan, with its own data version). Saved in the world's data folder as
  data/inhabited_world/encounters.dat:

    {"data_version": 1, "world_id": "<the WorldPlan's world ID>",
     "encounters": {"inhabited_world:placeholder_mine_foreman": {"state": "active", "entity": [...], ...}}}

  Scoped to the persisted RPG-world identity (02 section 4): it records the world ID it belongs to, and a store
  whose ID is not the world's own (a data file copied from another world) is not trusted (Encounters.store).

  Encounters with no entry are NOT_SPAWNED. Only definitions whose state changed get an entry.
 */
public final class EncounterData extends SavedData {
    public static final int DATA_VERSION = 1;

    private static final Codec<EncounterData> STORED = RecordCodecBuilder.<EncounterData>create(i -> i.group(
                    Codec.INT.fieldOf("data_version").forGetter(d -> DATA_VERSION),
                    UUIDUtil.STRING_CODEC.optionalFieldOf("world_id").forGetter(d -> Optional.ofNullable(d.worldId)),
                    Codec.unboundedMap(Identifier.CODEC, EncounterRecord.CODEC).optionalFieldOf("encounters", Map.of())
                            .forGetter(d -> new TreeMap<>(d.records)))
            .apply(i, (version, world, records) -> new EncounterData(version, world.orElse(null), records)));

    public static final Codec<EncounterData> CODEC = STORED.validate(d -> d.storedVersion <= DATA_VERSION
            ? DataResult.success(d)
            : DataResult.error(() -> "encounter data version " + d.storedVersion + " is newer than this mod's " + DATA_VERSION));

    /* Stopwatch data has no data fixers, as for the WorldPlan's file (see WorldPlanData). */
    public static final SavedDataType<EncounterData> TYPE = new SavedDataType<>(InhabitedWorld.id("encounters"), EncounterData::new, CODEC,
            DataFixTypes.SAVED_DATA_STOPWATCHES);

    private final int storedVersion;
    private @Nullable UUID worldId;
    private final Map<Identifier, EncounterRecord> records;

    public EncounterData() {
        this(DATA_VERSION, null, Map.of());
    }

    private EncounterData(int storedVersion, @Nullable UUID worldId, Map<Identifier, EncounterRecord> records) {
        this.storedVersion = storedVersion;
        this.worldId = worldId;
        this.records = new HashMap<>(records);
    }

    public @Nullable UUID worldId() {
        return this.worldId;
    }

    /**
     * Makes the store belong to a world: claims an unclaimed store, keeps its own, and empties one that belongs to
     * another world. Returns whether the store had to be emptied.
     */
    public boolean claim(UUID world) {
        if (world.equals(this.worldId)) {
            return false;
        }
        boolean foreign = this.worldId != null;
        if (foreign) {
            this.records.clear();
        }
        this.worldId = world;
        this.setDirty();
        return foreign;
    }

    public EncounterRecord get(Identifier encounter) {
        return this.records.getOrDefault(encounter, EncounterRecord.NEW);
    }

    public void put(Identifier encounter, EncounterRecord record) {
        EncounterRecord old = this.records.put(encounter, record);
        if (!record.equals(old)) {
            this.setDirty();
        }
    }

    public Map<Identifier, EncounterRecord> records() {
        return Map.copyOf(this.records);
    }
}
