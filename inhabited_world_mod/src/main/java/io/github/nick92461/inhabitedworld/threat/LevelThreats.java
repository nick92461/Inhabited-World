package io.github.nick92461.inhabitedworld.threat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.encounter.EncounterDefinition;
import io.github.nick92461.inhabitedworld.encounter.EncounterRuntime;
import io.github.nick92461.inhabitedworld.region.RegionIndex;
import io.github.nick92461.inhabitedworld.region.RegionKind;
import io.github.nick92461.inhabitedworld.region.RegionVolume;
import io.github.nick92461.inhabitedworld.region.Regions;
import io.github.nick92461.inhabitedworld.region.SemanticRegion;
import io.github.nick92461.inhabitedworld.spawn.SpawnProfile;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlan;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

/*
  The threat state of a running Inhabited World (canon 06 sections 17.3, 17.7, 17.8), carried by its overworld
  (LevelThreatsHolder) next to its region index:

  - the binding of each semantic region to its threat profile. A POI region's saved spawn profile is the POI marker
    (inhabited_world:poi_placeholder in every plan so far); its actual profile is chosen by the POI's identity: the
    site's catalog ID -> its PoiThreatBinding -> the threat profile. Settlements and the wilderness keep their engine
    profiles. Nothing new is saved in the WorldPlan: the catalog ID already is (02 section 10).
  - the hotspots of those profiles
  - the guaranteed encounters the POIs hold (EncounterRuntime; their state is in EncounterData)

  Built by Regions.install (world creation, world opening) and rebuilt after a data-pack reload. Once a second it
  counts the ambient hostiles of regions whose profile has a local cap (the spawn path reads that count).
 */
public final class LevelThreats {
    private static final int COUNT_TICKS = 20;

    private final RegionIndex index;
    private final List<Hotspot> hotspots;
    private final EncounterRuntime encounters;

    private LevelThreats(RegionIndex index, List<Hotspot> hotspots, EncounterRuntime encounters) {
        this.index = index;
        this.hotspots = hotspots;
        this.encounters = encounters;
    }

