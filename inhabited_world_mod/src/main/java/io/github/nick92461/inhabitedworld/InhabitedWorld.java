package io.github.nick92461.inhabitedworld;

import io.github.nick92461.inhabitedworld.worldgen.RpgWorlds;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InhabitedWorld implements ModInitializer {
	public static final String MOD_ID = "inhabited_world";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[{}] foundation initialized", MOD_ID);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			for (ServerLevel level : server.getAllLevels()) {
				boolean ours = RpgWorlds.isRpg(level.getChunkSource().getGenerator());
				LOGGER.info("[{}] level {} is an Inhabited World level: {}", MOD_ID, level.dimension().identifier(), ours);
			}
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}