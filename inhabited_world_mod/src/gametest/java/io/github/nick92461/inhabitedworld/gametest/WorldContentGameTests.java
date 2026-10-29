package io.github.nick92461.inhabitedworld.gametest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.nick92461.inhabitedworld.gametest.mixin.CustomSpawnersAccessor;
import io.github.nick92461.inhabitedworld.spawn.SpawnGovernance;
import io.github.nick92461.inhabitedworld.worldgen.FeatureSuppression;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/*
  P2A step 3 in a real Inhabited World (the game-test overworld is one, see RpgWorldGameTests): no uncontrolled
  dungeons or desert wells, no vanilla pseudo-humans from the game's own spawning, no trader or siege spawners,
  and no lit nether portals. Default-world behaviour is covered by the unit tests (SpawnPolicyTest and friends),
  since a game-test run has only this one overworld.
 */
public class WorldContentGameTests {
    private static final Set<ResourceKey<PlacedFeature>> EXPECTED_SUPPRESSED = Set.of(
            placedFeature("monster_room"), placedFeature("monster_room_deep"), placedFeature("desert_well"),
            // Pale oaks with a creaking heart: a generated hostile source outside the planner (user decision 2026-10-29).
            placedFeature("pale_oak_creaking_checked"));

    private static final List<EntityType<?>> EXPECTED_PROHIBITED = List.of(
            EntityTypes.VILLAGER, EntityTypes.WANDERING_TRADER, EntityTypes.ZOMBIE_VILLAGER, EntityTypes.WITCH,
            EntityTypes.PILLAGER, EntityTypes.VINDICATOR, EntityTypes.EVOKER, EntityTypes.ILLUSIONER);

    // Reasons the game uses on its own. Each must be refused for a prohibited mob where vanilla would allow it.
    private static final List<EntitySpawnReason> AUTOMATIC = List.of(
            EntitySpawnReason.NATURAL, EntitySpawnReason.CHUNK_GENERATION, EntitySpawnReason.STRUCTURE,
            EntitySpawnReason.PATROL, EntitySpawnReason.EVENT, EntitySpawnReason.SPAWNER, EntitySpawnReason.TRIAL_SPAWNER,
            EntitySpawnReason.REINFORCEMENT);

    private static ResourceKey<PlacedFeature> placedFeature(String path) {
        return ResourceKey.create(Registries.PLACED_FEATURE, Identifier.withDefaultNamespace(path));
    }

    // ---- Feature suppression (canon 06 section 15) ----

    @GameTest
    public void suppressedFeaturesAreTheDenylist(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderSet<PlacedFeature> suppressed = FeatureSuppression.forGenerator(level.getChunkSource().getGenerator(), level.registryAccess());
        Set<ResourceKey<PlacedFeature>> keys = suppressed.stream().map(holder -> holder.unwrapKey().orElseThrow()).collect(Collectors.toSet());

        helper.assertTrue(keys.equals(EXPECTED_SUPPRESSED), "suppressed placed features should be " + EXPECTED_SUPPRESSED + " but are " + keys);
        helper.succeed();
    }

