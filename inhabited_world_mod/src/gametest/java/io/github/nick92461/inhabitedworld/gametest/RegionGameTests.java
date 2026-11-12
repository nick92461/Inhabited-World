package io.github.nick92461.inhabitedworld.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.nick92461.inhabitedworld.gametest.mixin.LevelSkyDarkenAccessor;
import io.github.nick92461.inhabitedworld.region.RegionIndex;
import io.github.nick92461.inhabitedworld.region.RegionIndexHolder;
import io.github.nick92461.inhabitedworld.region.RegionKind;
import io.github.nick92461.inhabitedworld.region.Regions;
import io.github.nick92461.inhabitedworld.region.SemanticRegion;
import io.github.nick92461.inhabitedworld.region.SiteRegions;
import io.github.nick92461.inhabitedworld.spawn.SpawnGovernance;
import io.github.nick92461.inhabitedworld.spawn.SpawnPolicy;
import io.github.nick92461.inhabitedworld.spawn.SpawnProfile;
import io.github.nick92461.inhabitedworld.worldgen.planner.BlockRect;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;
import io.github.nick92461.inhabitedworld.worldgen.planner.V01Sites;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlan;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlans;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.phys.AABB;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
  Phase 2B in the real Inhabited World this game-test run creates (canon 06 sections 13 and 17.1 to 17.6, 09 Phase 2B
  and DoD 10, 13 to 15): the plan's town and POI regions exist in 3D and are installed on the overworld; vanilla's
  own natural spawner produces no hostiles inside the town while the same setup in the wilderness does; deliberate
  spawns appear in town; a hostile from outside stays alive in town; vanilla pseudo-humans are refused in every region;
  phantoms never spawn over the town; and the cost of a decision on the live level.

  The arena: three closed stone chambers, each exactly one chunk (16 x 3 x 16 blocks of air, light 0, which is what
  night does to open ground), placed by the plan: inside the town 48 blocks in from its inland edge, in the wilderness
  48 blocks outside that edge, and in the middle of the POI. Built once per run and shared by the tests.
 */
