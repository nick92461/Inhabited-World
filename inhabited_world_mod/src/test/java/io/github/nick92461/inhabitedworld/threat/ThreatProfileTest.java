package io.github.nick92461.inhabitedworld.threat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.encounter.EncounterDefinition;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.MobSpawnSettings;

/*
  The threat-profile format and the shipped placeholder content (canon 06 sections 17.3, 17.7, 17.8; 09 DoD 16-18):
  the shipped files decode, the mine profile differs from the wilderness as the brief requires, malformed profiles are
  refused with a reason, and the light conditions behave as documented.
 */
public class ThreatProfileTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static JsonElement resource(String path) {
        try (InputStream in = ThreatProfileTest.class.getResourceAsStream("/data/inhabited_world/" + path)) {
            assertNotNull(in, "shipped data file " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    static <T> T decode(Codec<T> codec, JsonElement json) {
        return codec.parse(JsonOps.INSTANCE, json).getOrThrow(error -> new AssertionError("does not decode: " + error));
    }

    /** The shipped abandoned-mine profile (other tests use it; bootstraps Minecraft first). */
    public static ThreatProfile shippedMine() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        return new ThreatProfile(InhabitedWorld.id("placeholder_abandoned_mine"),
                decode(ThreatProfile.Definition.codec(), resource("threat_profile/placeholder_abandoned_mine.json")));
    }

    public static ThreatProfile parse(String json) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        return new ThreatProfile(InhabitedWorld.id("test"), decode(ThreatProfile.Definition.codec(), JsonParser.parseString(json)));
    }

    private static boolean refused(String json) {
        return ThreatProfile.Definition.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isError();
    }

