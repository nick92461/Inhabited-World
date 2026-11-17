package io.github.nick92461.inhabitedworld.spawn;

import io.github.nick92461.inhabitedworld.threat.ThreatProfile;

import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

/*
  The spawn rules of an Inhabited World as plain Java (canon 06 sections 13 and 17.5): one decision from what is
  spawning (the entity type and its facts), why (its spawn reason) and where (the threat profile of the semantic
  region there, with that region's current ambient population). It takes plain facts, not live game objects, so it
  is unit-tested directly (08 section 50). Callers must keep it allocation-free (06 section 17.6).

  The decision table, in order:
  1. A Default world: vanilla, everything allowed.
  2. A vanilla pseudo-human (tag inhabited_world:prohibited_human_like) that the game adds by itself (AMBIENT or
     SOURCED): refused in every region (06 section 13).
  3. A DELIBERATE spawn (command, spawn egg, dispenser, bucket; later our own encounters and events) or a DERIVED one
     (it continues an existing mob): allowed in every region (06 section 17.5: blocking natural spawning in a town must
     not block deliberate spawns, and development must stay possible).
  4. An AMBIENT hostile (monster category), by the region's threat profile (06 sections 17.1 to 17.3):
     a. a type the profile blocks: refused, in any mode
     b. a profile that suppresses ambient hostiles (a protected settlement): refused (06 section 17.1). Hostiles from
        outside still walk in: nothing here touches existing mobs.
     c. a profile with its own list (a POI such as the abandoned mine): a type it does not list is refused
     d. the region already holds the profile's local cap of ambient hostiles: refused
     e. a listed type spawning naturally: allowed by the profile, whose spawn conditions then replace vanilla's light
        rules (ALLOW_BY_PROFILE; SpawnGovernance applies them)
  5. Everything else: allowed. The wilderness keeps vanilla spawning (06 section 17.2).
 */
public final class SpawnPolicy {
    /** Why a spawn happens, as far as the rules care. */
    public enum Origin {
        /**
         * The world makes the mob from nothing, as ecology: natural spawning, chunk generation, vanilla patrols,
         * zombie reinforcements and vanilla's world events (raids, sieges, traders, thunder traps). This is the
         * "ordinary hostile generation" a protected settlement suppresses (06 section 17.1).
         */
        AMBIENT,
        /**
         * The game adds the mob because something placed there makes it: a spawner block or trial spawner, a
         * structure's own mobs, a mob summoning others (evoker vexes, jockey riders), or a trigger (infested blocks,
         * sculk shriekers, pumpkin golems). Not ambient: such a source in a town is itself the reason for the
         * presence (06 section 17.1, "hostile presence inside a settlement should normally have a reason"). Natural
         * sources of these are already suppressed or disabled (monster rooms, structures, see the step-3 notes).
         */
        SOURCED,
        /** Someone chose this exact spawn: commands, spawn eggs (also from dispensers), buckets. */
        DELIBERATE,
        /** The mob continues one that already existed: breeding, conversion, loading, changing dimension. */
        DERIVED;

        /** Whether the game adds the mob by itself (the reasons the human-like ban covers). */
        public boolean automatic() {
            return this == AMBIENT || this == SOURCED;
        }
    }

    public enum Decision {
        ALLOW(true, "no rule refuses it"),
        ALLOW_BY_PROFILE(true,
                "the region's threat profile lists this hostile; its own spawn conditions replace vanilla's light rules here (canon 06 section 17.3)"),
        REFUSE_PROHIBITED_HUMAN_LIKE(false,
                "vanilla human-like mobs never spawn on their own in an Inhabited World, in any region (canon 06 section 13)"),
        REFUSE_AMBIENT_HOSTILE(false,
                "ordinary hostile generation does not occur in this region: a protected settlement; hostiles can still walk in (canon 06 section 17.1)"),
        REFUSE_BLOCKED_HOSTILE(false,
                "the region's threat profile blocks this hostile type (canon 06 section 17.3)"),
        REFUSE_UNLISTED_HOSTILE(false,
                "the region's threat profile allows only the hostiles it lists (canon 06 section 17.3)"),
        REFUSE_LOCAL_CAP(false,
                "the region already holds its threat profile's local cap of ambient hostiles (canon 06 section 17.3)"),
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
            case NATURAL, CHUNK_GENERATION, PATROL, REINFORCEMENT, EVENT -> Origin.AMBIENT;
            case STRUCTURE, SPAWNER, TRIAL_SPAWNER, MOB_SUMMONED, JOCKEY, TRIGGERED -> Origin.SOURCED;
            case COMMAND, SPAWN_ITEM_USE, DISPENSER, BUCKET -> Origin.DELIBERATE;
            case BREEDING, CONVERSION, LOAD, DIMENSION_TRAVEL -> Origin.DERIVED;
        };
    }

    /**
     * @param rpgWorld            whether the level is an Inhabited World (its persisted identity, canon 02 section 4)
     * @param prohibitedHumanLike whether the entity type is a vanilla pseudo-human (tag inhabited_world:prohibited_human_like)
     * @param hostile             whether the entity type is a hostile mob (monster category)
     * @param type                the entity type (for the profile's own lists)
     * @param reason              why the game is spawning it
     * @param profile             the threat profile of the semantic region at the spawn position
     * @param population          the ambient hostiles standing in that region now (only read when the profile has a local cap)
     */
    public static Decision decide(boolean rpgWorld, boolean prohibitedHumanLike, boolean hostile, EntityType<?> type, EntitySpawnReason reason,
                                  ThreatProfile profile, int population) {
        if (!rpgWorld) {
            return Decision.ALLOW;
        }
        Origin origin = origin(reason);
        if (prohibitedHumanLike && origin.automatic()) {
            return Decision.REFUSE_PROHIBITED_HUMAN_LIKE;
        }
        if (origin != Origin.AMBIENT || !hostile) {
            return Decision.ALLOW;
        }
        if (profile.blocks(type)) {
            return Decision.REFUSE_BLOCKED_HOSTILE;
        }
        ThreatProfile.AmbientHostiles mode = profile.ambientHostiles();
        if (mode == ThreatProfile.AmbientHostiles.SUPPRESSED) {
            return Decision.REFUSE_AMBIENT_HOSTILE;
        }
        if (mode == ThreatProfile.AmbientHostiles.LISTED && !profile.lists(type)) {
            return Decision.REFUSE_UNLISTED_HOSTILE;
        }
        if (population >= profile.localCap()) {
            return Decision.REFUSE_LOCAL_CAP;
        }
        return mode == ThreatProfile.AmbientHostiles.LISTED && reason == EntitySpawnReason.NATURAL ? Decision.ALLOW_BY_PROFILE : Decision.ALLOW;
    }

    /**
     * A thunderstorm's skeleton trap (a skeleton horse whose four skeleton riders appear when a player comes close).
     * Weather makes these hostiles from nothing, so they are ambient hostile generation: refused where the region
     * suppresses it, vanilla elsewhere.
     */
    public static Decision decideSkeletonTrap(boolean rpgWorld, ThreatProfile profile, int population) {
        return decide(rpgWorld, false, true, EntityTypes.SKELETON, EntitySpawnReason.EVENT, profile, population);
    }

    /**
     * A creaking heart summoning its creaking. Vanilla does this itself (reason SPAWNER) outside
     * SpawnPlacements.checkSpawnRules, so it gets its own rule: a heart that world generation placed is a hostile
     * source outside the planner and stays dormant, while a heart a player crafted and placed is a deliberate
     * choice and works as in vanilla, in every region.
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
