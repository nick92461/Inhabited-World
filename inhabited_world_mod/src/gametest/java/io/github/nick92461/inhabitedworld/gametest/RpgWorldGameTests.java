package io.github.nick92461.inhabitedworld.gametest;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.worldgen.RpgWorlds;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/*
  The game-test world is built from our shipped rpg_world preset (see build.gradle), so these tests run
  inside a real Inhabited World overworld: same dimension type, same noise settings, no other dimensions.
 */
public class RpgWorldGameTests {
    private static final ResourceKey<DimensionType> RPG_DIMENSION_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, InhabitedWorld.id("rpg_overworld"));

    // Every other game test relies on this one: the test world carries the Inhabited World identity.
    @GameTest
    public void testWorldIsAnInhabitedWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(RpgWorlds.isRpg(level.getChunkSource().getGenerator()), "test overworld must be an Inhabited World (noise settings)");
        helper.assertTrue(level.dimensionTypeRegistration().is(RPG_DIMENSION_TYPE), "test overworld must use the rpg_overworld dimension type");
        helper.succeed();
    }

    // P2A step 1: the preset has no Nether and no End.
    @GameTest
    public void noNetherOrEnd(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(server.getLevel(Level.NETHER) == null, "an Inhabited World must not have a Nether");
        helper.assertTrue(server.getLevel(Level.END) == null, "an Inhabited World must not have an End");
        helper.succeed();
    }

    // P2A step 2: the structure allowlist leaves the generator with no structure sets to place,
    // although the registry still holds vanilla's.
    @GameTest
    public void structureAllowlistAppliesInWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean registryHasSets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).listElements().findAny().isPresent();
        helper.assertTrue(registryHasSets, "the structure-set registry should still hold vanilla's sets");
        helper.assertTrue(level.getChunkSource().getGeneratorState().possibleStructureSets().isEmpty(),
                "no structure set may be placeable in an Inhabited World (allowlist is empty)");
        helper.succeed();
    }
}
