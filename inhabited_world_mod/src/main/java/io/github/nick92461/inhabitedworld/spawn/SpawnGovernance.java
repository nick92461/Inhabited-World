package io.github.nick92461.inhabitedworld.spawn;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.region.RegionIndex;
import io.github.nick92461.inhabitedworld.region.Regions;
import io.github.nick92461.inhabitedworld.region.SemanticRegion;
import io.github.nick92461.inhabitedworld.threat.RegionThreat;
import io.github.nick92461.inhabitedworld.threat.ThreatProfile;
import io.github.nick92461.inhabitedworld.worldgen.RpgWorlds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.CreakingHeartBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
  The bridge from Minecraft's spawn checks to the plain-Java SpawnPolicy. Hooks:
  - SpawnPlacements.checkSpawnRules (SpawnPlacementsMixin): natural and chunk-generation spawning, spawner blocks
    without custom rules, trial spawners, zombie reinforcements
  - NaturalSpawner.mobsAt (NaturalSpawnerMixin): inside a region whose threat profile lists its own hostiles, natural
    spawning draws its monsters from that list instead of the biome's
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

    // A level without planned regions is all wilderness (a Default world, or an Inhabited World made before the planner).
    private static final RegionThreat UNPLANNED = new RegionThreat(
            SemanticRegion.wilderness(new io.github.nick92461.inhabitedworld.region.RegionVolume(-30_000_000, -2048, -30_000_000, 30_000_000, 2047, 30_000_000)),
            ThreatProfile.WILDERNESS, java.util.Optional.empty());

    private SpawnGovernance() {
    }

    /**
     * The spawn check: vanilla's own verdict (vanilla) combined with the region's rules. Where the region's threat
     * profile lists the hostile (ALLOW_BY_PROFILE), the profile's spawn conditions and vanilla's ground rule decide
     * instead of vanilla's light rules, so a POI can be dangerous where the wilderness would not be.
     */
    public static boolean check(boolean vanilla, EntityType<?> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        ServerLevel server = level.getLevel();
        RegionThreat threat = threatAt(server, pos.getX(), pos.getY(), pos.getZ());
        SpawnPolicy.Decision decision = decide(type, server, reason, threat);
        if (decision == SpawnPolicy.Decision.ALLOW_BY_PROFILE) {
            return threat.profile().conditions().allows(level, pos) && groundAllows(type, level, reason, pos, random);
        }
        if (decision.allowed()) {
            return vanilla;
        }
        if (vanilla && LOGGER.isDebugEnabled()) {
            LOGGER.debug("refused {} ({}) at {} in region {}: {}", EntityType.getKey(type), reason, pos.toShortString(),
                    threat.region().id(), decision.explanation());
        }
        return false;
    }

    /** Whether the region's rules allow a spawn whose vanilla checks were already made elsewhere (phantoms). */
    public static boolean allows(EntityType<?> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos) {
        ServerLevel server = level.getLevel();
        RegionThreat threat = threatAt(server, pos.getX(), pos.getY(), pos.getZ());
        SpawnPolicy.Decision decision = decide(type, server, reason, threat);
        if (!decision.allowed() && LOGGER.isDebugEnabled()) {
            LOGGER.debug("refused {} ({}) at {} in region {}: {}", EntityType.getKey(type), reason, pos.toShortString(),
                    threat.region().id(), decision.explanation());
        }
        return decision.allowed();
    }

    // Vanilla's rule under every mob's light rule: it stands on a block that lets mobs spawn on it (not glass, slabs...).
    @SuppressWarnings("unchecked")
    private static boolean groundAllows(EntityType<?> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return Mob.checkMobSpawnRules((EntityType<? extends Mob>) type, level, reason, pos, random);
    }

    public static SpawnPolicy.Decision decide(EntityType<?> type, ServerLevel level, EntitySpawnReason reason, BlockPos pos) {
        return decide(type, level, reason, threatAt(level, pos.getX(), pos.getY(), pos.getZ()));
    }

    public static SpawnPolicy.Decision decide(EntityType<?> type, ServerLevel level, EntitySpawnReason reason, RegionThreat threat) {
        return SpawnPolicy.decide(RpgWorlds.isRpg(level), isProhibitedHumanLike(type), isHostile(type), type, reason, threat.profile(), threat.population());
    }

    /**
     * The monster spawn list natural spawning uses at a position: the region's threat-profile list where the profile
     * has its own, else null (the biome's list applies). Allocation-free.
     */
    public static @Nullable WeightedList<MobSpawnSettings.SpawnerData> naturalMonstersAt(ServerLevel level, BlockPos pos) {
        RegionIndex index = Regions.index(level);
        if (index == null || !RpgWorlds.isRpg(level)) {
            return null;
        }
        return index.threatAt(pos.getX(), pos.getY(), pos.getZ()).profile().naturalMonsters();
    }

    /** Whether a thunderstorm may make a skeleton trap at a lightning strike (see ServerLevelRegionsMixin). */
    public static boolean allowsSkeletonTrap(ServerLevel level, BlockPos pos) {
        RegionThreat threat = threatAt(level, pos.getX(), pos.getY(), pos.getZ());
        SpawnPolicy.Decision decision = SpawnPolicy.decideSkeletonTrap(RpgWorlds.isRpg(level), threat.profile(), threat.population());
        if (!decision.allowed() && LOGGER.isDebugEnabled()) {
            LOGGER.debug("no skeleton trap at {} in region {}: {}", pos.toShortString(), regionId(level, pos), decision.explanation());
        }
        return decision.allowed();
    }

    /** The threat binding at a block: its semantic region's, or the plain wilderness where a level has no regions. */
    public static RegionThreat threatAt(ServerLevel level, int x, int y, int z) {
        RegionIndex index = Regions.index(level);
        return index == null ? UNPLANNED : index.threatAt(x, y, z);
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
