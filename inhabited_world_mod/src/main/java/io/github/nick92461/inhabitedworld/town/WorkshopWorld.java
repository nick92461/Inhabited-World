package io.github.nick92461.inhabitedworld.town;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import io.github.nick92461.inhabitedworld.InhabitedWorld;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;

/*
  Development only: a town's WORKSHOP WORLD, the flat world in which the user edits the town in creative mode
  (plan: "Imported builds", edit loop). After the town's first export the workshop world is its source of truth:
  the exporter (python -m buildimport export <town>) reads it back and rewrites the templates and manifest.

  The dev client (DevWorkshopWorld, run with -Dinhabited_world.dev.workshop=<town>) creates the world once and asks
  for the paste; when that new world's server has started, this pastes the town at the manifest's workshop origin
  (which keeps the original map's chunk alignment), puts a sign on every marker, sets the spawn on the quay, stops
  time, weather and mob spawning, and writes inhabited_workshop.json into the world folder. Later launches only open
  the world: the paste never runs twice on the same world.
 */
public final class WorkshopWorld {
    public static final String INFO_FILE = "inhabited_workshop.json";

    private static volatile String pendingTown;

    private WorkshopWorld() {
    }

    public static void requestPaste(String town) {
        pendingTown = town;
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            String town = pendingTown;
            pendingTown = null;
            if (town != null) {
                setUp(server, town);
            }
        });
    }

    public static boolean isWorkshop(MinecraftServer server) {
        return Files.exists(server.getWorldPath(LevelResource.ROOT).resolve(INFO_FILE));
    }

    static void setUp(MinecraftServer server, String townName) {
        Path info = server.getWorldPath(LevelResource.ROOT).resolve(INFO_FILE);
        if (Files.exists(info)) {
            InhabitedWorld.LOGGER.info("[{}] workshop world already set up; not pasting again", InhabitedWorld.MOD_ID);
            return;
        }
        TownManifest town = TownManifest.find(server.getResourceManager(), townName).orElse(null);
        if (town == null) {
            InhabitedWorld.LOGGER.error("[{}] workshop: no town called '{}' in the mod's data", InhabitedWorld.MOD_ID, townName);
            return;
        }
        ServerLevel level = server.overworld();
        TownColumns columns = TownColumns.load(server.getResourceManager(), town.columnsId());
        BlockPos origin = town.workshopOrigin();
        TownPaste.Result result = TownPaste.paste(level, town, columns, origin, true);

        GameRules rules = level.getGameRules();
        rules.set(GameRules.ADVANCE_TIME, false, server);
        rules.set(GameRules.ADVANCE_WEATHER, false, server);
        rules.set(GameRules.SPAWN_MOBS, false, server);

        BlockPos spawn = town.markers().stream()
                .filter(m -> m.id().endsWith("quay")).findFirst()
                .or(() -> town.markers().stream().findFirst())
                .map(m -> origin.offset(m.pos()).west(2))
                .orElse(origin.offset(columns.width() / 2, town.size().getY(), columns.depth() / 2));
        level.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, spawn, 90f, 0f));

        String json = "{\n  \"town\": \"" + town.key() + "\",\n  \"id\": \"" + town.id() + "\",\n  \"version\": " + town.version()
                + ",\n  \"origin\": [" + origin.getX() + ", " + origin.getY() + ", " + origin.getZ() + "],\n  \"created\": \""
                + LocalDate.now() + "\",\n  \"note\": \"Workshop world for this town; it is the town's source of truth. "
                + "Re-export with: python -m buildimport export <town> (tools/buildimport/README.md).\"\n}\n";
        try {
            Files.writeString(info, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            InhabitedWorld.LOGGER.error("[{}] workshop: could not write {}", InhabitedWorld.MOD_ID, info, e);
        }
        InhabitedWorld.LOGGER.info("[{}] workshop world for {} ready: {}", InhabitedWorld.MOD_ID, town.id(), result);
    }
}
