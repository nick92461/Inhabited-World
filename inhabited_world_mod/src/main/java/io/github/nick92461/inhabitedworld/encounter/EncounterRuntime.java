package io.github.nick92461.inhabitedworld.encounter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.region.SemanticRegion;
import io.github.nick92461.inhabitedworld.threat.SiteAnchor;
import io.github.nick92461.inhabitedworld.threat.SpawnSpots;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

import org.jspecify.annotations.Nullable;

/*
  The guaranteed encounters of a running world (canon 06 sections 17.7 and 17.8), applying each encounter's persistent
  EncounterRecord to the world. Lives on the overworld with the world's other threat state (LevelThreats).

  Once a second, per encounter:
  - NOT_SPAWNED: when a player is within its trigger radius of its anchor (and the anchor's chunk is ticking, and the
    difficulty isn't peaceful), spawn it: the defined mob at a free spot near the anchor, persistence-required (so it
    never despawns), tagged with its ID, its wandering kept near that spot. The record turns ACTIVE first, so the mob
    entering the world is already the encounter's mob.
  - ACTIVE, mob loaded: record where it is. If it has left its POI region and has had nothing to fight for five
    checks, bring it back to its spot (it may chase a player out; it does not stay out).
  - ACTIVE, mob not loaded: normally its chunk is simply unloaded, and the record waits. If the chunks around its last
    known position have their entities loaded and it is still missing for five checks, it is gone without a trace
    (a lost save, an edited world): the encounter is lost and spawns again on the next approach.
  - DEFEATED: stays defeated, unless its definition respawns it, which happens when no player is within the trigger
    radius.

  Events (from LevelThreats): a mob of an encounter dying defeats it at once, so a mob that dies while its chunk is
  unloading is defeated, not respawned. A mob that leaves the world without dying (a command's discard, peaceful
  difficulty, a portal) is lost. A mob with an encounter's tag that loads but isn't the encounter's current mob (the
  encounter is defeated or has another mob, or the mob is in another dimension) is removed, so nothing duplicates.

  No custom AI: the mob keeps vanilla's (06 section 17.9); home restriction is vanilla's own mechanism.
 */
public final class EncounterRuntime {
    public static final String TAG = "inhabited_world.encounter";

    private static final int CHECK_TICKS = 20;
    private static final int GRACE_CHECKS = 5;
    private static final int SPOT_RADIUS = 6;
    private static final int SPOT_HEIGHT = 8;
    private static final int SPOT_TRIES = 24;

    /** One encounter bound to a world: its definition, where its anchor is, and the region it belongs to. */
    public static final class Bound {
        private final Identifier id;
        private final EncounterDefinition definition;
        private final SiteAnchor.Resolver anchor;
        private final @Nullable SemanticRegion region;
        private final String tag;
        private @Nullable BlockPos resolved;
        private int missingChecks;
        private int outsideChecks;
        private @Nullable String problem;

        public Bound(Identifier id, EncounterDefinition definition, SiteAnchor.Resolver anchor, @Nullable SemanticRegion region) {
            this.id = id;
            this.definition = definition;
            this.anchor = anchor;
            this.region = region;
            this.tag = tagOf(id);
        }

        public Identifier id() {
            return this.id;
        }

        public EncounterDefinition definition() {
            return this.definition;
        }

        public SiteAnchor.Resolver anchorResolver() {
            return this.anchor;
        }

        public @Nullable SemanticRegion region() {
            return this.region;
        }

        public String tag() {
            return this.tag;
        }

        public @Nullable String problem() {
            return this.problem;
        }

        public @Nullable BlockPos anchor(ServerLevel level) {
            if (this.resolved == null) {
                this.resolved = this.anchor.resolve(level).orElse(null);
            }
            return this.resolved;
        }
    }

    private final Map<Identifier, Bound> encounters = new LinkedHashMap<>();
    private final Map<String, Bound> byTag = new LinkedHashMap<>();
    private final List<Entity> toRemove = new ArrayList<>();

    public EncounterRuntime(List<Bound> encounters) {
        for (Bound bound : encounters) {
            this.add(bound);
        }
    }

    /** Adds an encounter to this world (data binding at install; game tests add their own). Replaces one with the same ID. */
    public void add(Bound bound) {
        Bound old = this.encounters.put(bound.id(), bound);
        if (old != null) {
            this.byTag.remove(old.tag());
        }
        this.byTag.put(bound.tag(), bound);
    }

    public void remove(Identifier id) {
        Bound old = this.encounters.remove(id);
        if (old != null) {
            this.byTag.remove(old.tag());
        }
    }

    public List<Bound> encounters() {
        return List.copyOf(this.encounters.values());
    }

    public @Nullable Bound get(Identifier id) {
        return this.encounters.get(id);
    }