    /*
      Creaking-heart pale oaks are never decorated directly: pale_garden_vegetation (a random selector) places them
      through PlacedFeature.place, so that nested entry point must be stopped too. Their only placement modifier
      draws no random, so this test proves it by outcome instead: on two identical prepared sites a plain pale oak
      grows (the control: the site is valid) while the creaking-heart one does not.
     */
    @GameTest(maxTicks = 100)
    public void nestedSuppressedFeatureNeverPlaces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Registry<PlacedFeature> registry = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);

        BlockPos controlSite = prepareTreeSite(helper, 6);
        boolean controlPlaced = registry.getValueOrThrow(placedFeature("pale_oak_checked"))
                .place(level, level.getChunkSource().getGenerator(), RandomSource.create(42L), controlSite);
        helper.assertTrue(controlPlaced, "control: a plain pale oak must grow on the prepared site");

        BlockPos testSite = prepareTreeSite(helper, 20);
        boolean placed = registry.getValueOrThrow(placedFeature("pale_oak_creaking_checked"))
                .place(level, level.getChunkSource().getGenerator(), RandomSource.create(42L), testSite);
        helper.assertFalse(placed, "a creaking-heart pale oak must not place in an Inhabited World");
        helper.assertBlockNotPresent(Blocks.PALE_OAK_LOG, helper.relativePos(testSite));
        helper.succeed();
    }

    // A grass floor with open air above, big enough for a 2x2 pale oak; returns the sapling position.
    private static BlockPos prepareTreeSite(GameTestHelper helper, int originX) {
        int originZ = 6;
        for (int x = originX - 5; x <= originX + 6; x++) {
            for (int z = originZ - 5; z <= originZ + 6; z++) {
                helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
                for (int y = 1; y <= 16; y++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        return helper.absolutePos(new BlockPos(originX, 1, originZ));
    }

    /*
      The hook must stop a suppressed feature before vanilla does anything: its placement modifiers would draw from
      the random source first (in_square, rarity_filter), so an untouched random proves the feature never ran.
      The control feature (coal ore) runs through the same placer and does draw.
     */
    @GameTest
    public void suppressedFeaturesNeverRunWhileOthersDo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Registry<PlacedFeature> registry = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        BlockPos chunkOrigin = ChunkPos.containing(helper.absolutePos(BlockPos.ZERO)).getWorldPosition();

        for (ResourceKey<PlacedFeature> key : EXPECTED_SUPPRESSED) {
            PlacedFeature feature = registry.getValueOrThrow(key);
            RandomSource random = RandomSource.create(42L);
            boolean placed = new FeaturePlacer(level, level.getChunkSource().getGenerator()).placeWithBiomeCheck(feature, random, chunkOrigin);
            helper.assertFalse(placed, key.identifier() + " must not place in an Inhabited World");
            helper.assertTrue(random.nextLong() == RandomSource.create(42L).nextLong(), key.identifier() + " must be stopped before it runs");
        }

        PlacedFeature control = registry.getValueOrThrow(placedFeature("ore_coal_upper"));
        RandomSource random = RandomSource.create(42L);
        new FeaturePlacer(level, level.getChunkSource().getGenerator()).placeWithBiomeCheck(control, random, chunkOrigin);
        helper.assertTrue(random.nextLong() != RandomSource.create(42L).nextLong(), "an allowed feature (ore_coal_upper) must still run");
        helper.succeed();
    }

    /*
      End to end: freshly generated terrain holds no spawner. Structures are off in game tests (and suppressed in
      Inhabited Worlds), so the only source of a spawner here would be a monster-room dungeon. A vanilla 7x7-chunk
      area almost always has several.
     */
    @GameTest(maxTicks = 400)
    public void generatedChunksHaveNoSpawnerDungeons(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos center = ChunkPos.containing(helper.absolutePos(BlockPos.ZERO));
        int radius = 3;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunk(center.x() + dx, center.z() + dz);
                chunk.getBlockEntities().forEach((pos, blockEntity) -> {
                    if (blockEntity instanceof SpawnerBlockEntity) {
                        helper.fail("found a spawner (monster-room dungeon?) at " + pos.toShortString());
                    }
                });
            }
        }
        helper.succeed();
    }

    // ---- Spawn governance (canon 06 sections 13, 17.5) ----

    // A closed stone box with a two-block air pocket: dark and solid-floored, so vanilla's rules let monsters
    // and villagers spawn in it. Returns the pocket's lower block (absolute).
    private static BlockPos darkPocket(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    helper.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        helper.setBlock(2, 1, 2, Blocks.AIR);
        helper.setBlock(2, 2, 2, Blocks.AIR);
        return helper.absolutePos(new BlockPos(2, 1, 2));
    }

    private static boolean check(GameTestHelper helper, EntityType<?> type, EntitySpawnReason reason, BlockPos pos) {
        return SpawnPlacements.checkSpawnRules(type, helper.getLevel(), reason, pos, helper.getLevel().getRandom());
    }

    // Ordinary monsters keep vanilla spawning (06 section 13): a zombie may still spawn naturally.
    @GameTest(maxTicks = 100)
    public void allowedMonsterStillSpawnsNaturally(GameTestHelper helper) {
        BlockPos pocket = darkPocket(helper);
        // Light settles a few ticks after the blocks change.
        helper.succeedWhen(() -> {
            helper.assertTrue(check(helper, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, pocket), "a zombie should spawn naturally in the dark");
            helper.assertTrue(check(helper, EntityTypes.ZOMBIE, EntitySpawnReason.CHUNK_GENERATION, pocket), "and at chunk generation");
        });
    }

    @GameTest
    public void prohibitedTagHoldsTheVanillaPseudoHumans(GameTestHelper helper) {
        for (EntityType<?> type : EXPECTED_PROHIBITED) {
            helper.assertTrue(SpawnGovernance.isProhibitedHumanLike(type), EntityType.getKey(type) + " should be prohibited");
        }
        for (EntityType<?> type : List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.LLAMA, EntityTypes.TRADER_LLAMA,
                EntityTypes.IRON_GOLEM, EntityTypes.CAT, EntityTypes.PHANTOM)) {
            helper.assertFalse(SpawnGovernance.isProhibitedHumanLike(type), EntityType.getKey(type) + " is not human-like");
        }
        helper.succeed();
    }

    /*
      Where vanilla would let each prohibited mob spawn (the zombie control proves the spot is valid, and the
      deliberate reasons pass), every automatic reason is refused.
     */
    @GameTest(maxTicks = 100)
    public void prohibitedMobsAreRefusedForAutomaticReasonsOnly(GameTestHelper helper) {
        BlockPos pocket = darkPocket(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(check(helper, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, pocket), "control: the spot must be valid for natural monsters");
            for (EntityType<?> type : EXPECTED_PROHIBITED) {
                String name = EntityType.getKey(type).toString();
                helper.assertTrue(check(helper, type, EntitySpawnReason.COMMAND, pocket), name + ": a command spawn must pass");
                helper.assertTrue(check(helper, type, EntitySpawnReason.SPAWN_ITEM_USE, pocket), name + ": a spawn egg must pass");
                for (EntitySpawnReason reason : AUTOMATIC) {
                    helper.assertFalse(check(helper, type, reason, pocket), name + ": a " + reason + " spawn must be refused");
                }
            }
        });
    }

    // The real deliberate paths still work: /summon and a spawn egg in a player's hand.
    @GameTest(maxTicks = 40)
    public void commandAndSpawnEggStillSpawnProhibitedMobs(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos summonAt = helper.absolutePos(new BlockPos(2, 1, 2));
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "summon minecraft:villager " + summonAt.getX() + " " + summonAt.getY() + " " + summonAt.getZ());

        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WANDERING_TRADER_SPAWN_EGG));
        BlockPos floor = helper.absolutePos(new BlockPos(5, 0, 5));
        helper.useBlock(new BlockPos(5, 0, 5), player, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false));

        helper.succeedWhen(() -> {
            helper.assertEntityPresent(EntityTypes.VILLAGER);
            helper.assertEntityPresent(EntityTypes.WANDERING_TRADER);
        });
    }

    // ---- Custom spawners ----

    @GameTest
    public void noTraderOrSiegeSpawner(GameTestHelper helper) {
        List<CustomSpawner> spawners = ((CustomSpawnersAccessor) helper.getLevel()).inhabitedWorldTest$customSpawners();
        helper.assertFalse(spawners.stream().anyMatch(s -> s instanceof WanderingTraderSpawner), "no wandering-trader spawner");
        helper.assertFalse(spawners.stream().anyMatch(s -> s instanceof VillageSiege), "no village-siege spawner");
        helper.assertTrue(spawners.stream().anyMatch(s -> s instanceof PhantomSpawner), "the phantom spawner stays");
        helper.succeed();
    }

    // ---- Portal ignition (canon 02 section 4) ----

    // An obsidian frame with a 2x3 opening, in the x-y plane at z = 1. Vanilla would light it in this overworld.
    private static void portalFrame(GameTestHelper helper) {
        for (int x = 0; x <= 3; x++) {
            helper.setBlock(x, 0, 1, Blocks.OBSIDIAN);
            helper.setBlock(x, 4, 1, Blocks.OBSIDIAN);
        }
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(0, y, 1, Blocks.OBSIDIAN);
            helper.setBlock(3, y, 1, Blocks.OBSIDIAN);
        }
    }

    @GameTest
    public void flintAndSteelDoesNotLightAPortal(GameTestHelper helper) {
        portalFrame(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        BlockPos bottom = helper.absolutePos(new BlockPos(1, 0, 1));
        helper.useBlock(new BlockPos(1, 0, 1), player, new BlockHitResult(Vec3.atCenterOf(bottom).add(0, 0.5, 0), Direction.UP, bottom, false));

        helper.assertBlockPresent(Blocks.FIRE, new BlockPos(1, 1, 1));
        for (int x = 1; x <= 2; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.assertBlockNotPresent(Blocks.NETHER_PORTAL, new BlockPos(x, y, 1));
            }
        }
        helper.succeed();
    }

    // Flint and steel, fire charges and dispensers ask canBePlacedAt first. Mid-air in the opening, fire can only
    // be placed if it would form a portal, so a refusal there means no portal.
    @GameTest
    public void noIgnitionPathSeesAPortal(GameTestHelper helper) {
        portalFrame(helper);
        BlockPos midAir = helper.absolutePos(new BlockPos(2, 2, 1));
        helper.assertFalse(BaseFireBlock.canBePlacedAt(helper.getLevel(), midAir, Direction.NORTH), "fire must not be placeable mid-frame");

        helper.setBlock(new BlockPos(2, 1, 1), Blocks.FIRE);
        for (int x = 1; x <= 2; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.assertBlockNotPresent(Blocks.NETHER_PORTAL, new BlockPos(x, y, 1));
            }
        }
        helper.succeed();
    }
}