    /** Registered always (not only in development): threats are part of the game. */
    public static void init() {
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            LevelThreats threats = of(level);
            if (threats != null) {
                threats.tick(level);
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            LevelThreats threats = forEntity(entity, level);
            if (threats != null) {
                threats.encounters.onLoad(entity, level, level.getServer().overworld());
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            LevelThreats threats = forEntity(entity, level);
            if (threats != null) {
                threats.encounters.onUnload(entity, level, level.getServer().overworld());
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.level() instanceof ServerLevel level) {
                LevelThreats threats = forEntity(entity, level);
                if (threats != null) {
                    threats.encounters.onDeath(entity, source, level.getServer().overworld());
                }
            }
        });
        // A data-pack reload may change profiles, bindings and encounters: bind them again (states are kept).
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) {
                Regions.install(server);
            }
        });
    }

    // Only entities with tags can be encounter mobs; everything else returns at the first check.
    private static @Nullable LevelThreats forEntity(Entity entity, ServerLevel level) {
        if (entity.entityTags().isEmpty() || !entity.entityTags().contains(EncounterRuntime.TAG)) {
            return null;
        }
        return of(level.getServer().overworld());
    }

    public static @Nullable LevelThreats of(ServerLevel level) {
        return ((LevelThreatsHolder) level).inhabitedWorld$threats();
    }

    /**
     * Binds a planned world's regions to their threat profiles from the loaded data, sets up hotspots and encounters,
     * and puts the result on the overworld. Returns the bound region index (to install on the level).
     */
    public static RegionIndex install(MinecraftServer server, ServerLevel overworld, WorldPlan plan, RegionIndex index) {
        ThreatData data = ThreatData.load(server.getResourceManager());
        RegionIndex bound = index.withThreats(region -> bind(region, plan, data));
        List<Hotspot> hotspots = new ArrayList<>();
        List<EncounterRuntime.Bound> encounters = new ArrayList<>();
        for (RegionThreat threat : bound.threats()) {
            PlannedSite site = threat.region().site().map(plan::site).orElse(null);
            if (site == null) {
                continue;
            }
            for (HotspotDefinition hotspot : threat.profile().hotspots()) {
                hotspots.add(new Hotspot(threat, hotspot, hotspot.anchor().at(site)));
            }
            PoiThreatBinding binding = threat.region().kind() == RegionKind.POI ? data.poiBindings().get(site.catalogId()) : null;
            if (binding == null) {
                continue;
            }
            for (Identifier id : binding.encounters()) {
                EncounterDefinition definition = data.encounters().get(id);
                if (definition == null) {
                    InhabitedWorld.LOGGER.error("[{}] POI {} ({}) names encounter {}, which no data pack defines", InhabitedWorld.MOD_ID, site.id(),
                            site.catalogId(), id);
                    continue;
                }
                encounters.add(new EncounterRuntime.Bound(id, definition, definition.anchor().at(site), threat.region()));
            }
        }
        LevelThreats threats = new LevelThreats(bound, List.copyOf(hotspots), new EncounterRuntime(encounters));
        ((LevelThreatsHolder) overworld).inhabitedWorld$setThreats(threats);
        InhabitedWorld.LOGGER.info("[{}] threat data: {} profiles, {} POI bindings, {} encounter definitions; {} hotspots and {} encounters in this world",
                InhabitedWorld.MOD_ID, data.profiles().size(), data.poiBindings().size(), data.encounters().size(), hotspots.size(), encounters.size());
        for (RegionThreat threat : bound.threats()) {
            InhabitedWorld.LOGGER.info("[{}]   region {}: threat profile {}", InhabitedWorld.MOD_ID, threat.region().id(), threat.describe());
        }
        return bound;
    }

    /** Removes a level's threat state (a world without regions). */
    public static void clear(ServerLevel level) {
        ((LevelThreatsHolder) level).inhabitedWorld$setThreats(null);
    }

    private static RegionThreat bind(SemanticRegion region, WorldPlan plan, ThreatData data) {
        if (region.profile() != SpawnProfile.POI_PLACEHOLDER) {
            return RegionThreat.builtIn(region);
        }
        PlannedSite site = region.site().map(plan::site).orElse(null);
        if (site == null) {
            InhabitedWorld.LOGGER.warn("[{}] POI region {} has no planned site; it keeps vanilla-like spawning", InhabitedWorld.MOD_ID, region.id());
            return RegionThreat.builtIn(region);
        }
        PoiThreatBinding binding = data.poiBindings().get(site.catalogId());
        ThreatProfile profile = binding == null ? null : data.profiles().get(binding.threatProfile());
        if (profile == null) {
            InhabitedWorld.LOGGER.warn("[{}] POI {} (catalog {}) has no threat profile in the loaded data ({}); it keeps vanilla-like spawning",
                    InhabitedWorld.MOD_ID, site.id(), site.catalogId(), binding == null ? "no poi_threats entry" : "profile " + binding.threatProfile() + " missing");
            return RegionThreat.builtIn(region);
        }
        return new RegionThreat(region, profile, Optional.of(site.catalogId()));
    }

    private void tick(ServerLevel level) {
        if (level.getGameTime() % COUNT_TICKS == 0) {
            for (RegionThreat threat : this.index.threats()) {
                if (threat.profile().hasLocalCap()) {
                    threat.setPopulation(countAmbientHostiles(level, threat.region().volume()));
                }
            }
        }
        for (Hotspot hotspot : this.hotspots) {
            hotspot.tick(level);
        }
        this.encounters.tick(level);
    }

    /** Living hostiles inside a region that ambient spawning made or could have made (not persistence-required). */
    public static int countAmbientHostiles(ServerLevel level, RegionVolume v) {
        AABB box = new AABB(v.minX(), v.minY(), v.minZ(), v.maxX() + 1, v.maxY() + 1, v.maxZ() + 1);
        return level.getEntities((Entity) null, box, e -> e instanceof Mob mob && mob.isAlive() && mob.getType().getCategory() == MobCategory.MONSTER
                && !mob.isPersistenceRequired() && !mob.requiresCustomPersistence()).size();
    }

    public RegionIndex index() {
        return this.index;
    }

    public List<Hotspot> hotspots() {
        return this.hotspots;
    }

    public EncounterRuntime encounters() {
        return this.encounters;
    }
}