    /** The tag a mob of this encounter carries (letters, digits and . _ - only, so it can be used in selectors unquoted). */
    public static String tagOf(Identifier id) {
        return TAG + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    // ---- ticking ----

    public void tick(ServerLevel level) {
        if (!this.toRemove.isEmpty()) {
            List<Entity> removing = List.copyOf(this.toRemove);
            this.toRemove.clear();
            for (Entity entity : removing) {
                if (!entity.isRemoved()) {
                    entity.discard();
                }
            }
        }
        if (level.getGameTime() % CHECK_TICKS != 0 || this.encounters.isEmpty()) {
            return;
        }
        EncounterData data = Encounters.store(level.getServer());
        if (data == null) {
            return;
        }
        for (Bound bound : this.encounters.values()) {
            this.check(level, data, bound);
        }
    }

    private void check(ServerLevel level, EncounterData data, Bound bound) {
        EncounterRecord record = data.get(bound.id);
        switch (record.state()) {
            case NOT_SPAWNED -> {
                BlockPos at = bound.anchor(level);
                if (at != null && level.getDifficulty() != Difficulty.PEACEFUL && level.isPositionEntityTicking(at)
                        && SpawnSpots.playerWithin(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, bound.definition.triggerRadius())) {
                    this.spawn(level, data, bound);
                }
            }
            case ACTIVE -> {
                Entity entity = level.getEntity(record.entity().orElseThrow());
                if (entity != null && entity.isAlive()) {
                    bound.missingChecks = 0;
                    data.put(bound.id, record.seen(entity.getUUID(), entity.blockPosition()));
                    this.keepInRegion(bound, entity);
                } else if (entity == null && record.lastPos().isPresent() && entitiesLoadedAround(level, record.lastPos().get())) {
                    if (++bound.missingChecks >= GRACE_CHECKS) {
                        bound.missingChecks = 0;
                        InhabitedWorld.LOGGER.warn("[{}] encounter {}: its mob {} is missing from its loaded chunks around {}; it will spawn again",
                                InhabitedWorld.MOD_ID, bound.id, record.entity().get(), record.lastPos().get().toShortString());
                        data.put(bound.id, record.lost(record.entity().get()));
                    }
                } else {
                    bound.missingChecks = 0;
                }
            }
            case DEFEATED -> {
                BlockPos at = bound.anchor(level);
                if (bound.definition.respawns() && at != null
                        && !SpawnSpots.playerWithin(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, bound.definition.triggerRadius())) {
                    data.put(bound.id, record.rearm(true));
                }
            }
        }
    }

    private void keepInRegion(Bound bound, Entity entity) {
        if (bound.region == null || !(entity instanceof Mob mob) || !mob.hasHome()) {
            return;
        }
        BlockPos pos = mob.blockPosition();
        if (bound.region.volume().contains(pos.getX(), pos.getY(), pos.getZ()) || mob.getTarget() != null) {
            bound.outsideChecks = 0;
            return;
        }
        if (++bound.outsideChecks >= GRACE_CHECKS) {
            bound.outsideChecks = 0;
            BlockPos home = mob.getHomePosition();
            mob.getNavigation().stop();
            mob.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
        }
    }

    private static boolean entitiesLoadedAround(ServerLevel level, BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.areEntitiesLoaded(ChunkPos.pack(cx + dx, cz + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Spawns a NOT_SPAWNED encounter now, near its anchor (the trigger distance is not checked: the caller decided).
     * Returns the mob, or null with Bound.problem() saying why not.
     */
    public @Nullable Mob spawn(ServerLevel level, EncounterData data, Bound bound) {
        EncounterRecord record = data.get(bound.id);
        if (record.state() != EncounterRecord.State.NOT_SPAWNED) {
            bound.problem = "it is " + record.state().getSerializedName();
            return null;
        }
        BlockPos at = bound.anchor(level);
        if (at == null) {
            bound.problem = "its anchor's chunk is not loaded";
            return null;
        }
        EntityType<?> type = bound.definition.entity();
        BlockPos spot = SpawnSpots.standable(level, type, at) ? at
                : SpawnSpots.find(level, type, level.getRandom(), at.getX(), at.getY(), at.getZ(), SPOT_RADIUS, SPOT_HEIGHT, SPOT_HEIGHT, SPOT_TRIES, p -> true);
        if (spot == null) {
            bound.problem = "no free spot for " + EntityType.getKey(type) + " within " + SPOT_RADIUS + " blocks of its anchor " + at.toShortString();
            return null;
        }
        if (!(type.create(level, EntitySpawnReason.EVENT) instanceof Mob mob)) {
            bound.problem = EntityType.getKey(type) + " is not a mob";
            return null;
        }
        mob.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.yHeadRot = mob.getYRot();
        bound.definition.name().ifPresent(name -> mob.setCustomName(Component.literal(name)));
        bound.definition.equipment().forEach((slot, item) -> mob.setItemSlot(slot, new ItemStack(item)));
        mob.setPersistenceRequired();
        mob.setHomeTo(spot, bound.definition.homeRadius());
        mob.addTag(TAG);
        mob.addTag(bound.tag);
        // The record first: the mob's load event must already see it as the encounter's own mob.
        data.put(bound.id, record.spawned(mob.getUUID(), spot));
        if (!level.addFreshEntity(mob)) {
            data.put(bound.id, record);
            bound.problem = "the level refused the mob";
            return null;
        }
        bound.problem = null;
        bound.missingChecks = 0;
        bound.outsideChecks = 0;
        InhabitedWorld.LOGGER.info("[{}] encounter {} spawned: {} at {}", InhabitedWorld.MOD_ID, bound.id, EntityType.getKey(type), spot.toShortString());
        return mob;
    }

    /** Development: back to NOT_SPAWNED, removing its mob if loaded. */
    public void reset(ServerLevel level, EncounterData data, Bound bound) {
        EncounterRecord record = data.get(bound.id);
        data.put(bound.id, record.reset());
        record.entity().map(level::getEntity).ifPresent(Entity::discard);
        bound.missingChecks = 0;
        bound.outsideChecks = 0;
    }

    // ---- entity events ----

    private @Nullable Bound boundOf(Entity entity) {
        if (entity.entityTags().isEmpty() || !entity.entityTags().contains(TAG)) {
            return null;
        }
        for (String tag : entity.entityTags()) {
            Bound bound = this.byTag.get(tag);
            if (bound != null) {
                return bound;
            }
        }
        return null;
    }

    /** A mob entered a level (spawned, or loaded with its chunk). */
    public void onLoad(Entity entity, ServerLevel level, ServerLevel overworld) {
        Bound bound = this.boundOf(entity);
        if (bound == null) {
            return;
        }
        EncounterData data = Encounters.store(overworld.getServer());
        if (data == null) {
            return;
        }
        EncounterRecord record = data.get(bound.id);
        if (level != overworld) {
            // Encounters belong to the overworld's POIs; one that went through a portal is lost there and spawns again.
            data.put(bound.id, record.lost(entity.getUUID()));
            this.toRemove.add(entity);
            return;
        }
        if (record.judgeLoaded(entity.getUUID()) == EncounterRecord.Loaded.REMOVE) {
            InhabitedWorld.LOGGER.info("[{}] encounter {} is {}: removing a stale copy of its mob ({})", InhabitedWorld.MOD_ID, bound.id,
                    record.state().getSerializedName(), entity.getUUID());
            this.toRemove.add(entity);
        } else {
            data.put(bound.id, record.seen(entity.getUUID(), entity.blockPosition()));
        }
    }

    /** A mob left a level: its chunk unloaded, or it was removed. */
    public void onUnload(Entity entity, ServerLevel level, ServerLevel overworld) {
        Bound bound = this.boundOf(entity);
        if (bound == null || level != overworld) {
            return;
        }
        EncounterData data = Encounters.store(overworld.getServer());
        if (data == null) {
            return;
        }
        EncounterRecord record = data.get(bound.id);
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason == null) {
            return;
        }
        switch (reason) {
            case UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER -> data.put(bound.id, record.seen(entity.getUUID(), entity.blockPosition()));
            // A death was recorded by onDeath already; a mob removed as killed without a death event still counts.
            case KILLED -> data.put(bound.id, record.defeated(entity.getUUID(), "removed"));
            case DISCARDED, CHANGED_DIMENSION -> {
                if (record.is(entity.getUUID())) {
                    InhabitedWorld.LOGGER.info("[{}] encounter {}: its mob left the world without dying ({}); it will spawn again",
                            InhabitedWorld.MOD_ID, bound.id, reason);
                }
                data.put(bound.id, record.lost(entity.getUUID()));
            }
        }
    }

    /** A living entity died (any cause: a player, a command's /kill, lava). */
    public void onDeath(LivingEntity entity, DamageSource source, ServerLevel overworld) {
        Bound bound = this.boundOf(entity);
        if (bound == null) {
            return;
        }
        EncounterData data = Encounters.store(overworld.getServer());
        if (data == null) {
            return;
        }
        EncounterRecord record = data.get(bound.id);
        if (record.is(entity.getUUID())) {
            String cause = source.getEntity() instanceof Player ? "player"
                    : source.typeHolder().unwrapKey().map(k -> k.identifier().toString()).orElse("unknown");
            data.put(bound.id, record.defeated(entity.getUUID(), cause));
            InhabitedWorld.LOGGER.info("[{}] encounter {} defeated ({})", InhabitedWorld.MOD_ID, bound.id, cause);
        }
    }

    /** The encounter's mob, if it is loaded in the level. */
    public Optional<Mob> entity(ServerLevel level, EncounterData data, Identifier id) {
        EncounterRecord record = data.get(id);
        return record.entity().map(level::getEntity).filter(e -> e instanceof Mob && e.isAlive()).map(e -> (Mob) e);
    }
}
