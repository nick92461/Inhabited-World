package io.github.nick92461.inhabitedworld.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.EntitySpawnReason;

import io.github.nick92461.inhabitedworld.spawn.SpawnPolicy.Decision;
import io.github.nick92461.inhabitedworld.spawn.SpawnPolicy.Origin;

// The prohibited human-like rule (canon 06 sections 13, 17.5), in Inhabited Worlds and in Default worlds.
class SpawnPolicyTest {
    // The game's own generation paths: these must never bring vanilla pseudo-humans into an Inhabited World.
    private static final Set<EntitySpawnReason> AUTOMATIC = EnumSet.of(
            EntitySpawnReason.NATURAL, EntitySpawnReason.CHUNK_GENERATION, EntitySpawnReason.STRUCTURE,
            EntitySpawnReason.PATROL, EntitySpawnReason.EVENT, EntitySpawnReason.SPAWNER,
            EntitySpawnReason.TRIAL_SPAWNER, EntitySpawnReason.REINFORCEMENT, EntitySpawnReason.MOB_SUMMONED,
            EntitySpawnReason.JOCKEY, EntitySpawnReason.TRIGGERED);

    // Development and debugging must stay possible (06 section 17.5).
    private static final Set<EntitySpawnReason> DELIBERATE = EnumSet.of(
            EntitySpawnReason.COMMAND, EntitySpawnReason.SPAWN_ITEM_USE, EntitySpawnReason.DISPENSER, EntitySpawnReason.BUCKET);

    @Test
    void everyReasonHasAnOrigin() {
        for (EntitySpawnReason reason : EntitySpawnReason.values()) {
            Origin origin = SpawnPolicy.origin(reason);
            assertEquals(AUTOMATIC.contains(reason), origin == Origin.AUTOMATIC, reason + " is " + origin);
            assertEquals(DELIBERATE.contains(reason), origin == Origin.DELIBERATE, reason + " is " + origin);
        }
    }

    @Test
    void derivedReasonsContinueExistingMobs() {
        for (EntitySpawnReason reason : EnumSet.of(EntitySpawnReason.BREEDING, EntitySpawnReason.CONVERSION,
                EntitySpawnReason.LOAD, EntitySpawnReason.DIMENSION_TRAVEL)) {
            assertEquals(Origin.DERIVED, SpawnPolicy.origin(reason), reason.name());
        }
    }

    @Test
    void inhabitedWorldRefusesProhibitedMobsOnlyForAutomaticReasons() {
        for (EntitySpawnReason reason : EntitySpawnReason.values()) {
            Decision decision = SpawnPolicy.decide(true, true, reason);
            assertEquals(!AUTOMATIC.contains(reason), decision.allowed(), reason.name());
            if (!decision.allowed()) {
                assertEquals(Decision.REFUSE_PROHIBITED_HUMAN_LIKE, decision);
            }
        }
    }

    @Test
    void inhabitedWorldAllowsOtherMobsForEveryReason() {
        for (EntitySpawnReason reason : EntitySpawnReason.values()) {
            assertEquals(Decision.ALLOW, SpawnPolicy.decide(true, false, reason), reason.name());
        }
    }

    // A Default world (isRpg false) keeps vanilla behaviour, villagers and witches included.
    @Test
    void defaultWorldAllowsEverything() {
        for (EntitySpawnReason reason : EntitySpawnReason.values()) {
            assertEquals(Decision.ALLOW, SpawnPolicy.decide(false, true, reason), reason.name());
            assertEquals(Decision.ALLOW, SpawnPolicy.decide(false, false, reason), reason.name());
        }
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
    void creakingHeartRefusalExplainsItself() {
        assertFalse(Decision.REFUSE_NATURAL_CREAKING_HEART.allowed());
        assertTrue(Decision.REFUSE_NATURAL_CREAKING_HEART.explanation().contains("06 section 15"));
    }

    @Test
    void refusalExplainsItself() {
        assertFalse(Decision.REFUSE_PROHIBITED_HUMAN_LIKE.allowed());
        assertTrue(Decision.REFUSE_PROHIBITED_HUMAN_LIKE.explanation().contains("06 section 13"));
        assertTrue(Decision.ALLOW.allowed());
    }
}
