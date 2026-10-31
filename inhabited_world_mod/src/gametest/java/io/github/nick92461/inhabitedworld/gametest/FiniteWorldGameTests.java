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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

/*
  P2A step 4 in a real Inhabited World created by this game-test run: the WorldPlan made at creation, the world
  border it sets, and pre-generation. The test run pre-generates only the middle of the map (build.gradle sets
  inhabited_world.dev.pregenChunkRadius), so these tests check that smaller area; the full map runs the same code.
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

    @GameTest(maxTicks = 200)
    public void preGenerationFinishedBeforeTheWorldOpened(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PregenState state = level.getDataStorage().get(PregenState.TYPE);
        helper.assertTrue(state != null, "pre-generation state must be saved with the overworld");
        helper.assertTrue(state.isComplete(), "pre-generation must be complete before the world opens");

        Pregeneration.Result result = Pregeneration.lastResult();
        PregenSchedule schedule = state.schedule();
        helper.assertTrue(result != null && result.finished(), "this server must have run pre-generation to the end");
        helper.assertTrue(result.chunks() == schedule.totalChunks(), "it generated " + result.chunks() + " of " + schedule.totalChunks() + " chunks");
        helper.assertTrue(plan(helper).playableArea().contains(schedule.area().minBlockX(), schedule.area().minBlockZ())
                && plan(helper).playableArea().contains(schedule.area().maxBlockX(), schedule.area().maxBlockZ()),
                "the pre-generated area lies inside the playable area");

        // Every scheduled chunk is at full status: saved, or (the last band, until it unloads) still in memory. The
        // first band was unloaded and saved as pre-generation went on. Negative control: a chunk far outside the
        // scheduled area, which nothing has asked for, has no saved data at all.
        for (int band = 0; band < schedule.bandCount(); band++) {
            for (ChunkPos pos : schedule.band(band)) {
                ChunkStatus status = status(level, pos);
                helper.assertTrue(status == ChunkStatus.FULL, "pre-generated chunk " + pos + " should be at full status, but is " + status);
            }
        }
        for (ChunkPos pos : schedule.band(0)) {
            helper.assertTrue(savedStatus(level, pos) == ChunkStatus.FULL, "first-band chunk " + pos + " should already be saved at full status");
        }
        ChunkPos untouched = new ChunkPos(schedule.area().maxChunkX() + 20, schedule.area().minChunkZ());
        helper.assertTrue(level.getChunkSource().chunkMap.read(untouched).join().isEmpty(), "control: chunk " + untouched + " was never generated");
        helper.succeed();
    }

    /*
      FULL if the chunk is full on disk or in memory. Neither alone is enough: the last band may not be saved yet,
      and a saved full chunk that something reloads at a lower level (the tests near it) shows a lower status in memory.
     */
    private static ChunkStatus status(ServerLevel level, ChunkPos pos) {
        ChunkStatus saved = savedStatus(level, pos);
        ChunkStatus loaded = level.getChunkSource().chunkMap.getLatestStatus(pos.pack());
        return saved == ChunkStatus.FULL || loaded == ChunkStatus.FULL ? ChunkStatus.FULL : (saved != null ? saved : loaded);
    }

    // The status a chunk has on disk (or in the write queue), or null if it was never saved.
    private static ChunkStatus savedStatus(ServerLevel level, ChunkPos pos) {
        Optional<CompoundTag> tag = level.getChunkSource().chunkMap.read(pos).join();
        return tag.map(SerializableChunkData::getChunkStatusFromTag).orElse(null);
    }
}
