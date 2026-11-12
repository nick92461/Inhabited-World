package io.github.nick92461.inhabitedworld.region;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/*
  Development only (canon 10 section 60, Favor Inspectability): shows semantic-region edges in game with particles,
  toggled per player by /inhabited plan edges. Twice a second, for each region near a watching player, particles
  trace the region's outline at the player's height (or at its floor or ceiling when the player is below or above it)
  along the stretch within REACH blocks: green for a settlement, flames for a POI. Only the watching player sees them.
 */
public final class RegionEdges {
    private static final int REACH = 48;
    private static final int STEP = 2;
    private static final int PERIOD_TICKS = 10;

    private static final Set<UUID> WATCHING = new HashSet<>();

    private RegionEdges() {
    }

    /** Registered with the development commands only. */
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(RegionEdges::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WATCHING.clear());
    }

    /** Toggles the edge view for a player; returns whether it is now on. */
    public static boolean toggle(ServerPlayer player) {
        if (WATCHING.remove(player.getUUID())) {
            return false;
        }
        WATCHING.add(player.getUUID());
        return true;
    }

    private static void tick(MinecraftServer server) {
        if (WATCHING.isEmpty() || server.getTickCount() % PERIOD_TICKS != 0) {
            return;
        }
        for (UUID id : WATCHING) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            RegionIndex index = Regions.index(level);
            if (index == null) {
                continue;
            }
            for (SemanticRegion region : index.regions()) {
                draw(level, player, region);
            }
        }
    }

    private static void draw(ServerLevel level, ServerPlayer player, SemanticRegion region) {
        RegionVolume v = region.volume();
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        double y = Math.max(v.minY(), Math.min(v.maxY() + 1, player.getY())) + 0.2;
        SimpleParticleType particle = region.kind() == RegionKind.POI ? ParticleTypes.FLAME : ParticleTypes.HAPPY_VILLAGER;
        // The outline runs along the outer faces of the edge blocks (max + 1).
        for (int x = Math.max(v.minX(), px - REACH); x <= Math.min(v.maxX() + 1, px + REACH); x += STEP) {
            send(level, player, particle, x, y, v.minZ());
            send(level, player, particle, x, y, v.maxZ() + 1);
        }
        for (int z = Math.max(v.minZ(), pz - REACH); z <= Math.min(v.maxZ() + 1, pz + REACH); z += STEP) {
            send(level, player, particle, v.minX(), y, z);
            send(level, player, particle, v.maxX() + 1, y, z);
        }
    }

    private static void send(ServerLevel level, ServerPlayer player, SimpleParticleType particle, double x, double y, double z) {
        if (Math.abs(x - player.getX()) <= REACH && Math.abs(z - player.getZ()) <= REACH) {
            level.sendParticles(player, particle, true, true, x, y, z, 1, 0, 0, 0, 0);
        }
    }
}
