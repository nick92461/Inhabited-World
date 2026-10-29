package io.github.nick92461.inhabitedworld.spawn;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.worldgen.RpgWorlds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ServerLevelAccessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
  The bridge from Minecraft's spawn check (SpawnPlacements.checkSpawnRules, see SpawnPlacementsMixin) to the
  plain-Java SpawnPolicy. It runs for every spawn attempt that vanilla's own rules pass, also on chunk-generation
  threads, so it only reads immutable facts and allocates nothing (canon 06 section 17.6).
 */
public final class SpawnGovernance {
    /** Vanilla entity types that stand for people (canon 06 section 13). Data, so it can grow without code. */
    public static final TagKey<EntityType<?>> PROHIBITED_HUMAN_LIKE =
            TagKey.create(Registries.ENTITY_TYPE, InhabitedWorld.id("prohibited_human_like"));

    // Refusals log at DEBUG under this logger, so turning it on doesn't flood the main log.
    private static final Logger LOGGER = LoggerFactory.getLogger(InhabitedWorld.MOD_ID + "/spawn");

    private SpawnGovernance() {
    }

    public static boolean allows(EntityType<?> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos) {
        SpawnPolicy.Decision decision = decide(type, level.getLevel(), reason);
        if (decision.allowed()) {
            return true;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("refused {} ({}) at {}: {}", EntityType.getKey(type), reason, pos.toShortString(), decision.explanation());
        }
        return false;
    }

    public static SpawnPolicy.Decision decide(EntityType<?> type, ServerLevel level, EntitySpawnReason reason) {
        return SpawnPolicy.decide(RpgWorlds.isRpg(level), isProhibitedHumanLike(type), reason);
    }

    // EntityType has no tag check of its own in 26.3; Entity.is(TagKey) goes through the same built-in holder.
    @SuppressWarnings("deprecation")
    public static boolean isProhibitedHumanLike(EntityType<?> type) {
        return type.builtInRegistryHolder().is(PROHIBITED_HUMAN_LIKE);
    }
}
