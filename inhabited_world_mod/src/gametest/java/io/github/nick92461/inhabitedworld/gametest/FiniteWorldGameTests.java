package io.github.nick92461.inhabitedworld.gametest;

import java.util.Optional;

import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;
import io.github.nick92461.inhabitedworld.worldgen.pregen.PregenSchedule;
import io.github.nick92461.inhabitedworld.worldgen.pregen.PregenState;
import io.github.nick92461.inhabitedworld.worldgen.pregen.Pregeneration;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlan;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlans;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

/*
  P2A step 4 in a real Inhabited World created by this game-test run: the WorldPlan made at creation, the world
  border it sets, and pre-generation. The test run pre-generates only a northern strip of the map and its ocean
  ring (build.gradle sets inhabited_world.dev.pregenRows); the full run uses the same code.
 */
public class FiniteWorldGameTests {
    private static WorldPlan plan(GameTestHelper helper) {
        WorldPlan plan = WorldPlans.get(helper.getLevel().getServer());
        if (plan == null) {
            throw helper.assertionException("a new Inhabited World must have a WorldPlan");
        }
        return plan;
    }

    @GameTest
    public void worldPlanIsMadeAtCreation(GameTestHelper helper) {
        WorldPlan plan = plan(helper);
        helper.assertTrue(plan.dataVersion() == WorldPlan.DATA_VERSION, "the plan carries its data version");
        helper.assertTrue(plan.worldId() != null, "the plan carries a permanent world ID");
        helper.assertTrue(plan.playableArea().equals(PlayableArea.V0_1), "the playable area is the v0.1 map, got " + plan.playableArea());
        helper.succeed();
    }

    // The border encloses exactly the playable area, and survives as the level's own saved border.
    @GameTest
    public void worldBorderIsThePlayableArea(GameTestHelper helper) {
        PlayableArea area = plan(helper).playableArea();
        WorldBorder border = helper.getLevel().getWorldBorder();
        helper.assertTrue(border.getCenterX() == area.centerX() && border.getCenterZ() == area.centerZ(),
                "border centre should be " + area.centerX() + ", " + area.centerZ() + " but is " + border.getCenterX() + ", " + border.getCenterZ());
        helper.assertTrue(border.getSize() == area.size(), "border size should be " + area.size() + " but is " + border.getSize());

        // Edge blocks: the last block inside, the first outside, on each side.
        helper.assertTrue(border.isWithinBounds(new BlockPos(area.minBlockX(), 64, area.minBlockZ())), "the area's first block is inside the border");
        helper.assertTrue(border.isWithinBounds(new BlockPos(area.maxBlockX(), 64, area.maxBlockZ())), "the area's last block is inside the border");
        helper.assertFalse(border.isWithinBounds(new BlockPos(area.minBlockX() - 1, 64, area.minBlockZ())), "one block west of the area is outside");
        helper.assertFalse(border.isWithinBounds(new BlockPos(area.maxBlockX() + 1, 64, area.maxBlockZ())), "one block east of the area is outside");
        helper.succeed();
    }

    // Vanilla's spawn search (the climate-based origin it starts from) lands inside the finite map.
    @GameTest
    public void spawnSearchStartsInsideThePlayableArea(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos origin = level.getChunkSource().getGenerator().getOrigin(level.getChunkSource().randomState());
        helper.assertTrue(plan(helper).playableArea().containsChunk(origin.x(), origin.z()), "the spawn search origin " + origin + " must lie inside the playable area");
        helper.succeed();
    }

    // The game-test mod moves the tests inside the border (GameTestServerMixin); every other test relies on it.
    @GameTest
    public void testsRunInsideThePlayableArea(GameTestHelper helper) {
        BlockPos here = helper.absolutePos(BlockPos.ZERO);
        helper.assertTrue(plan(helper).playableArea().contains(here.getX(), here.getZ()), "game tests must run inside the playable area, but run at " + here.toShortString());
        helper.assertTrue(helper.getLevel().getWorldBorder().isWithinBounds(here), "and inside the world border");
        helper.succeed();
    }

