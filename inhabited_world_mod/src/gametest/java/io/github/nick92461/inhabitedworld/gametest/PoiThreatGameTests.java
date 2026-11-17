package io.github.nick92461.inhabitedworld.gametest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import io.github.nick92461.inhabitedworld.encounter.EncounterData;
import io.github.nick92461.inhabitedworld.encounter.EncounterDefinition;
import io.github.nick92461.inhabitedworld.encounter.EncounterRecord;
import io.github.nick92461.inhabitedworld.encounter.EncounterRuntime;
import io.github.nick92461.inhabitedworld.encounter.Encounters;
import io.github.nick92461.inhabitedworld.region.RegionIndex;
import io.github.nick92461.inhabitedworld.region.RegionKind;
import io.github.nick92461.inhabitedworld.region.RegionVolume;
import io.github.nick92461.inhabitedworld.region.Regions;
import io.github.nick92461.inhabitedworld.region.SemanticRegion;
import io.github.nick92461.inhabitedworld.spawn.SpawnGovernance;
import io.github.nick92461.inhabitedworld.spawn.SpawnPolicy;
import io.github.nick92461.inhabitedworld.threat.Hotspot;
import io.github.nick92461.inhabitedworld.threat.HotspotDefinition;
import io.github.nick92461.inhabitedworld.threat.LevelThreats;
import io.github.nick92461.inhabitedworld.threat.RegionThreat;
import io.github.nick92461.inhabitedworld.threat.SiteAnchor;
import io.github.nick92461.inhabitedworld.threat.ThreatProfile;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;
import io.github.nick92461.inhabitedworld.worldgen.planner.V01Sites;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlan;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlans;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
  Phase 3 part 2 in the real Inhabited World this game-test run creates (canon 06 sections 17.3, 17.7, 17.8; 09 DoD
  16 to 19):

  - the POI's threat profile is found from its catalog entry and installed on the level
  - vanilla's own natural spawner, in identical buried one-chunk chambers: the POI spawns only its profile's three
    hostiles, also in a lamp-lit chamber where the wilderness spawns nothing; the wilderness keeps its own mix; the
    local cap stops the POI (the town's zero is RegionGameTests')
  - a controlled hotspot fills to its target, stops at its cap when its mobs leave its box, and idles without a player
  - a guaranteed encounter spawns once on approach, survives a save/load of its store and a chunk unload/reload
    without duplicating, is defeated by /kill even as its chunk unloads, and stays defeated after another reload
  - the shipped placeholder foreman spawns at the POI, is reachable through the query API, and is brought back when
    it leaves the POI region

  Chambers: 16 x 3 x 16 air in a stone shell (as in RegionGameTests), buried 12 blocks under the surface so they are
  dark underground spaces: two in the POI region (dark, and lit to block light 2-5 by light blocks in the ceiling), two
  as far into the wilderness, the same way. Built once per run and shared.
 */
