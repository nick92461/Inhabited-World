package io.github.nick92461.inhabitedworld.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;

import io.github.nick92461.inhabitedworld.spawn.SpawnPolicy.Decision;
import io.github.nick92461.inhabitedworld.spawn.SpawnPolicy.Origin;
import io.github.nick92461.inhabitedworld.threat.ThreatProfile;
import io.github.nick92461.inhabitedworld.threat.ThreatProfileTest;

/*
  The spawn decision table (canon 06 sections 13, 17.1 to 17.3, 17.5): entity type and facts x spawn reason x the
  region's threat profile (and its population against a local cap), in Inhabited Worlds and in Default worlds.
 */
class SpawnPolicyTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // The world making mobs from nothing: what a protected settlement suppresses for hostiles (06 section 17.1).
    private static final Set<EntitySpawnReason> AMBIENT = EnumSet.of(
            EntitySpawnReason.NATURAL, EntitySpawnReason.CHUNK_GENERATION, EntitySpawnReason.PATROL,
            EntitySpawnReason.REINFORCEMENT, EntitySpawnReason.EVENT);

    // The game adding a mob because something placed there makes it.
    private static final Set<EntitySpawnReason> SOURCED = EnumSet.of(
            EntitySpawnReason.STRUCTURE, EntitySpawnReason.SPAWNER, EntitySpawnReason.TRIAL_SPAWNER,
            EntitySpawnReason.MOB_SUMMONED, EntitySpawnReason.JOCKEY, EntitySpawnReason.TRIGGERED);

    // Development and debugging must stay possible (06 section 17.5).
    private static final Set<EntitySpawnReason> DELIBERATE = EnumSet.of(
            EntitySpawnReason.COMMAND, EntitySpawnReason.SPAWN_ITEM_USE, EntitySpawnReason.DISPENSER, EntitySpawnReason.BUCKET);

    private static final Set<EntitySpawnReason> DERIVED = EnumSet.of(
            EntitySpawnReason.BREEDING, EntitySpawnReason.CONVERSION, EntitySpawnReason.LOAD, EntitySpawnReason.DIMENSION_TRAVEL);

    @Test
    void everyReasonHasExactlyTheExpectedOrigin() {
        for (EntitySpawnReason reason : EntitySpawnReason.values()) {
            Origin origin = SpawnPolicy.origin(reason);
            assertEquals(AMBIENT.contains(reason), origin == Origin.AMBIENT, reason + " is " + origin);
            assertEquals(SOURCED.contains(reason), origin == Origin.SOURCED, reason + " is " + origin);
            assertEquals(DELIBERATE.contains(reason), origin == Origin.DELIBERATE, reason + " is " + origin);
            assertEquals(DERIVED.contains(reason), origin == Origin.DERIVED, reason + " is " + origin);
            assertEquals(AMBIENT.contains(reason) || SOURCED.contains(reason), origin.automatic(), reason + " automatic");
        }
    }

    // The engine profiles and the shipped abandoned-mine profile: every region kind a decision can meet.
    private static List<ThreatProfile> profiles() {
        return List.of(ThreatProfile.WILDERNESS, ThreatProfile.PROTECTED_SETTLEMENT, ThreatProfile.UNBOUND_POI, ThreatProfileTest.shippedMine());
    }

    private static Decision decide(boolean rpg, boolean humanLike, boolean hostile, EntityType<?> type, EntitySpawnReason reason, ThreatProfile profile) {
        return SpawnPolicy.decide(rpg, humanLike, hostile, type, reason, profile, 0);
    }

    // The global ban (06 section 13): every region, every automatic reason; deliberate and derived spawns pass.
    @Test
    void prohibitedHumanLikeMobsAreRefusedInEveryRegionForAutomaticReasons() {
        for (ThreatProfile profile : profiles()) {
            for (boolean hostile : new boolean[]{false, true}) {
                for (EntitySpawnReason reason : EntitySpawnReason.values()) {
                    Decision decision = decide(true, true, hostile, EntityTypes.WITCH, reason, profile);
                    boolean automatic = AMBIENT.contains(reason) || SOURCED.contains(reason);
                    assertEquals(automatic ? Decision.REFUSE_PROHIBITED_HUMAN_LIKE : Decision.ALLOW, decision, profile + " " + reason + " hostile " + hostile);
                }
            }
        }
    }

    // A protected settlement refuses exactly the ambient hostile spawns (06 section 17.1).
    @Test
    void protectedSettlementRefusesOnlyAmbientHostiles() {
        for (EntitySpawnReason reason : EntitySpawnReason.values()) {
            Decision hostile = decide(true, false, true, EntityTypes.ZOMBIE, reason, ThreatProfile.PROTECTED_SETTLEMENT);
            assertEquals(AMBIENT.contains(reason) ? Decision.REFUSE_AMBIENT_HOSTILE : Decision.ALLOW, hostile, "hostile, " + reason);
            assertEquals(Decision.ALLOW, decide(true, false, false, EntityTypes.COW, reason, ThreatProfile.PROTECTED_SETTLEMENT),
                    "animals keep spawning in town, " + reason);
        }
    }

    // Commands, spawn eggs, dispensers, buckets, and our encounters pass in town (06 section 17.5).
    @Test
    void deliberateHostileSpawnsPassInTown() {
        for (EntitySpawnReason reason : DELIBERATE) {
            assertEquals(Decision.ALLOW, decide(true, false, true, EntityTypes.ZOMBIE, reason, ThreatProfile.PROTECTED_SETTLEMENT), reason.name());
        }
    }

    // The wilderness keeps vanilla-style hostile ecology (06 section 17.2); so does a POI whose catalog names no profile.
    @Test
    void wildernessAndUnboundPoiAllowEveryOrdinaryMob() {
        for (ThreatProfile profile : new ThreatProfile[]{ThreatProfile.WILDERNESS, ThreatProfile.UNBOUND_POI}) {
            for (EntitySpawnReason reason : EntitySpawnReason.values()) {
                for (EntityType<?> type : List.of(EntityTypes.ZOMBIE, EntityTypes.CREEPER, EntityTypes.SPIDER, EntityTypes.ENDERMAN)) {
                    assertEquals(Decision.ALLOW, decide(true, false, true, type, reason, profile), profile + " " + type + " " + reason);
                }
                assertEquals(Decision.ALLOW, decide(true, false, false, EntityTypes.COW, reason, profile), profile + " passive " + reason);
            }
        }
    }

    /*
      The abandoned mine (06 section 17.3, 09 DoD 16): ambient hostiles are only its own three, and natural spawning of
      those runs under the profile's conditions; other reasons keep the P2B rules; animals are untouched.
     */
    @Test
    void theMineAllowsOnlyItsListedHostilesAmbiently() {
        ThreatProfile mine = ThreatProfileTest.shippedMine();
        for (EntityType<?> type : List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.CAVE_SPIDER)) {
            assertEquals(Decision.ALLOW_BY_PROFILE, decide(true, false, true, type, EntitySpawnReason.NATURAL, mine), "natural " + type);
            for (EntitySpawnReason reason : AMBIENT) {
                assertTrue(decide(true, false, true, type, reason, mine).allowed(), type + " " + reason);
            }
        }
        for (EntityType<?> type : List.of(EntityTypes.CREEPER, EntityTypes.SPIDER, EntityTypes.ENDERMAN, EntityTypes.PHANTOM)) {
            for (EntitySpawnReason reason : AMBIENT) {
                assertEquals(Decision.REFUSE_UNLISTED_HOSTILE, decide(true, false, true, type, reason, mine), type + " " + reason);
            }
            for (EntitySpawnReason reason : List.of(EntitySpawnReason.COMMAND, EntitySpawnReason.SPAWNER, EntitySpawnReason.SPAWN_ITEM_USE, EntitySpawnReason.LOAD)) {
                assertEquals(Decision.ALLOW, decide(true, false, true, type, reason, mine), "non-ambient " + type + " " + reason);
            }
        }
        assertEquals(Decision.ALLOW, decide(true, false, false, EntityTypes.BAT, EntitySpawnReason.NATURAL, mine), "bats keep the biome's rules");
        assertEquals(Decision.ALLOW, decide(false, false, true, EntityTypes.CREEPER, EntitySpawnReason.NATURAL, mine), "a Default world is vanilla");
    }

    // The local cap counts what stands in the region now; at the cap, ambient hostiles stop, other reasons don't.
    @Test
    void theLocalCapStopsAmbientHostilesOnly() {
        ThreatProfile mine = ThreatProfileTest.shippedMine();
        int cap = mine.localCap();
        assertEquals(12, cap);
        assertEquals(Decision.ALLOW_BY_PROFILE, SpawnPolicy.decide(true, false, true, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, mine, cap - 1));
        assertEquals(Decision.REFUSE_LOCAL_CAP, SpawnPolicy.decide(true, false, true, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, mine, cap));
        assertEquals(Decision.REFUSE_LOCAL_CAP, SpawnPolicy.decide(true, false, true, EntityTypes.ZOMBIE, EntitySpawnReason.REINFORCEMENT, mine, cap + 5));
        assertEquals(Decision.ALLOW, SpawnPolicy.decide(true, false, true, EntityTypes.ZOMBIE, EntitySpawnReason.COMMAND, mine, cap + 5));
        assertEquals(Decision.ALLOW, SpawnPolicy.decide(true, false, true, EntityTypes.ZOMBIE, EntitySpawnReason.SPAWNER, mine, cap + 5));
        assertEquals(Decision.ALLOW, SpawnPolicy.decide(true, false, true, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, ThreatProfile.WILDERNESS, 1000),
                "the wilderness has no local cap");
    }

    @Test
    void blockedTypesAreRefusedInAnyMode() {
        ThreatProfile noCreepers = ThreatProfileTest.parse("{\"ambient_hostiles\": \"vanilla\", \"blocked\": [\"minecraft:creeper\"]}");
        assertEquals(Decision.REFUSE_BLOCKED_HOSTILE, decide(true, false, true, EntityTypes.CREEPER, EntitySpawnReason.NATURAL, noCreepers));
        assertEquals(Decision.ALLOW, decide(true, false, true, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, noCreepers));
        assertEquals(Decision.ALLOW, decide(true, false, true, EntityTypes.CREEPER, EntitySpawnReason.COMMAND, noCreepers));
    }

    // A Default world (isRpg false) keeps vanilla behaviour, villagers and witches included, whatever the profile says.
    @Test
    void defaultWorldAllowsEverything() {
        for (ThreatProfile profile : profiles()) {
            for (EntitySpawnReason reason : EntitySpawnReason.values()) {
                for (int facts = 0; facts < 4; facts++) {
                    assertEquals(Decision.ALLOW, SpawnPolicy.decide(false, (facts & 1) != 0, (facts & 2) != 0, EntityTypes.WITCH, reason, profile, 1000),
                            profile + " " + reason);
                }
            }
        }
    }

    @Test
    void skeletonTrapsAreAmbientHostileGeneration() {
        assertEquals(Decision.REFUSE_AMBIENT_HOSTILE, SpawnPolicy.decideSkeletonTrap(true, ThreatProfile.PROTECTED_SETTLEMENT, 0));
        assertEquals(Decision.ALLOW, SpawnPolicy.decideSkeletonTrap(true, ThreatProfile.WILDERNESS, 0));
        assertEquals(Decision.ALLOW, SpawnPolicy.decideSkeletonTrap(true, ThreatProfileTest.shippedMine(), 0), "skeletons are the mine's own");
        assertEquals(Decision.ALLOW, SpawnPolicy.decideSkeletonTrap(false, ThreatProfile.PROTECTED_SETTLEMENT, 0));
    }

    // Creaking hearts (user decision 2026-10-29): generated ones stay dormant in an Inhabited World, placed ones work.
    @Test
    void inhabitedWorldRefusesOnlyNaturalCreakingHearts() {
        assertEquals(Decision.REFUSE_NATURAL_CREAKING_HEART, SpawnPolicy.decideCreakingHeart(true, true));
        assertEquals(Decision.ALLOW, SpawnPolicy.decideCreakingHeart(true, false));
    }

    @Test
    void defaultWorldCreakingHeartsAreVanilla() {
        assertEquals(Decision.ALLOW, SpawnPolicy.decideCreakingHeart(false, true));
        assertEquals(Decision.ALLOW, SpawnPolicy.decideCreakingHeart(false, false));
    }

    @Test
    void refusalsExplainThemselves() {
        assertFalse(Decision.REFUSE_PROHIBITED_HUMAN_LIKE.allowed());
        assertTrue(Decision.REFUSE_PROHIBITED_HUMAN_LIKE.explanation().contains("06 section 13"));
        assertFalse(Decision.REFUSE_AMBIENT_HOSTILE.allowed());
        assertTrue(Decision.REFUSE_AMBIENT_HOSTILE.explanation().contains("06 section 17.1"));
        for (Decision d : List.of(Decision.REFUSE_BLOCKED_HOSTILE, Decision.REFUSE_UNLISTED_HOSTILE, Decision.REFUSE_LOCAL_CAP)) {
            assertFalse(d.allowed());
            assertTrue(d.explanation().contains("06 section 17.3"), d.name());
        }
        assertTrue(Decision.ALLOW_BY_PROFILE.allowed());
        assertFalse(Decision.REFUSE_NATURAL_CREAKING_HEART.allowed());
        assertTrue(Decision.REFUSE_NATURAL_CREAKING_HEART.explanation().contains("06 section 15"));
        assertTrue(Decision.ALLOW.allowed());
    }

    // Profiles are saved by ID, and only the settlement profile suppresses ambient hostiles.
    @Test
    void profilesAreSavedByIdentifier() {
        for (SpawnProfile profile : SpawnProfile.values()) {
            assertEquals("inhabited_world", profile.id().getNamespace());
            assertEquals(profile, SpawnProfile.CODEC.parse(JsonOps.INSTANCE, SpawnProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile).getOrThrow()).getOrThrow());
            assertEquals(profile == SpawnProfile.PROTECTED_SETTLEMENT, profile.suppressesAmbientHostiles(), profile.name());
            assertEquals(profile.id(), ThreatProfile.builtIn(profile).id(), profile.name());
            assertEquals(profile.suppressesAmbientHostiles(), ThreatProfile.builtIn(profile).ambientHostiles() == ThreatProfile.AmbientHostiles.SUPPRESSED);
        }
        assertEquals("inhabited_world:protected_settlement", SpawnProfile.PROTECTED_SETTLEMENT.id().toString());
        assertNull(SpawnProfile.byId(Identifier.fromNamespaceAndPath("inhabited_world", "no_such_profile")));
        assertTrue(SpawnProfile.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive("inhabited_world:no_such_profile")).isError());
    }
}
