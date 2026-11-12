package io.github.nick92461.inhabitedworld.spawn;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.region.Regions;
import io.github.nick92461.inhabitedworld.region.SemanticRegion;
import io.github.nick92461.inhabitedworld.worldgen.RpgWorlds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.CreakingHeartBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
  The bridge from Minecraft's spawn checks to the plain-Java SpawnPolicy. Hooks:
  - SpawnPlacements.checkSpawnRules (SpawnPlacementsMixin): natural and chunk-generation spawning, spawner blocks
    without custom rules, trial spawners, zombie reinforcements
  - PhantomSpawner (PhantomSpawnerMixin): phantoms
  - ServerLevel.tickThunder (ServerLevelRegionsMixin): skeleton traps
  - CreakingHeartBlockEntity (CreakingHeartBlockEntityMixin): creakings from hearts

  It runs for every spawn attempt that vanilla's own rules pass, also on chunk-generation threads, so it only reads
  immutable facts (the entity type, the level's identity and its region index) and allocates nothing
  (canon 06 section 17.6).
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
        ServerLevel server = level.getLevel();
        SpawnPolicy.Decision decision = decide(type, server, reason, pos);
        if (decision.allowed()) {
            return true;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("refused {} ({}) at {} in region {}: {}", EntityType.getKey(type), reason, pos.toShortString(),
                    regionId(server, pos), decision.explanation());
        }
        return false;
    }

    public static SpawnPolicy.Decision decide(EntityType<?> type, ServerLevel level, EntitySpawnReason reason, BlockPos pos) {
        return SpawnPolicy.decide(RpgWorlds.isRpg(level), isProhibitedHumanLike(type), isHostile(type), reason,
                profileAt(level, pos.getX(), pos.getY(), pos.getZ()));
    }

    /** Whether a thunderstorm may make a skeleton trap at a lightning strike (see ServerLevelRegionsMixin). */
    public static boolean allowsSkeletonTrap(ServerLevel level, BlockPos pos) {
        SpawnPolicy.Decision decision = SpawnPolicy.decideSkeletonTrap(RpgWorlds.isRpg(level), profileAt(level, pos.getX(), pos.getY(), pos.getZ()));
        if (!decision.allowed() && LOGGER.isDebugEnabled()) {
            LOGGER.debug("no skeleton trap at {} in region {}: {}", pos.toShortString(), regionId(level, pos), decision.explanation());
        }
        return decision.allowed();
    }

    /** The spawn profile at a block: its semantic region's, or the wilderness's where a level has no regions. */
    public static SpawnProfile profileAt(ServerLevel level, int x, int y, int z) {
        SemanticRegion region = Regions.at(level, x, y, z);
        return region == null ? SpawnProfile.WILDERNESS : region.profile();
    }

    private static String regionId(ServerLevel level, BlockPos pos) {
        @Nullable SemanticRegion region = Regions.at(level, pos.getX(), pos.getY(), pos.getZ());
        return region == null ? SemanticRegion.WILDERNESS_ID : region.id();
    }

    /**
     * Whether a creaking heart may summon its creaking (see CreakingHeartBlockEntityMixin). Called at most once
     * per heart update (every 20 to 24 ticks) while an awake heart without a creaking has a player nearby.
     */
    public static boolean allowsCreakingHeart(ServerLevel level, BlockPos pos, BlockState heart) {
        SpawnPolicy.Decision decision = decideCreakingHeart(level, heart);
        if (decision.allowed()) {
            return true;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("refused minecraft:creaking from the creaking heart at {}: {}", pos.toShortString(), decision.explanation());
        }
        return false;
    }

    public static SpawnPolicy.Decision decideCreakingHeart(ServerLevel level, BlockState heart) {
        return SpawnPolicy.decideCreakingHeart(RpgWorlds.isRpg(level),
                heart.hasProperty(CreakingHeartBlock.NATURAL) && heart.getValue(CreakingHeartBlock.NATURAL));
    }

    /** Hostile for the region rules: vanilla's monster category (zombies, skeletons, creepers, spiders, phantoms, ...). */
    public static boolean isHostile(EntityType<?> type) {
        return type.getCategory() == MobCategory.MONSTER;
    }

    // EntityType has no tag check of its own in 26.3; Entity.is(TagKey) goes through the same built-in holder.
    @SuppressWarnings("deprecation")
    public static boolean isProhibitedHumanLike(EntityType<?> type) {
        return type.builtInRegistryHolder().is(PROHIBITED_HUMAN_LIKE);
    }
}