    /*
      Pre-generation covers the map and a 12-chunk ocean ring beyond the border (user decision 2026-11-11). This run
      only does the northern strip (build.gradle), which holds 12 rows of ring and 2 rows of the map: every chunk of
      it is saved at full status before the world opens, the ring chunks are open sea, and nothing past the ring was
      generated to full status (the negative control: chunks past the ring are not full, or not there at all).
     */
    @GameTest(maxTicks = 400)
    public void preGenerationCoversTheMapAndOceanRing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayableArea area = plan(helper).playableArea();
        PregenState state = level.getDataStorage().get(PregenState.TYPE);
        helper.assertTrue(state != null, "pre-generation state must be saved with the overworld");
        helper.assertTrue(state.isComplete(), "pre-generation must be complete before the world opens");

        PregenSchedule full = Pregeneration.fullSchedule(area);
        helper.assertTrue(full.minChunkX() == area.minChunkX() - 12 && full.maxChunkZ() == area.maxChunkZ() + 12,
                "the full schedule reaches 12 chunks past the border, but is " + full);
        String rows = System.getProperty(Pregeneration.DEV_ROWS_PROPERTY);
        PregenSchedule expected = rows == null || rows.equals("all") ? full : full.firstRows(Integer.parseInt(rows));
        PregenSchedule schedule = state.schedule();
        helper.assertTrue(schedule.equals(expected), "this world pre-generated " + schedule + ", expected " + expected);

        Pregeneration.Result result = Pregeneration.lastResult();
        helper.assertTrue(result != null && result.finished(), "this server must have run pre-generation to the end");
        helper.assertTrue(result.chunks() == schedule.totalChunks(), "it generated " + result.chunks() + " of " + schedule.totalChunks() + " chunks");

        int ring = 0;
        int map = 0;
        for (int band = 0; band < schedule.bandCount(); band++) {
            for (ChunkPos pos : schedule.band(band)) {
                ChunkStatus status = savedStatus(level, pos);
                helper.assertTrue(status == ChunkStatus.FULL, "pre-generated chunk " + pos + " should be saved at full status, but is " + status);
                if (area.containsChunk(pos.x(), pos.z())) {
                    map++;
                } else if (ring++ % 3 == 0) {
                    assertOpenSea(helper, level.getChunk(pos.x(), pos.z()));
                }
            }
        }
        helper.assertTrue(ring > 0 && map > 0, "the run must cover both ring and map chunks (ring " + ring + ", map " + map + ")");

        // Just past the ring, chunks are only partly generated: vanilla needs neighbours around every full chunk.
        // Beyond those neighbours nothing was generated at all.
        ChunkPos pastRing = new ChunkPos(area.minChunkX() - 13, area.minChunkZ());
        helper.assertTrue(savedStatus(level, pastRing) != ChunkStatus.FULL, "control: chunk " + pastRing + ", 13 chunks past the border, is not full");
        ChunkPos farBeyond = new ChunkPos(area.minChunkX() - 13 - ChunkLevel.RADIUS_AROUND_FULL_CHUNK, area.minChunkZ());
        helper.assertTrue(savedStatus(level, farBeyond) == null, "control: chunk " + farBeyond + " was never generated");
        helper.succeed();
    }

    // Open sea: the floor at the chunk's centre lies below sea level, and the block at sea level is water.
    private static void assertOpenSea(GameTestHelper helper, LevelChunk chunk) {
        int floor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, 8, 8);
        BlockPos sea = chunk.getPos().getWorldPosition().offset(8, 62, 8);
        helper.assertTrue(floor < 62, "ring chunk " + chunk.getPos() + " should be sea, but its floor is at " + floor);
        helper.assertTrue(chunk.getFluidState(sea).is(FluidTags.WATER), "ring chunk " + chunk.getPos() + " should hold water at sea level");
    }

    // The status a chunk has on disk (or in the write queue), or null if it was never saved.
    private static ChunkStatus savedStatus(ServerLevel level, ChunkPos pos) {
        Optional<CompoundTag> tag = level.getChunkSource().chunkMap.read(pos).join();
        return tag.map(SerializableChunkData::getChunkStatusFromTag).orElse(null);
    }
}
