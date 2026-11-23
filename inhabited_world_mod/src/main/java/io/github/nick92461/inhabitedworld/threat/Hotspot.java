package io.github.nick92461.inhabitedworld.threat;

import java.util.List;
import java.util.Optional;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.region.RegionVolume;
import io.github.nick92461.inhabitedworld.spawn.SpawnGovernance;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
  One controlled hostile hotspot of a region, running in a world (canon 06 section 17.7, 09 DoD 17; the definition is
  HotspotDefinition). Every interval_ticks, while a player is within its activation radius:

  1. population: the profile's listed hostiles standing in its box (any origin, persistence-required mobs such as
     encounters excepted), and own: the living mobs it spawned, wherever they are in or around its region
  2. while population < target and own < cap: spawn one of the profile's hostiles (by weight) at a spot its own search
     finds in the box that meets the profile's spawn conditions and keeps min_player_distance from every player, up to
     spawns_per_run at a time

  Its mobs are ordinary hostiles (spawn reason SPAWNER: a placed source, like a spawner block): vanilla AI, vanilla
  despawning when players leave, counted in vanilla's mob caps like any non-persistent hostile. They carry the tags
  inhabited_world.hotspot and inhabited_world.hotspot.<region>.<id>. The spawn still passes the spawn decision, so the
  global rules (the human-like ban) hold here too.

  Respects peaceful difficulty and the spawn_mobs and spawn_monsters game rules, as natural spawning does.
 */
public final class Hotspot {
    public static final String TAG = "inhabited_world.hotspot";

    private static final Logger LOGGER = LoggerFactory.getLogger(InhabitedWorld.MOD_ID + "/hotspot");
    private static final int SPOT_TRIES = 24;
    // How far around the region its own mobs are still counted (they may chase a player out of it).
    private static final int OWN_MARGIN = 48;

    private final RegionThreat region;
    private final HotspotDefinition definition;
    private final SiteAnchor.Resolver anchor;
    private final String tag;
    private final int phase;

    private @Nullable BlockPos resolved;
    private boolean active;
    private int population;
    private int own;
    private int spawnedTotal;
    private @Nullable String problem;

    public Hotspot(RegionThreat region, HotspotDefinition definition, SiteAnchor.Resolver anchor) {
        this.region = region;
        this.definition = definition;
        this.anchor = anchor;
        this.tag = TAG + "." + region.region().id() + "." + definition.id();
        // Spread hotspots over the interval instead of all firing on the same tick.
        this.phase = Math.floorMod(this.tag.hashCode(), definition.intervalTicks());
    }

    public void tick(ServerLevel level) {
        if (level.getGameTime() % this.definition.intervalTicks() != this.phase) {
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().get(GameRules.SPAWN_MOBS)
                || !level.getGameRules().get(GameRules.SPAWN_MONSTERS)) {
            this.active = false;
            return;
        }
        this.run(level);
    }

    /** One round of the hotspot (tick calls it every interval; tests call it directly). Returns how many it spawned. */
    public int run(ServerLevel level) {
        BlockPos at = this.anchor(level);
        if (at == null) {
            this.active = false;
            this.problem = "its anchor's chunk is not loaded";
            return 0;
        }
        this.active = SpawnSpots.playerWithin(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, this.definition.activationRadius());
        if (!this.active) {
            return 0;
        }
        this.count(level, at);
        int spawned = 0;
        while (spawned < this.definition.spawnsPerRun() && this.population < this.definition.target() && this.own < this.definition.cap()) {
            if (!this.spawnOne(level, at)) {
                break;
            }
            spawned++;
            this.population++;
            this.own++;
        }
        this.spawnedTotal += spawned;
        return spawned;
    }

    private void count(ServerLevel level, BlockPos at) {
        ThreatProfile profile = this.region.profile();
        this.population = level.getEntities((Entity) null, this.box(at),
                e -> e instanceof Mob mob && mob.isAlive() && profile.lists(mob.getType()) && !mob.isPersistenceRequired()).size();
        RegionVolume v = this.region.region().volume();
        AABB box = this.box(at);
        AABB around = new AABB(Math.min(v.minX() - OWN_MARGIN, box.minX), Math.min(v.minY(), box.minY), Math.min(v.minZ() - OWN_MARGIN, box.minZ),
                Math.max(v.maxX() + 1 + OWN_MARGIN, box.maxX), Math.max(v.maxY() + 1, box.maxY), Math.max(v.maxZ() + 1 + OWN_MARGIN, box.maxZ));
        this.own = level.getEntities((Entity) null, around, e -> e.isAlive() && e.entityTags().contains(this.tag)).size();
    }

    private boolean spawnOne(ServerLevel level, BlockPos at) {
        Optional<EntityType<?>> pick = this.region.profile().hostileWeights().getRandom(level.getRandom());
        if (pick.isEmpty()) {
            return false;
        }
        EntityType<?> type = pick.get();
        HotspotDefinition d = this.definition;
        SpawnConditions conditions = this.region.profile().conditions();
        BlockPos spot = SpawnSpots.find(level, type, level.getRandom(), at.getX(), at.getY(), at.getZ(), d.radius(), d.below(), d.above(), SPOT_TRIES,
                p -> conditions.allows(level, p)
                        && !SpawnSpots.playerWithin(level, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, d.minPlayerDistance())
                        && SpawnGovernance.decide(type, level, EntitySpawnReason.SPAWNER, p).allowed());
        if (spot == null) {
            this.problem = "no spot in its box meets the profile's spawn conditions (" + conditions.describe() + ")";
            return false;
        }
        if (!(type.create(level, EntitySpawnReason.SPAWNER) instanceof Mob mob)) {
            this.problem = EntityType.getKey(type) + " is not a mob";
            return false;
        }
        mob.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.SPAWNER, null);
        mob.addTag(TAG);
        mob.addTag(this.tag);
        level.addFreshEntityWithPassengers(mob);
        this.problem = null;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("hotspot {} spawned {} at {}", this.tag, EntityType.getKey(type), spot.toShortString());
        }
        return true;
    }

    /** The box, as an AABB of whole blocks. */
    public AABB box(BlockPos at) {
        int r = this.definition.radius();
        return new AABB(at.getX() - r, at.getY() - this.definition.below(), at.getZ() - r,
                at.getX() + r + 1, at.getY() + this.definition.above() + 1, at.getZ() + r + 1);
    }

    /** The anchor, resolved once its chunk is loaded. */
    public @Nullable BlockPos anchor(ServerLevel level) {
        if (this.resolved == null) {
            this.resolved = this.anchor.resolve(level).orElse(null);
        }
        return this.resolved;
    }

    public RegionThreat region() {
        return this.region;
    }

    public HotspotDefinition definition() {
        return this.definition;
    }

    public String tag() {
        return this.tag;
    }

    public String describe(ServerLevel level) {
        BlockPos at = this.anchor(level);
        if (at != null) {
            this.count(level, at);
        }
        return this.region.region().id() + "/" + this.definition.id() + " (" + this.anchor.describe() + (at == null ? ", not resolved yet" : " = " + at.toShortString())
                + "): " + (this.active ? "ACTIVE" : "idle") + ", population " + this.population + " of target " + this.definition.target()
                + ", own mobs " + this.own + " of cap " + this.definition.cap() + ", spawned " + this.spawnedTotal + " this session"
                + (this.problem == null ? "" : "; last problem: " + this.problem) + "\n    " + this.definition.describe();
    }

    public static List<String> describeAll(ServerLevel level, List<Hotspot> hotspots) {
        return hotspots.stream().map(h -> h.describe(level)).toList();
    }
}
