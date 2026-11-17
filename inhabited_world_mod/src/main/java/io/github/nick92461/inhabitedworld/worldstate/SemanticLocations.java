package io.github.nick92461.inhabitedworld.worldstate;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.server.MinecraftServer;

/*
  The semantic location registry (canon 09 section 54, Phase 3; 02 section 16): every named place inside the world's
  placed structures, by id. Later phases (NPC schedules, quests) look places up here instead of using coordinates.

  It is a read-only view of the WorldPlan's placed structures, in plan order, so it holds whatever the plan holds:
  any number of placed structures, each with its own markers. Built from the plan in memory (no disk access); the
  plan only changes while its world is being created, so a registry taken after the world opened stays current.
 */
public final class SemanticLocations {
    private static final SemanticLocations EMPTY = new SemanticLocations(Map.of());

    private final Map<String, SemanticLocation> byId;

    private SemanticLocations(Map<String, SemanticLocation> byId) {
        this.byId = byId;
    }

    public static SemanticLocations of(WorldPlan plan) {
        Map<String, SemanticLocation> byId = new LinkedHashMap<>();
        for (PlacedStructure structure : plan.structures()) {
            for (SemanticLocation location : structure.locations()) {
                byId.put(location.id(), location);
            }
        }
        return new SemanticLocations(Collections.unmodifiableMap(byId));
    }

    /** The running world's registry; empty for a world without a plan or placed structures. */
    public static SemanticLocations of(MinecraftServer server) {
        WorldPlan plan = WorldPlans.get(server);
        return plan == null ? EMPTY : of(plan);
    }

    public Optional<SemanticLocation> get(String id) {
        return Optional.ofNullable(this.byId.get(id));
    }

    /** Every location, in plan order. */
    public Collection<SemanticLocation> all() {
        return this.byId.values();
    }

    /** The locations inside one site's structure. */
    public List<SemanticLocation> ofSite(String site) {
        return this.byId.values().stream().filter(l -> l.site().equals(site)).toList();
    }

    public int size() {
        return this.byId.size();
    }
}
