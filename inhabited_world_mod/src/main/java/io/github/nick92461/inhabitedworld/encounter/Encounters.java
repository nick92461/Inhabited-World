package io.github.nick92461.inhabitedworld.encounter;

import java.util.List;
import java.util.Optional;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.threat.LevelThreats;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlan;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlans;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;

import org.jspecify.annotations.Nullable;

/*
  The query API for guaranteed encounters, by semantic encounter ID (canon 06 section 17.8). Phase 4's quest system
  asks it whether its required enemy is still to come, out there, or beaten, and for the mob itself while it is
  loaded; it never needs coordinates or entity IDs (06 section 17.12 item 8).

  Server-side and authoritative (10 section 60, multiplayer discipline); call it on the server thread.
 */
public final class Encounters {
    private Encounters() {
    }

    /** The state of an encounter of this world, or empty if no POI of the world holds an encounter with that ID. */
    public static Optional<EncounterRecord.State> state(MinecraftServer server, Identifier encounter) {
        return record(server, encounter).map(EncounterRecord::state);
    }

    /** The full persistent record (state, mob, counters), or empty as for state. */
    public static Optional<EncounterRecord> record(MinecraftServer server, Identifier encounter) {
        LevelThreats threats = LevelThreats.of(server.overworld());
        EncounterData data = store(server);
        if (threats == null || data == null || threats.encounters().get(encounter) == null) {
            return Optional.empty();
        }
        return Optional.of(data.get(encounter));
    }

    /** The encounter's mob while it is active and loaded. */
    public static Optional<Mob> entity(MinecraftServer server, Identifier encounter) {
        LevelThreats threats = LevelThreats.of(server.overworld());
        EncounterData data = store(server);
        if (threats == null || data == null) {
            return Optional.empty();
        }
        return threats.encounters().entity(server.overworld(), data, encounter);
    }

    /** The encounters this world's POIs hold. */
    public static List<Identifier> ids(MinecraftServer server) {
        LevelThreats threats = LevelThreats.of(server.overworld());
        return threats == null ? List.of() : threats.encounters().encounters().stream().map(EncounterRuntime.Bound::id).toList();
    }

    /**
     * The world's encounter store, scoped to its persisted world ID; null for a world without a WorldPlan (a Default
     * world: no POIs, no encounters). A store that belongs to another world ID is emptied (and the log says so).
     */
    public static @Nullable EncounterData store(MinecraftServer server) {
        WorldPlan plan = WorldPlans.get(server);
        if (plan == null) {
            return null;
        }
        EncounterData data = server.getDataStorage().computeIfAbsent(EncounterData.TYPE);
        java.util.UUID previous = data.worldId();
        if (data.claim(plan.worldId())) {
            InhabitedWorld.LOGGER.warn("[{}] the encounter store belonged to world {}, not to this world {}; its states were discarded",
                    InhabitedWorld.MOD_ID, previous, plan.worldId());
        }
        return data;
    }
}