    @Test
    void theShippedMineProfileIsTheBriefsAbandonedMine() {
        ThreatProfile mine = shippedMine();
        assertEquals(ThreatProfile.AmbientHostiles.LISTED, mine.ambientHostiles());
        Set<EntityType<?>> listed = mine.definition().hostiles().stream().map(HostileEntry::type).collect(Collectors.toSet());
        assertEquals(Set.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.CAVE_SPIDER), listed, "only zombies, skeletons and cave spiders");
        for (EntityType<?> type : List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.CAVE_SPIDER)) {
            assertTrue(mine.lists(type));
        }
        assertFalse(mine.lists(EntityTypes.CREEPER));
        assertTrue(mine.hasLocalCap());
        assertEquals(12, mine.localCap());
        assertTrue(mine.conditions().maxBlockLight() > SpawnConditions.VANILLA_LIKE.maxBlockLight(),
                "the mine lets its hostiles spawn in lamp-lit tunnels, where the wilderness doesn't");
        // Natural spawning's monster list in the region is exactly the profile's.
        Set<EntityType<?>> naturalTypes = mine.naturalMonsters().unwrap().stream().map(w -> w.value().type()).collect(Collectors.toSet());
        assertEquals(listed, naturalTypes);
        int total = mine.naturalMonsters().unwrap().stream().mapToInt(w -> w.weight()).sum();
        assertEquals(100, total, "weights 50 / 30 / 20");
        assertEquals(1, mine.hotspots().size());
        HotspotDefinition hotspot = mine.hotspots().getFirst();
        assertTrue(hotspot.target() <= hotspot.cap());
        assertTrue(hotspot.activationRadius() > hotspot.minPlayerDistance());
    }

    @Test
    void engineProfilesKeepThePhase2bRules() {
        assertEquals(ThreatProfile.AmbientHostiles.VANILLA, ThreatProfile.WILDERNESS.ambientHostiles());
        assertEquals(ThreatProfile.AmbientHostiles.SUPPRESSED, ThreatProfile.PROTECTED_SETTLEMENT.ambientHostiles());
        assertEquals(ThreatProfile.AmbientHostiles.VANILLA, ThreatProfile.UNBOUND_POI.ambientHostiles());
        for (ThreatProfile p : List.of(ThreatProfile.WILDERNESS, ThreatProfile.PROTECTED_SETTLEMENT, ThreatProfile.UNBOUND_POI)) {
            assertNull(p.naturalMonsters(), p + " keeps the biome's spawn list");
            assertFalse(p.hasLocalCap());
            assertTrue(p.hotspots().isEmpty());
        }
    }

    @Test
    void theShippedPoiBindingAndEncounterDecode() {
        PoiThreatBinding binding = decode(PoiThreatBinding.CODEC, resource("poi_threats/placeholder_external_poi.json"));
        assertEquals(InhabitedWorld.id("placeholder_abandoned_mine"), binding.threatProfile());
        assertEquals(List.of(InhabitedWorld.id("placeholder_mine_foreman")), binding.encounters());

        EncounterDefinition foreman = decode(EncounterDefinition.CODEC, resource("encounter/placeholder_mine_foreman.json"));
        assertEquals(EntityTypes.ZOMBIE, foreman.entity());
        assertTrue(foreman.name().orElseThrow().contains("placeholder"), "placeholder content is marked as such");
        assertEquals(Items.IRON_PICKAXE, foreman.equipment().get(EquipmentSlot.MAINHAND));
        assertEquals(Items.IRON_HELMET, foreman.equipment().get(EquipmentSlot.HEAD));
        assertFalse(foreman.respawns(), "a required quest enemy stays defeated");
        assertEquals(32, foreman.triggerRadius());
        assertEquals(SiteAnchor.Vertical.SURFACE, foreman.anchor().vertical());
    }

    @Test
    void malformedProfilesAreRefused() {
        assertFalse(refused("{\"ambient_hostiles\": \"suppressed\"}"));
        assertTrue(refused("{\"ambient_hostiles\": \"listed\"}"), "listed without hostiles");
        assertTrue(refused("{\"ambient_hostiles\": \"vanilla\", \"hostiles\": [{\"type\": \"minecraft:zombie\", \"weight\": 1}]}"), "hostiles outside listed");
        assertTrue(refused("{\"ambient_hostiles\": \"listed\", \"hostiles\": [{\"type\": \"minecraft:cow\", \"weight\": 1}]}"), "not a hostile");
        assertTrue(refused("{\"ambient_hostiles\": \"listed\", \"hostiles\": [{\"type\": \"minecraft:zombie\", \"weight\": 0}]}"), "zero weight");
        assertTrue(refused("{\"ambient_hostiles\": \"listed\", \"hostiles\": [{\"type\": \"minecraft:zombie\", \"weight\": 1, \"min_group\": 3, \"max_group\": 2}]}"));
        assertTrue(refused("{\"ambient_hostiles\": \"listed\", \"hostiles\": [{\"type\": \"minecraft:zombie\", \"weight\": 1}, {\"type\": \"minecraft:zombie\", \"weight\": 2}]}"),
                "listed twice");
        assertTrue(refused("{\"ambient_hostiles\": \"listed\", \"hostiles\": [{\"type\": \"minecraft:zombie\", \"weight\": 1}], \"blocked\": [\"minecraft:zombie\"]}"),
                "listed and blocked");
        assertTrue(refused("{\"ambient_hostiles\": \"vanilla\", \"local_cap\": -1}"));
        assertTrue(refused("{\"ambient_hostiles\": \"listed\", \"hostiles\": [{\"type\": \"minecraft:zombie\", \"weight\": 1}], \"conditions\": {\"max_block_light\": 16}}"));
        assertTrue(refused("{\"ambient_hostiles\": \"sometimes\"}"));
        String hotspot = "{\"ambient_hostiles\": \"listed\", \"hostiles\": [{\"type\": \"minecraft:zombie\", \"weight\": 1}], \"hotspots\": [%s]}";
        assertFalse(refused(hotspot.formatted("{\"id\": \"a\", \"radius\": 8, \"target\": 2, \"cap\": 3}")));
        assertTrue(refused(hotspot.formatted("{\"id\": \"a\", \"radius\": 8, \"target\": 4, \"cap\": 3}")), "cap below target");
        assertTrue(refused(hotspot.formatted("{\"id\": \"A b\", \"radius\": 8, \"target\": 2, \"cap\": 3}")), "bad hotspot id");
        assertTrue(refused(hotspot.formatted("{\"id\": \"a\", \"radius\": 8, \"target\": 2, \"cap\": 3}, {\"id\": \"a\", \"radius\": 8, \"target\": 2, \"cap\": 3}")),
                "duplicate hotspot id");
        assertTrue(refused("{\"ambient_hostiles\": \"vanilla\", \"hotspots\": [{\"id\": \"a\", \"radius\": 8, \"target\": 2, \"cap\": 3}]}"), "hotspot without a list");
    }

    @Test
    void spawnConditionsAreDeterministicLightLimits() {
        SpawnConditions vanilla = SpawnConditions.VANILLA_LIKE;
        SpawnConditions mine = shippedMine().conditions();
        // A torch-lit tunnel at night: block light 5, brightness 5.
        assertFalse(vanilla.allows(20, 5, 5), "the wilderness rule refuses any block light");
        assertTrue(mine.allows(20, 5, 5), "the mine allows its lamps");
        // Full dark: both allow.
        assertTrue(vanilla.allows(20, 0, 0));
        assertTrue(mine.allows(20, 0, 0));
        // Daylight on the surface: neither.
        assertFalse(mine.allows(80, 0, 15));
        assertFalse(mine.allows(20, 8, 8), "brighter than the mine's lamps");
        SpawnConditions deep = new SpawnConditions(7, 7, -64, 0);
        assertTrue(deep.allows(-10, 0, 0));
        assertFalse(deep.allows(10, 0, 0), "above its height band");
    }

    @Test
    void spawnerDataUsesTheGroupSizes() {
        HostileEntry entry = new HostileEntry(EntityTypes.CAVE_SPIDER, 20, 1, 3);
        MobSpawnSettings.SpawnerData data = entry.spawnerData();
        assertEquals(EntityTypes.CAVE_SPIDER, data.type());
        assertEquals(1, data.count().minInclusive());
        assertEquals(3, data.count().maxInclusive());
    }
}