public class RegionGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger("inhabited_world/gametest");
    private static final int INSET = 48;
    private static final int SPAWN_ATTEMPTS = 300;

    private record Chamber(String name, ChunkPos chunk, int floorY) {
        BlockPos center() {
            return new BlockPos(this.chunk.getMiddleBlockX(), this.floorY, this.chunk.getMiddleBlockZ());
        }
    }

    private record Arena(ServerLevel level, Chamber town, Chamber wilderness, Chamber poi, BlockPos playerSpot) {
        boolean dark() {
            for (Chamber chamber : List.of(this.town, this.wilderness, this.poi)) {
                BlockPos c = chamber.center();
                if (this.level.getBrightness(LightLayer.SKY, c) != 0 || this.level.getBrightness(LightLayer.BLOCK, c) != 0) {
                    return false;
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

    private static synchronized Arena arena(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (arena != null && arena.level() == level) {
            return arena;
        }
        WorldPlan plan = plan(helper);
        PlannedSite town = plan.site(V01Sites.TOWN);
        PlannedSite poi = plan.site(V01Sites.EXTERNAL_POI);
        RegionIndex index = Regions.index(level);
        if (town == null || poi == null || index == null) {
            throw helper.assertionException("the plan needs a town, a POI and an installed region index");
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
        }
        // An inland side: away from the waterfront first, then the others, until the wilderness spot is usable.
        List<Direction> sides = new ArrayList<>();
        town.waterfront().ifPresent(d -> sides.add(d.getOpposite()));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!sides.contains(d) && town.waterfront().map(w -> w != d).orElse(true)) {
                sides.add(d);
            }
        }
        BlockRect region = town.region();
        for (Direction side : sides) {
            int edgeX = side == Direction.WEST ? region.minX() : side == Direction.EAST ? region.maxX() : (int) region.centerX();
            int edgeZ = side == Direction.NORTH ? region.minZ() : side == Direction.SOUTH ? region.maxZ() : (int) region.centerZ();
            BlockPos inside = new BlockPos(edgeX - side.getStepX() * INSET, 0, edgeZ - side.getStepZ() * INSET);
            BlockPos outside = new BlockPos(edgeX + side.getStepX() * INSET, 0, edgeZ + side.getStepZ() * INSET);
            Chamber townChamber = chamber(level, "town", inside);
            Chamber wildChamber = chamber(level, "wilderness", outside);
            if (!plan.playableArea().contains(outside.getX(), outside.getZ())
                    || !index.regionAt(wildChamber.center().getX(), wildChamber.floorY(), wildChamber.center().getZ()).isWilderness()
                    || !hasMonsters(level, townChamber.center()) || !hasMonsters(level, wildChamber.center())) {
                continue;
            }
            Chamber poiChamber = chamber(level, "poi", new BlockPos(poi.centerX(), 0, poi.centerZ()));
            for (Chamber c : List.of(townChamber, wildChamber, poiChamber)) {
                build(level, c);
            }
            BlockPos player = new BlockPos(edgeX, (townChamber.floorY() + wildChamber.floorY()) / 2 + 1, edgeZ);
            arena = new Arena(level, townChamber, wildChamber, poiChamber, player);
            LOGGER.info("region arena on the town's {} side: town chamber {} (region {}), wilderness chamber {} (region {}), POI chamber {} (region {}), player at {}",
                    side.getName(), townChamber.center().toShortString(), regionAt(level, townChamber).id(), wildChamber.center().toShortString(),
                    regionAt(level, wildChamber).id(), poiChamber.center().toShortString(), regionAt(level, poiChamber).id(), player.toShortString());
            return arena;
        }
        throw helper.assertionException("no inland side of the town has usable wilderness for the arena");
    }

    private static Chamber chamber(ServerLevel level, String name, BlockPos column) {
        ChunkPos chunk = ChunkPos.containing(column);
        level.getChunk(chunk.x(), chunk.z());
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
        return new Chamber(name, chunk, Math.max(surface, level.getSeaLevel()) + 3);
    }

    private static SemanticRegion regionAt(ServerLevel level, Chamber chamber) {
        return Regions.at(level, chamber.center().getX(), chamber.floorY(), chamber.center().getZ());
    }

    private static boolean hasMonsters(ServerLevel level, BlockPos pos) {
        // 26.3 resolves the spawn lists as an environment attribute (biome-driven), as NaturalSpawner does.
        return !level.environmentAttributes().getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, pos).getMobsToSpawn(MobCategory.MONSTER).isEmpty();
    }

    // Stone from one block outside the chunk to one inside it, a 16 x 3 x 16 air room within; the chunk stays loaded.
    private static void build(ServerLevel level, Chamber c) {
        int x0 = c.chunk().getMinBlockX();
        int z0 = c.chunk().getMinBlockZ();
        for (int x = x0 - 1; x <= x0 + 16; x++) {
            for (int z = z0 - 1; z <= z0 + 16; z++) {
                for (int y = c.floorY() - 1; y <= c.floorY() + 3; y++) {
                    boolean room = x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16 && y >= c.floorY() && y < c.floorY() + 3;
                    level.setBlockAndUpdate(new BlockPos(x, y, z), room ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState());
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

    // vanilla's own natural spawner, run SPAWN_ATTEMPTS times for the monster category from random spots in a chamber
    private static int naturalMonsterSpawns(ServerLevel level, Chamber chamber) {
        LevelChunk chunk = level.getChunk(chamber.chunk().x(), chamber.chunk().z());
        List<Mob> spawned = new ArrayList<>();
        int total = 0;
        for (int k = 0; k < SPAWN_ATTEMPTS; k++) {
            BlockPos start = new BlockPos(chamber.chunk().getMinBlockX() + level.getRandom().nextInt(16), chamber.floorY(),
                    chamber.chunk().getMinBlockZ() + level.getRandom().nextInt(16));
            NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER, level, chunk, start, (type, l, pos, c) -> true, (mob, c) -> spawned.add(mob));
            total += spawned.size();
            spawned.forEach(Entity::discard);
            spawned.clear();
        }
        return total;
    }

    // ---- Regions ----

    // DoD 10: the town and the POI possess authoritative semantic regions, saved in the plan and installed on the level.
    @GameTest
    public void townAndPoiHaveSemanticRegions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorldPlan plan = plan(helper);
        SemanticRegion town = plan.region(V01Sites.TOWN);
        SemanticRegion poi = plan.region(V01Sites.EXTERNAL_POI);
        helper.assertTrue(town != null && town.kind() == RegionKind.SETTLEMENT && town.profile() == SpawnProfile.PROTECTED_SETTLEMENT,
                "the town is a protected settlement region");
        helper.assertTrue(poi != null && poi.kind() == RegionKind.POI && poi.profile() == SpawnProfile.POI_PLACEHOLDER, "the POI has its region");
        helper.assertTrue(town.volume().footprint().equals(plan.site(V01Sites.TOWN).region()), "the town's region stands on its semantic boundary");
        helper.assertTrue(SiteRegions.WORLD_MIN_Y == level.getMinY() && SiteRegions.WORLD_MAX_Y == level.getMaxY(),
                "the RPG overworld's build range is " + level.getMinY() + " to " + level.getMaxY());
        helper.assertTrue(town.volume().minY() > level.getMinY(), "the town's region stops above the deep caves (3D)");

        RegionIndex index = Regions.index(level);
        helper.assertTrue(index != null && index.regions().size() == plan.regions().size(), "the region index is installed on the overworld");
        PlannedSite site = plan.site(V01Sites.TOWN);
        helper.assertTrue(index.regionAt(site.centerX(), 100, site.centerZ()) == town, "the town's middle is the town");
        helper.assertTrue(index.regionAt(site.centerX(), town.volume().minY() - 1, site.centerZ()).isWilderness(), "below the town is wilderness");
        helper.assertTrue(index.regionAt(plan.playableArea().centerX(), 70, plan.playableArea().centerZ()).isWilderness()
                || index.regionAt(plan.playableArea().centerX(), 70, plan.playableArea().centerZ()) == town, "the map's middle is wilderness or town");
        LOGGER.info("game-test world regions: {}", String.join("; ", index.regions().stream().map(SemanticRegion::describe).toList()));
        helper.succeed();
    }

    // ---- Natural spawning (DoD 13 and 15) ----

    /*
      Vanilla's natural spawner, a player 48 to 60 blocks away: no hostile spawns inside the town, while the identical
      chamber in the wilderness does produce them. Negative control: with the region index briefly removed (in the same
      tick, so no other test sees it), the town chamber produces hostiles too, so the zero is the region rule's doing.
     */
    @GameTest(maxTicks = 400)
    public void noNaturalHostilesInTownWhileTheWildernessHasThem(GameTestHelper helper) {
        Arena a = arena(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(a.dark(), "the chambers are dark once light has settled");
            ServerLevel level = helper.getLevel();
            ServerPlayer player = player(helper, a.playerSpot());
            try {
                int town = naturalMonsterSpawns(level, a.town());
                int wilderness = naturalMonsterSpawns(level, a.wilderness());
                // Vanilla spawns only within 128 blocks of a player: stand 48 blocks from the POI chamber for its count.
                BlockPos nearPoi = a.poi().center().east(INSET);
                player.snapTo(nearPoi.getX() + 0.5, nearPoi.getY() + 1, nearPoi.getZ() + 0.5);
                int poi = naturalMonsterSpawns(level, a.poi());
                player.snapTo(a.playerSpot().getX() + 0.5, a.playerSpot().getY(), a.playerSpot().getZ() + 0.5);
                RegionIndexHolder holder = (RegionIndexHolder) level;
                RegionIndex index = holder.inhabitedWorld$regionIndex();
                int townWithoutRegions;
                try {
                    holder.inhabitedWorld$setRegionIndex(null);
                    townWithoutRegions = naturalMonsterSpawns(level, a.town());
                } finally {
                    holder.inhabitedWorld$setRegionIndex(index);
                }
                LOGGER.info("natural monster spawns from {} attempts each: town {}, wilderness {}, POI {}, town without regions (control) {}",
                        SPAWN_ATTEMPTS, town, wilderness, poi, townWithoutRegions);
                helper.assertTrue(town == 0, "no natural hostile may spawn inside the town, but " + town + " did");
                helper.assertTrue(wilderness > 0, "the wilderness keeps natural hostile spawning, but none spawned");
                helper.assertTrue(poi > 0, "the POI placeholder keeps vanilla-like spawning, but none spawned");
                helper.assertTrue(townWithoutRegions > 0, "control: without regions the town chamber spawns hostiles");
            } finally {
                removePlayer(level.getServer(), player);
            }
        });
    }

    // The same decision through SpawnPlacements.checkSpawnRules (the hook every natural spawn passes), per region.
    @GameTest(maxTicks = 400)
    public void spawnRulesDependOnTheRegion(GameTestHelper helper) {
        Arena a = arena(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(a.dark(), "the chambers are dark once light has settled");
            ServerLevel level = helper.getLevel();
            BlockPos town = a.town().center();
            BlockPos wild = a.wilderness().center();
            BlockPos poi = a.poi().center();
            for (EntitySpawnReason reason : List.of(EntitySpawnReason.NATURAL, EntitySpawnReason.REINFORCEMENT)) {
                helper.assertFalse(check(level, EntityTypes.ZOMBIE, reason, town), "a zombie must not spawn in town, " + reason);
                helper.assertTrue(check(level, EntityTypes.ZOMBIE, reason, wild), "a zombie may spawn in the wilderness, " + reason);
                helper.assertTrue(check(level, EntityTypes.ZOMBIE, reason, poi), "a zombie may spawn in the POI (placeholder), " + reason);
            }
            helper.assertTrue(SpawnGovernance.decide(EntityTypes.SKELETON, level, EntitySpawnReason.NATURAL, town) == SpawnPolicy.Decision.REFUSE_AMBIENT_HOSTILE,
                    "the town's refusal names the settlement rule");
            helper.assertTrue(SpawnGovernance.decide(EntityTypes.SKELETON, level, EntitySpawnReason.NATURAL, wild) == SpawnPolicy.Decision.ALLOW,
                    "the wilderness allows it");
            // Deliberate reasons pass the town's rule (the spawn itself doesn't go through this check, see below).
            for (EntitySpawnReason reason : List.of(EntitySpawnReason.COMMAND, EntitySpawnReason.SPAWN_ITEM_USE, EntitySpawnReason.SPAWNER)) {
                helper.assertTrue(check(level, EntityTypes.ZOMBIE, reason, town), "a zombie with reason " + reason + " passes in town");
            }
        });
    }

    private static boolean check(ServerLevel level, EntityType<?> type, EntitySpawnReason reason, BlockPos pos) {
        return SpawnPlacements.checkSpawnRules(type, level, reason, pos, level.getRandom());
    }

    // ---- Deliberate spawns and walk-ins (06 sections 17.1, 17.5; DoD 14) ----

    // /summon and a spawn egg (reason SPAWN_ITEM_USE) still put a hostile in the middle of town.
    @GameTest(maxTicks = 400)
    public void summonedAndEggHostilesAppearInTown(GameTestHelper helper) {
        Arena a = arena(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(a.dark(), "the chambers are dark once light has settled");
            ServerLevel level = helper.getLevel();
            BlockPos pos = a.town().center();
            AABB room = new AABB(pos).inflate(9, 2, 9);
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(),
                    String.format(Locale.ROOT, "summon minecraft:skeleton %d %d %d", pos.getX(), pos.getY(), pos.getZ()));
            Zombie egg = EntityTypes.ZOMBIE.spawn(level, pos.east(2), EntitySpawnReason.SPAWN_ITEM_USE);
            List<Entity> skeletons = new ArrayList<>(level.getEntities(EntityTypes.SKELETON, room, Entity::isAlive));
            helper.assertTrue(!skeletons.isEmpty(), "/summon puts a skeleton in town");
            helper.assertTrue(egg != null && egg.isAlive(), "a spawn egg puts a zombie in town");
            skeletons.forEach(Entity::discard);
            egg.discard();
        });
    }

    // A hostile from the wilderness that enters the town stays there: nothing removes it (DoD 14).
    @GameTest(maxTicks = 400)
    public void hostilesFromOutsideCanEnterTown(GameTestHelper helper) {
        Arena a = arena(helper);
        ServerLevel level = helper.getLevel();
        Zombie[] zombie = new Zombie[1];
        helper.succeedWhen(() -> {
            if (zombie[0] == null) {
                helper.assertTrue(a.dark(), "the chambers are dark once light has settled");
                zombie[0] = EntityTypes.ZOMBIE.spawn(level, a.wilderness().center(), EntitySpawnReason.COMMAND);
                helper.assertTrue(zombie[0] != null, "a zombie spawns in the wilderness");
                zombie[0].setPersistenceRequired();
                BlockPos town = a.town().center();
                zombie[0].snapTo(town.getX() + 0.5, town.getY(), town.getZ() + 0.5);
                helper.assertTrue(Regions.at(level, town.getX(), town.getY(), town.getZ()).kind() == RegionKind.SETTLEMENT, "it is now in town");
                throw helper.assertionException("give it a few ticks in town");
            }
            helper.assertTrue(zombie[0].tickCount >= 40, "the zombie has lived 40 ticks in town (" + zombie[0].tickCount + ")");
            helper.assertTrue(zombie[0].isAlive() && !zombie[0].isRemoved(), "a hostile from outside stays in town");
            zombie[0].discard();
        });
    }

    // ---- Prohibited human-like mobs (06 section 13), in every region ----

    @GameTest(maxTicks = 400)
    public void prohibitedHumanLikeMobsAreRefusedInEveryRegion(GameTestHelper helper) {
        Arena a = arena(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(a.dark(), "the chambers are dark once light has settled");
            ServerLevel level = helper.getLevel();
            for (Chamber chamber : List.of(a.town(), a.wilderness(), a.poi())) {
                BlockPos pos = chamber.center();
                for (EntityType<?> type : List.of(EntityTypes.VILLAGER, EntityTypes.WITCH, EntityTypes.PILLAGER, EntityTypes.ZOMBIE_VILLAGER)) {
                    for (EntitySpawnReason reason : List.of(EntitySpawnReason.NATURAL, EntitySpawnReason.SPAWNER, EntitySpawnReason.STRUCTURE)) {
                        helper.assertTrue(SpawnGovernance.decide(type, level, reason, pos) == SpawnPolicy.Decision.REFUSE_PROHIBITED_HUMAN_LIKE,
                                EntityType.getKey(type) + " (" + reason + ") must be refused in the " + chamber.name() + " as a pseudo-human");
                        helper.assertFalse(check(level, type, reason, pos), EntityType.getKey(type) + " must fail the spawn check in the " + chamber.name());
                    }
                    helper.assertTrue(SpawnGovernance.decide(type, level, EntitySpawnReason.COMMAND, pos).allowed(),
                            "/summon " + EntityType.getKey(type) + " still works in the " + chamber.name() + " (development stays possible)");
                }
            }
        });
    }

    // ---- Phantoms (they bypass checkSpawnRules; PhantomSpawnerMixin) ----

    /*
      A sleepless player at night under open sky: vanilla's phantom spawner sends phantoms when the player stands in the
      wilderness, never when the player stands in town. Night is set for the synchronous check only.
     */
    @GameTest(maxTicks = 400)
    public void phantomsNeverSpawnOverTheTown(GameTestHelper helper) {
        Arena a = arena(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(a.dark(), "the chambers are dark once light has settled");
            ServerLevel level = helper.getLevel();
            // On the chambers' roofs: open sky, above sea level.
            BlockPos inTown = a.town().center().above(4);
            BlockPos inWilderness = a.wilderness().center().above(4);
            ServerPlayer player = player(helper, inTown);
            try {
                player.getStats().setValue(player, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 10_000_000);
                int town = phantomsAround(helper, level, player, inTown);
                int wilderness = phantomsAround(helper, level, player, inWilderness);
                LOGGER.info("phantoms from {} spawner runs: over the town {}, over the wilderness {}", 60, town, wilderness);
                helper.assertTrue(town == 0, "no phantom may spawn over the town, but " + town + " did");
                helper.assertTrue(wilderness > 0, "phantoms still spawn over the wilderness");
            } finally {
                removePlayer(level.getServer(), player);
            }
        });
    }

    private static int phantomsAround(GameTestHelper helper, ServerLevel level, ServerPlayer player, BlockPos at) {
        player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.assertTrue(level.canSeeSky(player.blockPosition()), "the player must see the sky at " + at.toShortString());
        AABB around = new AABB(at).inflate(16, 40, 16);
        int count = 0;
        for (int run = 0; run < 60; run++) {
            ((LevelSkyDarkenAccessor) level).inhabitedWorldTest$setSkyDarken(11);
            new PhantomSpawner().tick(level, true);
            List<Entity> phantoms = new ArrayList<>(level.getEntities(EntityTypes.PHANTOM, around, Entity::isAlive));
            count += phantoms.size();
            phantoms.forEach(Entity::discard);
        }
        return count;
    }

    // ---- Cost on the live level (06 section 17.6) ----

    @GameTest(maxTicks = 400)
    public void spawnDecisionCostOnTheLiveLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorldPlan plan = plan(helper);
        java.util.Random random = new java.util.Random(20261112L);
        BlockPos[] positions = new BlockPos[4096];
        PlannedSite town = plan.site(V01Sites.TOWN);
        for (int k = 0; k < positions.length; k++) {
            positions[k] = k % 4 == 0
                    ? new BlockPos(town.centerX() + random.nextInt(200) - 100, 60 + random.nextInt(40), town.centerZ() + random.nextInt(200) - 100)
                    : new BlockPos(random.nextInt(1024) - 512, random.nextInt(384) - 64, random.nextInt(1024) - 512);
        }
        int calls = 2_000_000;
        long refused = 0;
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 4; round++) {
            long start = System.nanoTime();
            for (int k = 0; k < calls; k++) {
                if (!SpawnGovernance.decide(EntityTypes.ZOMBIE, level, EntitySpawnReason.NATURAL, positions[k & 4095]).allowed()) {
                    refused++;
                }
            }
            best = Math.min(best, System.nanoTime() - start);
        }
        double perCall = best / (double) calls;
        LOGGER.info(String.format(Locale.ROOT, "spawn decision on the live level (entity tags, RPG identity, region lookup, policy): %.1f ns per call, best of 4 x %d (%d refused in all)",
                perCall, calls, refused));
        helper.assertTrue(refused > 0, "some positions lie in town");
        helper.assertTrue(perCall < 2000, "a spawn decision costs " + perCall + " ns");
        helper.succeed();
    }
}
