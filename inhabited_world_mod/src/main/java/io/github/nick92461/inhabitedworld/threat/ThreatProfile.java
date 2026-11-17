package io.github.nick92461.inhabitedworld.threat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.nick92461.inhabitedworld.spawn.SpawnProfile;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.MobSpawnSettings;

import org.jspecify.annotations.Nullable;

/*
  A semantic region's threat profile (canon 06 section 17.3, "the semantic POI owns its threat meaning"; 09 DoD 16):
  the minimal reusable rule set for the hostiles that appear in a region by themselves (ambient generation). Spawn
  origins other than ambient keep the P2B rules (SpawnPolicy); encounters are separate world state (encounter/).

  POI profiles are data, data/<namespace>/threat_profile/<path>.json, chosen through the POI's catalog entry
  (PoiThreatBinding). The two engine profiles behind the saved SpawnProfile IDs are code: the wilderness (vanilla) and
  the protected settlement (no ambient hostiles).

    ambient_hostiles  "vanilla": the biome's own hostiles and vanilla's light rules (the wilderness)
                      "suppressed": none (a protected settlement, a shrine)
                      "listed": only the hostiles listed here, by their weights, under the conditions below
    hostiles          the listed hostiles ("listed" only): type, weight, group size
    blocked           hostile types refused here whatever the mode (optional)
    local_cap         the most ambient hostiles that may stand in the region at once; ambient spawning stops there
                      (optional; vanilla's own caps still apply too)
    conditions        where listed hostiles may spawn (SpawnConditions; "listed" only)
    hotspots          controlled hostile hotspots in the region (HotspotDefinition; "listed" only, they spawn the
                      listed hostiles)

  The compiled form keeps plain arrays for the spawn path: a type check is a scan of a few references, with no
  allocation (06 section 17.6).
 */
public final class ThreatProfile {
    public enum AmbientHostiles implements StringRepresentable {
        VANILLA,
        SUPPRESSED,
        LISTED;

        public static final Codec<AmbientHostiles> CODEC = StringRepresentable.fromEnum(AmbientHostiles::values);

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** The file format. */
    public record Definition(AmbientHostiles ambientHostiles, List<HostileEntry> hostiles, List<EntityType<?>> blocked, Optional<Integer> localCap,
                             SpawnConditions conditions, List<HotspotDefinition> hotspots) {
        /** The file format's codec (built on first use: it needs Minecraft's registries, the engine profiles don't). */
        public static Codec<Definition> codec() {
            return Format.CODEC;
        }

        private static DataResult<Definition> check(Definition d) {
            boolean listed = d.ambientHostiles() == AmbientHostiles.LISTED;
            if (listed && d.hostiles().isEmpty()) {
                return DataResult.error(() -> "ambient_hostiles 'listed' needs at least one hostile (use 'suppressed' for none)");
            }
            if (!listed && !d.hostiles().isEmpty()) {
                return DataResult.error(() -> "hostiles are only read with ambient_hostiles 'listed'");
            }
            if (!listed && !d.hotspots().isEmpty()) {
                return DataResult.error(() -> "hotspots spawn the listed hostiles, so they need ambient_hostiles 'listed'");
            }
            Set<EntityType<?>> seen = new HashSet<>();
            for (HostileEntry entry : d.hostiles()) {
                if (!seen.add(entry.type())) {
                    return DataResult.error(() -> EntityType.getKey(entry.type()) + " is listed twice");
                }
                if (d.blocked().contains(entry.type())) {
                    return DataResult.error(() -> EntityType.getKey(entry.type()) + " is both listed and blocked");
                }
            }
            Set<String> ids = new HashSet<>();
            for (HotspotDefinition hotspot : d.hotspots()) {
                if (!ids.add(hotspot.id())) {
                    return DataResult.error(() -> "hotspot id " + hotspot.id() + " is used twice");
                }
            }
            return DataResult.success(d);
        }
    }

    private static final class Format {
        static final Codec<Definition> CODEC = RecordCodecBuilder.<Definition>create(i -> i.group(
                        AmbientHostiles.CODEC.fieldOf("ambient_hostiles").forGetter(Definition::ambientHostiles),
                        HostileEntry.CODEC.listOf().optionalFieldOf("hostiles", List.of()).forGetter(Definition::hostiles),
                        BuiltInRegistries.ENTITY_TYPE.byNameCodec().listOf().optionalFieldOf("blocked", List.of()).forGetter(Definition::blocked),
                        Codec.intRange(0, 256).optionalFieldOf("local_cap").forGetter(Definition::localCap),
                        SpawnConditions.CODEC.optionalFieldOf("conditions", SpawnConditions.VANILLA_LIKE).forGetter(Definition::conditions),
                        HotspotDefinition.CODEC.listOf().optionalFieldOf("hotspots", List.of()).forGetter(Definition::hotspots))
                .apply(i, Definition::new))
                .validate(Definition::check);
    }

