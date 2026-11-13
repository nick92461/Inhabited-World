package io.github.nick92461.inhabitedworld.client.dev;

import java.util.List;
import java.util.Optional;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.town.WorkshopWorld;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/*
  Development only: opens a town's workshop world when the title screen first appears (workshop.bat at the
  workspace root runs this through Loom's runWorkshop with -Dinhabited_world.dev.workshop=<town>).

  - If saves/workshop-<town> exists, it is opened as it is. It is never deleted or recreated: after the first
    export it is the town's source of truth.
  - Otherwise a new one is created: a flat sea (stone to y 40, water to y 63, plains biome, no structures or
    lakes), creative mode, cheats on; WorkshopWorld then pastes the town into it once the server has started.
 */
public final class DevWorkshopWorld {
    public static final String PROPERTY = "inhabited_world.dev.workshop";

    private static boolean opened;

    private DevWorkshopWorld() {
    }

    public static void registerIfRequested() {
        String town = System.getProperty(PROPERTY);
        if (town == null || town.isBlank() || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Screen screen = client.gui.screen();
            if (!opened && client.gui.overlay() == null
                    && (screen instanceof TitleScreen || screen instanceof AccessibilityOnboardingScreen)) {
                opened = true;
                open(client, town.trim());
            }
        });
    }

    public static String folder(String town) {
        String last = town.contains("/") ? town.substring(town.lastIndexOf('/') + 1) : town;
        return "workshop-" + last;
    }

    private static void open(Minecraft client, String town) {
        String folder = folder(town);
        if (client.getLevelSource().levelExists(folder)) {
            InhabitedWorld.LOGGER.info("[{}] workshop: opening existing '{}'", InhabitedWorld.MOD_ID, folder);
            client.createWorldOpenFlows().openWorld(folder, () -> client.gui.setScreen(new TitleScreen()));
            return;
        }
        InhabitedWorld.LOGGER.info("[{}] workshop: creating '{}' for town {}", InhabitedWorld.MOD_ID, folder, town);
        WorkshopWorld.requestPaste(town);
        LevelSettings settings = new LevelSettings("Workshop " + town, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT,
                true, WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(0L, false, false);
        client.createWorldOpenFlows().createFreshLevel(folder, settings, options, DevWorkshopWorld::flatSea, client.gui.screen());
    }

    static WorldDimensions flatSea(HolderLookup.Provider registries) {
        HolderGetter<Biome> biomes = registries.lookupOrThrow(Registries.BIOME);
        FlatLevelGeneratorSettings flat = FlatLevelGeneratorSettings.getDefault(biomes,
                        registries.lookupOrThrow(Registries.STRUCTURE_SET), registries.lookupOrThrow(Registries.PLACED_FEATURE))
                .withBiomeAndLayers(List.of(
                                new FlatLayerInfo(1, Blocks.BEDROCK),      // y -64
                                new FlatLayerInfo(104, Blocks.STONE),      // y -63 .. 40
                                new FlatLayerInfo(23, Blocks.WATER)),      // y 41 .. 63, our sea level
                        Optional.empty(), biomes.getOrThrow(Biomes.PLAINS));
        WorldDimensions dims = registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
        return dims.replaceOverworldGenerator(registries, new FlatLevelSource(flat));
    }
}
