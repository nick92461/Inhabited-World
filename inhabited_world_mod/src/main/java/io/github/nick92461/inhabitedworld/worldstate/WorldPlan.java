package io.github.nick92461.inhabitedworld.worldstate;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;

import net.minecraft.core.UUIDUtil;

/*
  The WorldPlan (canon 02 section 7): made once when an Inhabited World is created, saved with it, and never
  re-planned on load. This is its first piece, holding only what P2A step 4 uses:

  - worldId: the permanent unique world ID that every WorldPlan carries (02 section 7, 08 section 49)
  - playableArea: the finite map's bounds, which set the world border and the pre-generated area

  Later phases add the town and POI placements, semantic regions and geography facts (implementation plan,
  "WorldPlan"). Each addition is a new field with a migration from this data version (08 section 49).
 */
public record WorldPlan(int dataVersion, UUID worldId, PlayableArea playableArea) {
    public static final int DATA_VERSION = 1;

    public static final Codec<WorldPlan> CODEC = RecordCodecBuilder.<WorldPlan>create(i -> i.group(
                    Codec.INT.fieldOf("data_version").forGetter(WorldPlan::dataVersion),
                    UUIDUtil.STRING_CODEC.fieldOf("world_id").forGetter(WorldPlan::worldId),
                    PlayableArea.CODEC.fieldOf("playable_area").forGetter(WorldPlan::playableArea))
            .apply(i, WorldPlan::new))
            .validate(WorldPlan::checkVersion);

    // A save written by a newer mod version must not be read as if it were this version's.
    private static DataResult<WorldPlan> checkVersion(WorldPlan plan) {
        return plan.dataVersion() == DATA_VERSION
                ? DataResult.success(plan)
                : DataResult.error(() -> "unsupported WorldPlan data version " + plan.dataVersion() + " (this mod reads " + DATA_VERSION + ")");
    }

    public static WorldPlan create(UUID worldId, PlayableArea playableArea) {
        return new WorldPlan(DATA_VERSION, worldId, playableArea);
    }
}