    /** The wilderness (06 section 17.2): vanilla-style hostile ecology. */
    public static final ThreatProfile WILDERNESS = new ThreatProfile(SpawnProfile.WILDERNESS.id(),
            new Definition(AmbientHostiles.VANILLA, List.of(), List.of(), Optional.empty(), SpawnConditions.VANILLA_LIKE, List.of()));
    /** A protected settlement (06 section 17.1): ordinary hostile generation never happens inside. */
    public static final ThreatProfile PROTECTED_SETTLEMENT = new ThreatProfile(SpawnProfile.PROTECTED_SETTLEMENT.id(),
            new Definition(AmbientHostiles.SUPPRESSED, List.of(), List.of(), Optional.empty(), SpawnConditions.VANILLA_LIKE, List.of()));
    /** A POI whose catalog entry names no threat profile: vanilla-like, as before Phase 3 (logged when it happens). */
    public static final ThreatProfile UNBOUND_POI = new ThreatProfile(SpawnProfile.POI_PLACEHOLDER.id(),
            new Definition(AmbientHostiles.VANILLA, List.of(), List.of(), Optional.empty(), SpawnConditions.VANILLA_LIKE, List.of()));

    public static final int NO_CAP = Integer.MAX_VALUE;

    private final Identifier id;
    private final Definition definition;
    private final EntityType<?>[] listed;
    private final EntityType<?>[] blocked;
    private final WeightedList<MobSpawnSettings.SpawnerData> spawnList;
    private final WeightedList<EntityType<?>> hostileWeights;
    private final int localCap;
    // Copied out of the definition so the spawn path reads plain fields.
    private final AmbientHostiles ambient;

    public ThreatProfile(Identifier id, Definition definition) {
        this.id = id;
        this.definition = definition;
        this.ambient = definition.ambientHostiles();
        this.listed = definition.hostiles().stream().map(HostileEntry::type).toArray(EntityType<?>[]::new);
        this.blocked = definition.blocked().toArray(EntityType<?>[]::new);
        WeightedList.Builder<MobSpawnSettings.SpawnerData> spawns = WeightedList.builder();
        WeightedList.Builder<EntityType<?>> weights = WeightedList.builder();
        for (HostileEntry entry : definition.hostiles()) {
            spawns.add(entry.spawnerData(), entry.weight());
            weights.add(entry.type(), entry.weight());
        }
        this.spawnList = spawns.build();
        this.hostileWeights = weights.build();
        this.localCap = definition.localCap().orElse(NO_CAP);
    }

    /** The engine profile behind a saved spawn-profile ID (a POI's real profile comes from its catalog entry). */
    public static ThreatProfile builtIn(SpawnProfile profile) {
        return switch (profile) {
            case WILDERNESS -> WILDERNESS;
            case PROTECTED_SETTLEMENT -> PROTECTED_SETTLEMENT;
            case POI_PLACEHOLDER -> UNBOUND_POI;
        };
    }

    public Identifier id() {
        return this.id;
    }

    public Definition definition() {
        return this.definition;
    }

    public AmbientHostiles ambientHostiles() {
        return this.ambient;
    }

    /** Whether the profile names this type as one of its own hostiles. Allocation-free. */
    public boolean lists(EntityType<?> type) {
        for (EntityType<?> t : this.listed) {
            if (t == type) {
                return true;
            }
        }
        return false;
    }

    /** Whether the profile refuses this type as an ambient hostile whatever its mode. Allocation-free. */
    public boolean blocks(EntityType<?> type) {
        EntityType<?>[] blocked = this.blocked;
        if (blocked.length == 0) {
            return false;
        }
        for (EntityType<?> t : blocked) {
            if (t == type) {
                return true;
            }
        }
        return false;
    }

    /** The cap on ambient hostiles standing in the region, or NO_CAP. */
    public int localCap() {
        return this.localCap;
    }

    public boolean hasLocalCap() {
        return this.localCap != NO_CAP;
    }

    public SpawnConditions conditions() {
        return this.definition.conditions();
    }

    public List<HotspotDefinition> hotspots() {
        return this.definition.hotspots();
    }

    /** The monster spawn list natural spawning uses inside the region instead of the biome's ("listed" only), else null. */
    public @Nullable WeightedList<MobSpawnSettings.SpawnerData> naturalMonsters() {
        return this.ambientHostiles() == AmbientHostiles.LISTED ? this.spawnList : null;
    }

    /** The listed hostiles by weight (hotspots draw from it). */
    public WeightedList<EntityType<?>> hostileWeights() {
        return this.hostileWeights;
    }

    public String describe() {
        List<String> parts = new ArrayList<>();
        parts.add("ambient hostiles " + this.ambientHostiles().getSerializedName());
        if (!this.definition.hostiles().isEmpty()) {
            parts.add("hostiles " + String.join(", ", this.definition.hostiles().stream().map(HostileEntry::describe).toList()));
        }
        if (!this.definition.blocked().isEmpty()) {
            parts.add("blocked " + String.join(", ", this.definition.blocked().stream().map(t -> EntityType.getKey(t).toString()).toList()));
        }
        if (this.hasLocalCap()) {
            parts.add("local cap " + this.localCap);
        }
        if (this.ambientHostiles() == AmbientHostiles.LISTED) {
            parts.add("conditions " + this.conditions().describe());
        }
        if (!this.hotspots().isEmpty()) {
            parts.add(this.hotspots().size() + " hotspot(s)");
        }
        return this.id + " (" + String.join("; ", parts) + ")";
    }

    @Override
    public String toString() {
        return "ThreatProfile[" + this.id + "]";
    }
}
