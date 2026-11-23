package io.github.nick92461.inhabitedworld.threat;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;

import org.jspecify.annotations.Nullable;

/*
  Finds where a hotspot or an encounter can put a mob, by searching a box itself rather than waiting for vanilla's
  random spawn rolls to land there (canon 06 section 17.7: a deliberately dangerous POI should not be empty because
  spawn rolls did not cooperate). Each try picks a random column in the box and scans it from top to bottom for every
  spot the mob can stand on (its placement rule, a sturdy floor, room for its body), then takes one of those at
  random; so a cave or a tunnel anywhere in the box is found within a few tries.

  Only loaded, entity-ticking columns are searched: a search never loads or generates terrain.
 */
public final class SpawnSpots {
    private static final int MAX_COLUMN_SPOTS = 64;

    private SpawnSpots() {
    }

    /**
     * @param cx, cy, cz the box's anchor
     * @param radius     horizontal half-size of the box
     * @param below      how far the box reaches below the anchor's y
     * @param above      how far it reaches above it
     * @param tries      how many columns to try
     * @param extra      what else the spot must satisfy (light rules, player distance)
     */
    public static @Nullable BlockPos find(ServerLevel level, EntityType<?> type, RandomSource random, int cx, int cy, int cz, int radius, int below,
                                          int above, int tries, Predicate<BlockPos> extra) {
        int[] ys = new int[MAX_COLUMN_SPOTS];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Math.min(cy + above, level.getMaxY() - 1);
        int bottom = Math.max(cy - below, level.getMinY() + 1);
        for (int t = 0; t < tries; t++) {
            int x = cx + random.nextInt(2 * radius + 1) - radius;
            int z = cz + random.nextInt(2 * radius + 1) - radius;
            pos.set(x, cy, z);
            if (!level.isPositionEntityTicking(pos)) {
                continue;
            }
            int found = 0;
            for (int y = top; y >= bottom && found < ys.length; y--) {
                pos.set(x, y, z);
                if (standable(level, type, pos) && extra.test(pos)) {
                    ys[found++] = y;
                }
            }
            if (found > 0) {
                return new BlockPos(x, ys[random.nextInt(found)], z);
            }
        }
        return null;
    }

    /** Whether the mob can stand at pos: its placement rule (floor it may spawn on, empty blocks), a sturdy floor, room for its body. */
    public static boolean standable(ServerLevel level, EntityType<?> type, BlockPos pos) {
        BlockPos floor = pos.below();
        return SpawnPlacements.isSpawnPositionOk(type, level, pos)
                && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                && level.getFluidState(pos).isEmpty()
                && level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
    }

    /** Whether any player who isn't a spectator is within distance of a point. */
    public static boolean playerWithin(ServerLevel level, double x, double y, double z, double distance) {
        double limit = distance * distance;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(x, y, z) <= limit) {
                return true;
            }
        }
        return false;
    }
}
