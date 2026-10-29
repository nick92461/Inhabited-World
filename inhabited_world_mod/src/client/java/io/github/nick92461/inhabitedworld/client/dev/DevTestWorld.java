package io.github.nick92461.inhabitedworld.client.dev;

import java.io.IOException;
import java.util.Locale;

import io.github.nick92461.inhabitedworld.InhabitedWorld;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;

/*
  Development only (canon 10 section 60, Testing Workflow): when the title screen first appears, open a fresh
  test world: fixed seed, creative, cheats on. Each launch replaces the previous test world of the same kind;
  no other world is touched.

  It runs only when the dev run sets -Dinhabited_world.dev.testWorld (Loom's runTestClient, used by
  test-launch.bat) and only in a development environment, so the shipped mod never creates a world.
 */
public final class DevTestWorld {
    public static final String PROPERTY = "inhabited_world.dev.testWorld";

    // Fixed so every test world, and its Default-world twin, has the same terrain.
    public static final long SEED = 20261029L;

    private enum Kind {
        RPG("inhabited-test", "Inhabited Test", ResourceKey.create(Registries.WORLD_PRESET, InhabitedWorld.id("rpg_world"))),
        DEFAULT("default-test", "Default Test", WorldPresets.NORMAL);

        final String folder;
        final String name;
        final ResourceKey<WorldPreset> preset;

        Kind(String folder, String name, ResourceKey<WorldPreset> preset) {
            this.folder = folder;
            this.name = name;
            this.preset = preset;
        }
    }

    private static boolean opened;

    private DevTestWorld() {
    }

    public static void registerIfRequested() {
        String requested = System.getProperty(PROPERTY);
        if (requested == null || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }

        Kind kind;
        try {
            kind = Kind.valueOf(requested.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            InhabitedWorld.LOGGER.error("[{}] dev test world: unknown kind '{}' (use rpg or default)", InhabitedWorld.MOD_ID, requested);
            return;
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Wait until startup resource loading is over and the first menu is up, then act once. A fresh
            // run folder opens on the accessibility onboarding screen instead of the title screen.
            Screen screen = client.gui.screen();
            if (!opened && client.gui.overlay() == null
                    && (screen instanceof TitleScreen || screen instanceof AccessibilityOnboardingScreen)) {
                opened = true;
                open(client, kind);
            }
        });
    }

    private static void open(Minecraft client, Kind kind) {
        LevelStorageSource saves = client.getLevelSource();
        if (saves.levelExists(kind.folder)) {
            try (LevelStorageSource.LevelStorageAccess access = saves.createAccess(kind.folder)) {
                access.deleteLevel();
            } catch (IOException e) {
                // Don't fall back to a second folder: stale test worlds would pile up unnoticed.
                InhabitedWorld.LOGGER.error("[{}] dev test world: could not delete the previous '{}'", InhabitedWorld.MOD_ID, kind.folder, e);
                return;
            }
        }

        InhabitedWorld.LOGGER.info("[{}] dev test world: creating '{}' (preset {}, seed {})",
                InhabitedWorld.MOD_ID, kind.folder, kind.preset.identifier(), SEED);

        LevelSettings settings = new LevelSettings(kind.name, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT,
                true, WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(SEED, true, false);

        // The same path vanilla's own debug-world and demo-world buttons use: no create-world screen.
        client.createWorldOpenFlows().createFreshLevel(kind.folder, settings, options,
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(kind.preset).value().createWorldDimensions(),
                client.gui.screen());
    }
}
