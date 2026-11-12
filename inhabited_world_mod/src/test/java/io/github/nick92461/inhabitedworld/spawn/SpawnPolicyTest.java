package io.github.nick92461.inhabitedworld.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;

import io.github.nick92461.inhabitedworld.spawn.SpawnPolicy.Decision;
import io.github.nick92461.inhabitedworld.spawn.SpawnPolicy.Origin;

/*
  The spawn decision table (canon 06 sections 13, 17.1, 17.2, 17.5): entity facts x spawn reason x region profile,
  in Inhabited Worlds and in Default worlds.
 */
class SpawnPolicyTest {
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

    // The global ban (06 section 13): every region, every automatic reason; deliberate and derived spawns pass.
    @Test
    void prohibitedHumanLikeMobsAreRefusedInEveryRegionForAutomaticReasons() {
        for (SpawnProfile profile : SpawnProfile.values()) {
            for (boolean hostile : new boolean[]{false, true}) {
                for (EntitySpawnReason reason : EntitySpawnReason.values()) {
                    Decision decision = SpawnPolicy.decide(true, true, hostile, reason, profile);
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
            Decision hostile = SpawnPolicy.decide(true, false, true, reason, SpawnProfile.PROTECTED_SETTLEMENT);
            assertEquals(AMBIENT.contains(reason) ? Decision.REFUSE_AMBIENT_HOSTILE : Decision.ALLOW, hostile, "hostile, " + reason);
            assertEquals(Decision.ALLOW, SpawnPolicy.decide(true, false, false, reason, SpawnProfile.PROTECTED_SETTLEMENT),
                    "animals keep spawning in town, " + reason);
        }
    }

    // Commands, spawn eggs, dispensers, buckets, and our future encounters pass in town (06 section 17.5).
    @Test
    void deliberateHostileSpawnsPassInTown() {
        for (EntitySpawnReason reason : DELIBERATE) {
            assertEquals(Decision.ALLOW, SpawnPolicy.decide(true, false, true, reason, SpawnProfile.PROTECTED_SETTLEMENT), reason.name());
        }
    }

    // The wilderness keeps vanilla-style hostile ecology (06 section 17.2); the POI placeholder too, until Phase 3.
    @Test
    void wildernessAndPoiPlaceholderAllowEveryOrdinaryMob() {
        for (SpawnProfile profile : new SpawnProfile[]{SpawnProfile.WILDERNESS, SpawnProfile.POI_PLACEHOLDER}) {
            for (EntitySpawnReason reason : EntitySpawnReason.values()) {
                assertEquals(Decision.ALLOW, SpawnPolicy.decide(true, false, true, reason, profile), profile + " hostile " + reason);
                assertEquals(Decision.ALLOW, SpawnPolicy.decide(true, false, false, reason, profile), profile + " passive " + reason);
            }
        }
    }

    // A Default world (isRpg false) keeps vanilla behaviour, villagers and witches included, whatever the profile says.
    @Test
    void defaultWorldAllowsEverything() {
        for (SpawnProfile profile : SpawnProfile.values()) {
            for (EntitySpawnReason reason : EntitySpawnReason.values()) {
                for (int facts = 0; facts < 4; facts++) {
                    assertEquals(Decision.ALLOW, SpawnPolicy.decide(false, (facts & 1) != 0, (facts & 2) != 0, reason, profile), profile + " " + reason);
                }
            }
        }
    }

    @Test
    void skeletonTrapsAreAmbientHostileGeneration() {
        assertEquals(Decision.REFUSE_AMBIENT_HOSTILE, SpawnPolicy.decideSkeletonTrap(true, SpawnProfile.PROTECTED_SETTLEMENT));
        assertEquals(Decision.ALLOW, SpawnPolicy.decideSkeletonTrap(true, SpawnProfile.WILDERNESS));
        assertEquals(Decision.ALLOW, SpawnPolicy.decideSkeletonTrap(true, SpawnProfile.POI_PLACEHOLDER));
        assertEquals(Decision.ALLOW, SpawnPolicy.decideSkeletonTrap(false, SpawnProfile.PROTECTED_SETTLEMENT));
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
        }
        assertEquals("inhabited_world:protected_settlement", SpawnProfile.PROTECTED_SETTLEMENT.id().toString());
        assertNull(SpawnProfile.byId(Identifier.fromNamespaceAndPath("inhabited_world", "no_such_profile")));
        assertTrue(SpawnProfile.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive("inhabited_world:no_such_profile")).isError());
    }
}