public class PoiThreatGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger("inhabited_world/gametest");
    private static final int SPAWN_ATTEMPTS = 300;
    private static final int BURY = 12;
    private static final int POI_OFFSET = 40;
    private static final int WILD_GAP = 48;
    private static final Identifier MINE_PROFILE = Identifier.fromNamespaceAndPath("inhabited_world", "placeholder_abandoned_mine");
    private static final Identifier FOREMAN = Identifier.fromNamespaceAndPath("inhabited_world", "placeholder_mine_foreman");
    private static final Identifier TEST_ENCOUNTER = Identifier.fromNamespaceAndPath("inhabited_world_gametest", "test_foreman");

    private record Chamber(String name, ChunkPos chunk, int floorY, boolean lit) {
        BlockPos center() {
            return new BlockPos(this.chunk.getMiddleBlockX(), this.floorY, this.chunk.getMiddleBlockZ());
        }
    }

    private record Arena(ServerLevel level, Chamber poiDark, Chamber poiLit, Chamber wildDark, Chamber wildLit, BlockPos poiPlayer, BlockPos wildPlayer) {
        boolean settled() {
            for (Chamber c : List.of(this.poiDark, this.poiLit, this.wildDark, this.wildLit)) {
                for (BlockPos p : List.of(c.center(), c.center().offset(-8, 0, -8), c.center().offset(7, 0, 7), c.center().offset(-8, 0, 7),
                        c.center().offset(-7, 0, -7), c.center().offset(5, 0, -4))) {
                    int block = this.level.getBrightness(LightLayer.BLOCK, p);
                    if (this.level.getBrightness(LightLayer.SKY, p) != 0 || (c.lit() ? block < 1 || block > 7 : block != 0)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private static Arena arena;

    private static WorldPlan plan(GameTestHelper helper) {
        WorldPlan plan = WorldPlans.get(helper.getLevel().getServer());
        if (plan == null) {
            throw helper.assertionException("a new Inhabited World must have a WorldPlan");
        }
        return plan;
    }

    private static PlannedSite poi(GameTestHelper helper) {
        PlannedSite poi = plan(helper).site(V01Sites.EXTERNAL_POI);
        if (poi == null) {
            throw helper.assertionException("the plan has an external POI");
        }
        return poi;
    }

    private static LevelThreats threats(GameTestHelper helper) {
        LevelThreats threats = LevelThreats.of(helper.getLevel());
        if (threats == null) {
            throw helper.assertionException("the overworld has its threat state installed");
        }
        return threats;
    }

    private static RegionThreat poiThreat(GameTestHelper helper) {
        PlannedSite poi = poi(helper);
        return SpawnGovernance.threatAt(helper.getLevel(), poi.centerX(), poi.predicted().medianY(), poi.centerZ());
    }

    private static synchronized Arena arena(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (arena != null && arena.level() == level) {
            return arena;
        }
        WorldPlan plan = plan(helper);
        PlannedSite poi = poi(helper);
        RegionIndex index = Regions.index(level);
        if (index == null) {
            throw helper.assertionException("the region index is installed");
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
        }
        int cx = poi.centerX();
        int cz = poi.centerZ();
        Chamber poiDark = chamber(level, "poi dark", new BlockPos(cx - POI_OFFSET, 0, cz - POI_OFFSET), false);
        Chamber poiLit = chamber(level, "poi lit", new BlockPos(cx + POI_OFFSET, 0, cz - POI_OFFSET), true);
        for (Chamber c : List.of(poiDark, poiLit)) {
            if (index.regionAt(c.center().getX(), c.floorY() + 2, c.center().getZ()).kind() != RegionKind.POI
                    || index.regionAt(c.center().getX(), c.floorY(), c.center().getZ()).kind() != RegionKind.POI) {
                throw helper.assertionException("the " + c.name() + " chamber must lie in the POI region");
            }
        }
        // Wilderness chambers: beyond the POI region's edge on a side that is wilderness with a monster spawn list.
        RegionVolume region = poiThreat(helper).region().volume();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int edgeX = side == Direction.WEST ? region.minX() : side == Direction.EAST ? region.maxX() : cx;
            int edgeZ = side == Direction.NORTH ? region.minZ() : side == Direction.SOUTH ? region.maxZ() : cz;
            Direction along = side.getClockWise();
            BlockPos a = new BlockPos(edgeX + side.getStepX() * WILD_GAP - along.getStepX() * 20, 0, edgeZ + side.getStepZ() * WILD_GAP - along.getStepZ() * 20);
            BlockPos b = new BlockPos(edgeX + side.getStepX() * WILD_GAP + along.getStepX() * 20, 0, edgeZ + side.getStepZ() * WILD_GAP + along.getStepZ() * 20);
            if (!plan.playableArea().contains(a.getX(), a.getZ()) || !plan.playableArea().contains(b.getX(), b.getZ())) {
                continue;
            }
            Chamber wildDark = chamber(level, "wilderness dark", a, false);
            Chamber wildLit = chamber(level, "wilderness lit", b, true);
            boolean usable = true;
            for (Chamber c : List.of(wildDark, wildLit)) {
                usable &= index.regionAt(c.center().getX(), c.floorY(), c.center().getZ()).isWilderness() && hasMonsters(level, c.center());
            }
            if (!usable) {
                continue;
            }
            for (Chamber c : List.of(poiDark, poiLit, wildDark, wildLit)) {
                build(level, c);
            }
            // Players 40 blocks from their chambers (vanilla spawns 24 to 128 blocks from a player), out of the hotspot's and
            // the foreman's reach (the POI's centre).
            BlockPos poiPlayer = new BlockPos(cx, poiDark.floorY() + 1, cz - POI_OFFSET);
            BlockPos wildPlayer = new BlockPos((a.getX() + b.getX()) / 2 + side.getStepX() * 30, wildDark.floorY() + 1, (a.getZ() + b.getZ()) / 2 + side.getStepZ() * 30);
            arena = new Arena(level, poiDark, poiLit, wildDark, wildLit, poiPlayer, wildPlayer);
            LOGGER.info("POI threat arena: POI chambers {} (dark) and {} (lit), wilderness chambers {} (dark) and {} (lit) on the POI's {} side",
                    poiDark.center().toShortString(), poiLit.center().toShortString(), wildDark.center().toShortString(), wildLit.center().toShortString(),
                    side.getName());
            return arena;
        }
        throw helper.assertionException("no side of the POI has usable wilderness for the arena");
    }

    private static Chamber chamber(ServerLevel level, String name, BlockPos column, boolean lit) {
        ChunkPos chunk = ChunkPos.containing(column);
        level.getChunk(chunk.x(), chunk.z());
        int lowest = Integer.MAX_VALUE;
        for (int x = chunk.getMinBlockX() - 1; x <= chunk.getMaxBlockX() + 1; x += 3) {
            for (int z = chunk.getMinBlockZ() - 1; z <= chunk.getMaxBlockZ() + 1; z += 3) {
                lowest = Math.min(lowest, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z));
            }
        }
        return new Chamber(name, chunk, Math.max(lowest - BURY, level.getMinY() + 8), lit);
    }

    private static boolean hasMonsters(ServerLevel level, BlockPos pos) {
        return !level.environmentAttributes().getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, pos).getMobsToSpawn(MobCategory.MONSTER).isEmpty();
    }

    // Stone from one block outside the chunk to one inside it, a 16 x 3 x 16 air room within; lit rooms get level-7 light
    // blocks in the ceiling every 3 blocks from the walls (at most 1 block off on each axis, so block light 2 to 4 on the
    // floor). The chunk stays loaded.
    private static void build(ServerLevel level, Chamber c) {
        int x0 = c.chunk().getMinBlockX();
        int z0 = c.chunk().getMinBlockZ();
        for (int x = x0 - 1; x <= x0 + 16; x++) {
            for (int z = z0 - 1; z <= z0 + 16; z++) {
                for (int y = c.floorY() - 1; y <= c.floorY() + 4; y++) {
                    boolean room = x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16 && y >= c.floorY() && y < c.floorY() + 3;
                    boolean lamp = c.lit() && y == c.floorY() + 3 && (x - x0) % 3 == 0 && (z - z0) % 3 == 0;
                    level.setBlockAndUpdate(new BlockPos(x, y, z), room ? Blocks.AIR.defaultBlockState()
                            : lamp ? Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 7) : Blocks.STONE.defaultBlockState());
                }
            }
        }
        level.setChunkForced(c.chunk().x(), c.chunk().z(), true);
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    private static void removePlayer(MinecraftServer server, ServerPlayer player) {
        server.getPlayerList().remove(player);
    }

    // vanilla's own natural spawner, SPAWN_ATTEMPTS times for the monster category from random spots in a chamber
    private static Map<String, Integer> naturalMonsterSpawns(ServerLevel level, Chamber chamber) {
        LevelChunk chunk = level.getChunk(chamber.chunk().x(), chamber.chunk().z());
        Map<String, Integer> types = new TreeMap<>();
        List<Mob> spawned = new ArrayList<>();
        for (int k = 0; k < SPAWN_ATTEMPTS; k++) {
            BlockPos start = new BlockPos(chamber.chunk().getMinBlockX() + level.getRandom().nextInt(16), chamber.floorY(),
                    chamber.chunk().getMinBlockZ() + level.getRandom().nextInt(16));
            NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER, level, chunk, start, (type, l, pos, c) -> true, (mob, c) -> spawned.add(mob));
            for (Mob mob : spawned) {
                types.merge(EntityType.getKey(mob.getType()).getPath(), 1, Integer::sum);
                mob.discard();
            }
            spawned.clear();
        }
        return types;
    }

    private static int total(Map<String, Integer> types) {
        return types.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static List<Mob> tagged(ServerLevel level, String tag) {
        return new ArrayList<>(level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.isAlive() && m.entityTags().contains(tag)));
    }

    private static List<Mob> taggedIncludingDying(ServerLevel level, String tag) {
        return new ArrayList<>(level.getEntities(EntityTypeTest.forClass(Mob.class), m -> !m.isRemoved() && m.entityTags().contains(tag)));
    }

    // ---- The profile, found from the POI's identity (09 DoD 16) ----

    @GameTest
    public void thePoiThreatProfileComesFromItsCatalogEntry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlannedSite poi = poi(helper);
        RegionThreat threat = poiThreat(helper);
        helper.assertTrue(threat.region().kind() == RegionKind.POI, "the POI's centre is in its region");
        helper.assertTrue(threat.profile().id().equals(MINE_PROFILE), "the POI's profile is the abandoned mine's, got " + threat.profile().id());
        helper.assertTrue(threat.catalogId().equals(Optional.of(poi.catalogId())), "chosen by its catalog entry " + poi.catalogId());
        helper.assertTrue(threat.profile().lists(EntityTypes.CAVE_SPIDER) && !threat.profile().lists(EntityTypes.CREEPER), "its own hostiles");
        PlannedSite town = plan(helper).site(V01Sites.TOWN);
        helper.assertTrue(SpawnGovernance.threatAt(level, town.centerX(), 100, town.centerZ()).profile() == ThreatProfile.PROTECTED_SETTLEMENT,
                "the town keeps the protected-settlement profile");
        LevelThreats threats = threats(helper);
        helper.assertTrue(threats.hotspots().size() == 1 && threats.hotspots().getFirst().region() == threat, "the mine's hotspot is set up");
        helper.assertTrue(Encounters.ids(level.getServer()).contains(FOREMAN), "the POI holds the foreman encounter");
        LOGGER.info("POI threat binding: {}", threat.describe());
        helper.succeed();
    }

    // ---- Natural spawning by profile (06 section 17.3, 09 DoD 16) ----

    @GameTest(maxTicks = 600)
    public void thePoiSpawnsOnlyItsProfileHostilesAndAlsoWhereTheWildernessWouldNot(GameTestHelper helper) {
        Arena a = arena(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(a.settled(), "the chambers' light has settled (dark ones 0, lit ones 1 to 7)");
            ServerLevel level = helper.getLevel();
            RegionThreat poi = poiThreat(helper);
            ServerPlayer player = player(helper, a.poiPlayer());
            try {
                poi.setPopulation(0); // the live count is refreshed once a second; this check runs within one tick
                Map<String, Integer> poiDark = naturalMonsterSpawns(level, a.poiDark());
                Map<String, Integer> poiLit = naturalMonsterSpawns(level, a.poiLit());
                poi.setPopulation(poi.profile().localCap());
                Map<String, Integer> poiAtCap = naturalMonsterSpawns(level, a.poiDark());
                poi.setPopulation(0);
                player.snapTo(a.wildPlayer().getX() + 0.5, a.wildPlayer().getY(), a.wildPlayer().getZ() + 0.5);
                Map<String, Integer> wildDark = naturalMonsterSpawns(level, a.wildDark());
                Map<String, Integer> wildLit = naturalMonsterSpawns(level, a.wildLit());
                LOGGER.info("natural monster spawns from {} attempts each, buried chambers: POI dark {} {}, POI lit {} {}, POI at its local cap {}, "
                                + "wilderness dark {} {}, wilderness lit {} {}", SPAWN_ATTEMPTS, total(poiDark), poiDark, total(poiLit), poiLit, total(poiAtCap),
                        total(wildDark), wildDark, total(wildLit), wildLit);
                List<String> own = List.of("zombie", "skeleton", "cave_spider");
                helper.assertTrue(total(poiDark) > 0, "the POI's dark chamber spawns hostiles");
                helper.assertTrue(own.containsAll(poiDark.keySet()) && own.containsAll(poiLit.keySet()),
                        "the POI spawns only zombies, skeletons and cave spiders, got " + poiDark + " and " + poiLit);
                helper.assertTrue(poiDark.getOrDefault("cave_spider", 0) + poiLit.getOrDefault("cave_spider", 0) > 0,
                        "cave spiders, which no biome spawns, appear by the profile's list");
                helper.assertTrue(total(poiLit) > 0, "the POI spawns its hostiles in a lamp-lit tunnel (block light up to 7)");
                helper.assertTrue(total(wildLit) == 0, "the wilderness spawns nothing with block light, but " + wildLit + " spawned");
                helper.assertTrue(total(wildDark) > 0, "the wilderness keeps natural hostile spawning");
                helper.assertTrue(!own.containsAll(wildDark.keySet()), "the wilderness keeps its own mix of hostiles, got " + wildDark);
                helper.assertTrue(total(poiAtCap) == 0, "at its local cap the POI spawns no more, but " + poiAtCap + " spawned");
            } finally {
                removePlayer(level.getServer(), player);
            }
        });
    }

    // The decision names the profile's reasons.
    @GameTest(maxTicks = 600)
    public void spawnDecisionsInThePoiNameTheProfile(GameTestHelper helper) {
        Arena a = arena(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(a.settled(), "the chambers' light has settled");
            ServerLevel level = helper.getLevel();
            poiThreat(helper).setPopulation(0);
            BlockPos lit = a.poiLit().center();
            helper.assertTrue(SpawnGovernance.decide(EntityTypes.CREEPER, level, EntitySpawnReason.NATURAL, lit) == SpawnPolicy.Decision.REFUSE_UNLISTED_HOSTILE,
                    "a creeper is not the mine's");
            helper.assertTrue(SpawnGovernance.decide(EntityTypes.ZOMBIE, level, EntitySpawnReason.NATURAL, lit) == SpawnPolicy.Decision.ALLOW_BY_PROFILE,
                    "a zombie is the mine's");
            helper.assertTrue(net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(EntityTypes.CAVE_SPIDER, level, EntitySpawnReason.NATURAL, lit, level.getRandom()),
                    "the full check passes for a cave spider in the lit tunnel");
            helper.assertFalse(net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(EntityTypes.ZOMBIE, level, EntitySpawnReason.NATURAL, a.wildLit().center(),
                    level.getRandom()), "and fails for a zombie in the lit wilderness chamber");
            helper.assertTrue(SpawnGovernance.decide(EntityTypes.CREEPER, level, EntitySpawnReason.COMMAND, lit).allowed(), "/summon still works in the POI");
        });
    }

    // ---- The hotspot (06 section 17.7, 09 DoD 17) ----

    /*
      A hotspot like the mine's, anchored in its own buried chamber in the POI: run round after round, it fills its box to
      its target and stops; mobs that leave the box are replaced until its cap of own mobs; then it stops even though the
      box holds fewer than the target; and without a player within its activation radius it does nothing.
     */
    @GameTest(maxTicks = 600)
    public void theHotspotFillsToItsTargetAndStopsAtItsCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlannedSite poi = poi(helper);
        RegionThreat threat = poiThreat(helper);
        Chamber room = chamber(level, "hotspot", new BlockPos(poi.centerX(), 0, poi.centerZ() + POI_OFFSET), false);
        build(level, room);
        // A sealed cell beside the room, outside the hotspot's box, where mobs are "taken out" of the box.
        BlockPos cell = new BlockPos(room.chunk().getMinBlockX() - 4, room.floorY(), room.chunk().getMiddleBlockZ());
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 3; y++) {
                    boolean air = Math.abs(x) <= 1 && Math.abs(z) <= 1 && y >= 0 && y <= 2;
                    level.setBlockAndUpdate(cell.offset(x, y, z), air ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState());
                }
            }
        }
        HotspotDefinition definition = new HotspotDefinition("gametest", SiteAnchor.CENTRE_SURFACE, 7, 1, 2, 4, 6, 1, 1, 40, 4);
        Hotspot hotspot = new Hotspot(threat, definition, SiteAnchor.fixed(room.center()));
        int[] stage = {0};
        ServerPlayer[] player = {null};
        helper.succeedWhen(() -> {
            BlockPos c = room.center();
            helper.assertTrue(level.getBrightness(LightLayer.SKY, c) == 0 && level.getBrightness(LightLayer.BLOCK, c) == 0, "the hotspot room is dark");
            if (player[0] == null) {
                player[0] = player(helper, c.offset(0, 0, 12)); // 12 blocks off, in the stone beside the room
            }
            String tag = hotspot.tag();
            AABB box = hotspot.box(c);
            // Mobs that live natural spawning put in the room since it was built are not the hotspot's: clear them.
            level.getEntities((Entity) null, box, e -> e instanceof Mob && !e.entityTags().contains(tag)).forEach(Entity::discard);
            int spawned = 0;
            for (int round = 0; round < 40; round++) {
                spawned += hotspot.run(level);
            }
            List<Mob> own = tagged(level, tag);
            long inBox = own.stream().filter(m -> box.contains(m.position())).count();
            helper.assertTrue(spawned == 4 && own.size() == 4 && inBox == 4, "it fills its box to its target of 4 and stops (spawned " + spawned
                    + ", own " + own.size() + ", in the box " + inBox + ")");
            for (Mob mob : own) {
                helper.assertTrue(threat.profile().lists(mob.getType()), "its mobs are the profile's: " + EntityType.getKey(mob.getType()));
                helper.assertFalse(mob.isPersistenceRequired(), "hotspot mobs are ordinary, despawnable hostiles");
            }
            // Two leave the box: it replaces them, up to its cap of 6 own mobs.
            own.get(0).teleportTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
            own.get(1).teleportTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
            int refill = 0;
            for (int round = 0; round < 40; round++) {
                refill += hotspot.run(level);
            }
            helper.assertTrue(refill == 2 && tagged(level, tag).size() == 6, "it replaces the two that left, up to its cap (" + refill + ")");
            // Two more leave: the box holds 2 of its target 4, but it has 6 own mobs alive, its cap: it stops.
            List<Mob> now = tagged(level, tag).stream().filter(m -> box.contains(m.position())).toList();
            now.get(0).teleportTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
            now.get(1).teleportTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
            int beyondCap = 0;
            for (int round = 0; round < 40; round++) {
                beyondCap += hotspot.run(level);
            }
            helper.assertTrue(beyondCap == 0 && tagged(level, tag).size() == 6, "it never exceeds its cap of own mobs (" + beyondCap + " more)");
            // Without a player near, nothing happens, though one of its mobs dies.
            tagged(level, tag).getFirst().discard();
            player[0].snapTo(c.getX() + 0.5, c.getY() + 60, c.getZ() + 100.5);
            int idle = 0;
            for (int round = 0; round < 20; round++) {
                idle += hotspot.run(level);
            }
            helper.assertTrue(idle == 0, "a hotspot with no player within its activation radius spawns nothing (" + idle + ")");
            LOGGER.info("hotspot: filled to target 4, refilled 2 to its cap of 6, then {} beyond the cap and {} without a player", beyondCap, idle);
            tagged(level, tag).forEach(Entity::discard);
            removePlayer(level.getServer(), player[0]);
            level.setChunkForced(room.chunk().x(), room.chunk().z(), false);
        });
    }

    // ---- Guaranteed encounters (06 sections 17.7, 17.8; 09 DoD 18, 19) ----

    private static Path encounterFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATA).resolve("inhabited_world").resolve("encounters.dat");
    }

    // What a reload would read: save the store, decode its file, and make the decoded store the live one.
    private static EncounterData reloadStore(GameTestHelper helper, MinecraftServer server) {
        server.getDataStorage().saveAndJoin();
        try {
            CompoundTag file = NbtIo.readCompressed(encounterFile(server), NbtAccounter.unlimitedHeap());
            EncounterData loaded = EncounterData.CODEC.parse(NbtOps.INSTANCE, file.get("data")).getOrThrow(e -> helper.assertionException("the saved store decodes: " + e));
            server.getDataStorage().set(EncounterData.TYPE, loaded);
            return loaded;
        } catch (IOException e) {
            throw helper.assertionException("the encounter store is saved at " + encounterFile(server) + ": " + e);
        }
    }

    /*
      A test encounter in a sealed room far from the other tests' players (so its chunk can unload): it spawns once when a
      player comes near; it stays one mob through a save/load of its store and an unload/reload of its chunk; /kill
      defeats it at once, even as its chunk unloads straight after; and after another reload, with a player standing next
      to its anchor, it stays defeated and nothing respawns.
     */
    @GameTest(maxTicks = 4800)
    public void aGuaranteedEncounterSpawnsOnceAndKeepsItsStateThroughReloads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        WorldPlan plan = plan(helper);
        PlannedSite poi = poi(helper);
        PlannedSite town = plan.site(V01Sites.TOWN);
        // A spot about 250 blocks from the POI and from the town.
        BlockPos spot = null;
        for (int angle = 0; angle < 360 && spot == null; angle += 15) {
            int x = poi.centerX() + (int) Math.round(250 * Math.cos(Math.toRadians(angle)));
            int z = poi.centerZ() + (int) Math.round(250 * Math.sin(Math.toRadians(angle)));
            boolean inside = x > plan.playableArea().minBlockX() + 48 && x < plan.playableArea().maxBlockX() - 48
                    && z > plan.playableArea().minBlockZ() + 48 && z < plan.playableArea().maxBlockZ() - 48;
            double toTown = Math.max(Math.max(town.region().minX() - x, x - town.region().maxX()), Math.max(town.region().minZ() - z, z - town.region().maxZ()));
            if (inside && toTown > 200) {
                spot = new BlockPos(x, 0, z);
            }
        }
        if (spot == null) {
            throw helper.assertionException("no spot 250 blocks from the POI and 200 from the town");
        }
        Chamber room = chamber(level, "encounter", spot, false);
        build(level, room);
        BlockPos anchor = room.center();
        long chunkKey = room.chunk().pack();
        LevelThreats threats = threats(helper);
        EncounterDefinition definition = new EncounterDefinition(EntityTypes.ZOMBIE, Optional.of("Test foreman"), Map.of(EquipmentSlot.MAINHAND, Items.IRON_PICKAXE),
                SiteAnchor.CENTRE_SURFACE, 16, 4, false);
        EncounterRuntime.Bound bound = new EncounterRuntime.Bound(TEST_ENCOUNTER, definition, SiteAnchor.fixed(anchor), null);
        threats.encounters().add(bound);
        String tag = bound.tag();
        UUID[] mob = {null};
        ServerPlayer[] player = {player(helper, anchor.offset(0, 0, 10))};
        helper.startSequence()
                // Spawned once, lazily, because a player came within its trigger radius.
                .thenWaitUntil(() -> {
                    EncounterRecord r = Encounters.record(server, TEST_ENCOUNTER).orElseThrow();
                    helper.assertTrue(r.state() == EncounterRecord.State.ACTIVE, "the encounter spawns when a player is near, state " + r.state());
                    mob[0] = r.entity().orElseThrow();
                })
                .thenExecuteAfter(70, () -> {
                    List<Mob> mobs = tagged(level, tag);
                    helper.assertTrue(mobs.size() == 1 && mobs.getFirst().getUUID().equals(mob[0]), "exactly one mob after several checks: " + mobs.size());
                    Mob m = mobs.getFirst();
                    helper.assertTrue(m.isPersistenceRequired(), "persistence required");
                    helper.assertTrue(m.getCustomName() != null && m.getCustomName().getString().equals("Test foreman"), "named");
                    helper.assertTrue(m.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_PICKAXE), "equipped");
                    helper.assertTrue(m.entityTags().contains(EncounterRuntime.TAG) && m.entityTags().contains(tag), "tagged with its encounter");
                    helper.assertTrue(Encounters.entity(server, TEST_ENCOUNTER).map(Mob::getUUID).equals(Optional.of(mob[0])), "the query API finds the mob");
                    // Save/load: the file says active with this mob; the reloaded store keeps the one mob.
                    EncounterData loaded = reloadStore(helper, server);
                    helper.assertTrue(loaded.get(TEST_ENCOUNTER).is(mob[0]) && loaded.get(TEST_ENCOUNTER).isActive(), "the saved file: active, this mob");
                    helper.assertTrue(plan.worldId().equals(loaded.worldId()), "the store belongs to this world's ID");
                })
                .thenExecuteAfter(70, () -> {
                    helper.assertTrue(tagged(level, tag).size() == 1, "still one mob after the store reload: " + tagged(level, tag).size());
                    helper.assertTrue(Encounters.record(server, TEST_ENCOUNTER).orElseThrow().spawns() == 1, "spawned once");
                    // Chunk unload: nobody near, chunk no longer forced.
                    removePlayer(server, player[0]);
                    level.setChunkForced(room.chunk().x(), room.chunk().z(), false);
                })
                .thenWaitUntil(() -> helper.assertTrue(level.getEntity(mob[0]) == null && !level.areEntitiesLoaded(chunkKey),
                        "the encounter's chunk unloads with its mob"))
                .thenExecute(() -> {
                    EncounterRecord r = Encounters.record(server, TEST_ENCOUNTER).orElseThrow();
                    helper.assertTrue(r.is(mob[0]) && r.isActive(), "unloaded is not lost: still active with its mob, " + r.describe());
                    // Reload the chunk with a player at the anchor (a not-spawned encounter would spawn now).
                    player[0] = player(helper, anchor.offset(0, 0, 10));
                    level.setChunkForced(room.chunk().x(), room.chunk().z(), true);
                })
                .thenWaitUntil(() -> helper.assertTrue(level.getEntity(mob[0]) != null, "the mob loads back with its chunk"))
                .thenExecuteAfter(70, () -> {
                    List<Mob> mobs = tagged(level, tag);
                    EncounterRecord r = Encounters.record(server, TEST_ENCOUNTER).orElseThrow();
                    helper.assertTrue(mobs.size() == 1 && mobs.getFirst().getUUID().equals(mob[0]), "the same single mob after the chunk reload: " + mobs.size());
                    helper.assertTrue(r.spawns() == 1 && r.is(mob[0]), "reconciled, not respawned: " + r.describe());
                    // /kill defeats it at once, while the dying mob is still in the world; its chunk unloads right after.
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "kill @e[tag=" + tag + "]");
                    EncounterRecord killed = Encounters.record(server, TEST_ENCOUNTER).orElseThrow();
                    helper.assertTrue(killed.state() == EncounterRecord.State.DEFEATED, "killed by a command: defeated at once, " + killed.describe());
                    helper.assertTrue(killed.defeatedBy().equals(Optional.of("minecraft:generic_kill")), "the cause is recorded: " + killed.defeatedBy());
                    helper.assertTrue(!taggedIncludingDying(level, tag).isEmpty(), "the mob is still dying in the world when the defeat is recorded");
                    removePlayer(server, player[0]);
                    level.setChunkForced(room.chunk().x(), room.chunk().z(), false);
                })
                .thenWaitUntil(() -> helper.assertTrue(!level.areEntitiesLoaded(chunkKey), "the chunk unloads with the dying mob"))
                .thenExecute(() -> {
                    EncounterData loaded = reloadStore(helper, server);
                    helper.assertTrue(loaded.get(TEST_ENCOUNTER).state() == EncounterRecord.State.DEFEATED, "the saved file: defeated");
                    player[0] = player(helper, anchor.offset(0, 0, 3));
                    level.setChunkForced(room.chunk().x(), room.chunk().z(), true);
                })
                .thenWaitUntil(() -> helper.assertTrue(level.areEntitiesLoaded(chunkKey), "the chunk loads back"))
                .thenExecuteAfter(120, () -> {
                    EncounterRecord r = Encounters.record(server, TEST_ENCOUNTER).orElseThrow();
                    helper.assertTrue(r.state() == EncounterRecord.State.DEFEATED && r.spawns() == 1, "it stays defeated after the reload: " + r.describe());
                    helper.assertTrue(tagged(level, tag).isEmpty(), "no living mob of a defeated encounter, a player 3 blocks from its anchor");
                    LOGGER.info("test encounter: {}", r.describe());
                    threats.encounters().remove(TEST_ENCOUNTER);
                    removePlayer(server, player[0]);
                    level.setChunkForced(room.chunk().x(), room.chunk().z(), false);
                })
                .thenSucceed();
    }

    /*
      The shipped placeholder foreman: a player approaching the POI's centre makes it appear, as defined; the query API
      reports it; led out of the POI region with nothing to fight, it is brought back to its spot. Reset at the end (so it
      is not spawned for later runs of other tests).
     */
    @GameTest(maxTicks = 1200)
    public void theMinesForemanAppearsWhenAPlayerComesNear(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        PlannedSite poi = poi(helper);
        LevelThreats threats = threats(helper);
        EncounterRuntime.Bound bound = threats.encounters().get(FOREMAN);
        if (bound == null) {
            throw helper.assertionException("the foreman encounter is bound to the POI");
        }
        EncounterData data = Encounters.store(server);
        threats.encounters().reset(level, data, bound);
        level.getChunk(poi.centerX() >> 4, poi.centerZ() >> 4);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, poi.centerX(), poi.centerZ());
        ServerPlayer[] player = {player(helper, new BlockPos(poi.centerX() + 12, surface + 1, poi.centerZ()))};
        // A mock player reports creative but is a survival player underneath: make it creative, so the foreman has
        // nobody to fight (a zombie chasing a player may leave its region; it comes back once it has no target).
        player[0].setGameMode(GameType.CREATIVE);
        SemanticRegion region = poiThreat(helper).region();
        Mob[] foreman = {null};
        ChunkPos[] outChunk = {null};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(Encounters.state(server, FOREMAN).equals(Optional.of(EncounterRecord.State.ACTIVE)), "the foreman appears");
                    foreman[0] = Encounters.entity(server, FOREMAN).orElseThrow(() -> helper.assertionException("the query API has the foreman"));
                })
                .thenExecute(() -> {
                    Mob m = foreman[0];
                    helper.assertTrue(m.getType() == EntityTypes.ZOMBIE && m.getCustomName() != null && m.getCustomName().getString().equals("Foreman (placeholder)"),
                            "the placeholder foreman, named");
                    helper.assertTrue(m.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && m.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_PICKAXE),
                            "with its equipment");
                    helper.assertTrue(m.isPersistenceRequired() && m.hasHome(), "persistent and kept near its spot");
                    BlockPos p = m.blockPosition();
                    helper.assertTrue(region.volume().contains(p.getX(), p.getY(), p.getZ()), "it appears in the POI region");
                    helper.assertTrue(Math.abs(p.getX() - poi.centerX()) <= 8 && Math.abs(p.getZ() - poi.centerZ()) <= 8, "near the POI's centre, at " + p.toShortString());
                    // Lead it out of the region (12 blocks past the edge, onto the ground there), nothing to fight. Mock players
                    // don't carry chunk tickets as they move, so the chunk it is led into is force-loaded (else the mob would be
                    // stored into a chunk that then unloads: unloaded, not lost, and the record would rightly wait).
                    outChunk[0] = new ChunkPos((region.volume().maxX() + 12) >> 4, p.getZ() >> 4);
                    level.setChunkForced(outChunk[0].x(), outChunk[0].z(), true);
                })
                .thenWaitUntil(() -> helper.assertTrue(level.areEntitiesLoaded(outChunk[0].pack()), "the chunk outside the region is ready"))
                .thenExecute(() -> {
                    Mob m = foreman[0];
                    BlockPos p = m.blockPosition();
                    int outX = region.volume().maxX() + 12;
                    m.teleportTo(outX + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, outX, p.getZ()), p.getZ() + 0.5);
                    helper.assertFalse(region.volume().contains(m.blockPosition().getX(), m.blockPosition().getY(), m.blockPosition().getZ()), "led out");
                })
                .thenWaitUntil(() -> {
                    Mob m = Encounters.entity(server, FOREMAN).orElseThrow(() -> helper.assertionException("the foreman is loaded"));
                    BlockPos p = m.blockPosition();
                    helper.assertTrue(region.volume().contains(p.getX(), p.getY(), p.getZ()), "brought back into the POI region, at " + p.toShortString()
                            + " (target " + (m.getTarget() == null ? "none" : m.getTarget().getName().getString()) + ", home "
                            + (m.hasHome() ? m.getHomePosition().toShortString() : "none") + ", record "
                            + Encounters.record(server, FOREMAN).map(EncounterRecord::describe).orElse("?") + ")");
                    helper.assertTrue(m.getUUID().equals(foreman[0].getUUID()), "the same foreman");
                })
                .thenExecute(() -> {
                    helper.assertTrue(tagged(level, bound.tag()).size() == 1, "one foreman");
                    level.setChunkForced(outChunk[0].x(), outChunk[0].z(), false);
                    removePlayer(server, player[0]);
                    threats.encounters().reset(level, Encounters.store(server), bound);
                    helper.assertTrue(Encounters.state(server, FOREMAN).equals(Optional.of(EncounterRecord.State.NOT_SPAWNED)), "reset for the next approach");
                    LOGGER.info(String.format(Locale.ROOT, "foreman: spawned at the POI, returned after leaving its region; record %s",
                            Encounters.record(server, FOREMAN).map(EncounterRecord::describe).orElse("?")));
                })
                .thenSucceed();
    }
}
