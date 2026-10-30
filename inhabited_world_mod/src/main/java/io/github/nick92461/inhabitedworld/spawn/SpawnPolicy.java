package io.github.nick92461.inhabitedworld.spawn;

import net.minecraft.world.entity.EntitySpawnReason;

/*
  The spawn rules of an Inhabited World as plain Java (canon 06 sections 13 and 17.5): one decision from what is
  spawning and why, and from P2B also where (semantic regions). It takes plain facts, not live game objects,
  so it is unit-tested directly (08 section 50). Callers must keep it allocation-free (06 section 17.6).
 */
public final class SpawnPolicy {
    /** Why a spawn happens, as far as the rules care. */
    public enum Origin {
        /** The game generates or adds the mob by itself: natural spawning, chunk generation, structures, spawners, events. */
        AUTOMATIC,
        /** Someone chose this exact spawn: commands, spawn eggs (also from dispensers), buckets. */
        DELIBERATE,
        /** The mob continues one that already existed: breeding, conversion, loading, changing dimension. */
        DERIVED
    }

    public enum Decision {
        ALLOW(true, "no rule refuses it"),
        REFUSE_PROHIBITED_HUMAN_LIKE(false,
                "vanilla human-like mobs never spawn on their own in an Inhabited World (canon 06 section 13)"),
        REFUSE_NATURAL_CREAKING_HEART(false,
                "a generated creaking heart never spawns a creaking in an Inhabited World; a placed one does (canon 06 section 15, user decision 2026-10-29)");

        private final boolean allowed;
        private final String explanation;

        Decision(boolean allowed, String explanation) {
            this.allowed = allowed;
            this.explanation = explanation;
        }

        public boolean allowed() {
            return this.allowed;
        }

        public String explanation() {
            return this.explanation;
        }
    }

    private SpawnPolicy() {
    }

    /*
      Exhaustive on purpose (no default): a new spawn reason in a Minecraft update fails compilation here,
      so it gets classified deliberately.
     */
    public static Origin origin(EntitySpawnReason reason) {
        return switch (reason) {
            case NATURAL, CHUNK_GENERATION, STRUCTURE, PATROL, EVENT, SPAWNER, TRIAL_SPAWNER,
                 REINFORCEMENT, MOB_SUMMONED, JOCKEY, TRIGGERED -> Origin.AUTOMATIC;
            case COMMAND, SPAWN_ITEM_USE, DISPENSER, BUCKET -> Origin.DELIBERATE;
            case BREEDING, CONVERSION, LOAD, DIMENSION_TRAVEL -> Origin.DERIVED;
        };
    }

    /**
     * @param rpgWorld            whether the level is an Inhabited World (its persisted identity, canon 02 section 4)
     * @param prohibitedHumanLike whether the entity type is a vanilla pseudo-human (tag inhabited_world:prohibited_human_like)
     * @param reason              why the game is spawning it
     */
    public static Decision decide(boolean rpgWorld, boolean prohibitedHumanLike, EntitySpawnReason reason) {
        if (rpgWorld && prohibitedHumanLike && origin(reason) == Origin.AUTOMATIC) {
            return Decision.REFUSE_PROHIBITED_HUMAN_LIKE;
        }
        return Decision.ALLOW;
    }

    /**
     * A creaking heart summoning its creaking. Vanilla does this itself (reason SPAWNER) outside
     * SpawnPlacements.checkSpawnRules, so it gets its own rule: a heart that world generation placed is a hostile
     * source outside the planner and stays dormant, while a heart a player crafted and placed is a deliberate
     * choice and works as in vanilla.
     *
     * @param rpgWorld     whether the level is an Inhabited World
     * @param naturalHeart whether the heart was generated (its block-state property natural=true)
     */
    public static Decision decideCreakingHeart(boolean rpgWorld, boolean naturalHeart) {
        if (rpgWorld && naturalHeart) {
            return Decision.REFUSE_NATURAL_CREAKING_HEART;
        }
        return Decision.ALLOW;
    }
}
