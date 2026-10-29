package io.github.nick92461.inhabitedworld.client;

import io.github.nick92461.inhabitedworld.client.dev.DevTestWorld;

import net.fabricmc.api.ClientModInitializer;

public class InhabitedWorldClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Dev runs only; does nothing unless the test launch asked for a test world.
		DevTestWorld.registerIfRequested();
	}
}
