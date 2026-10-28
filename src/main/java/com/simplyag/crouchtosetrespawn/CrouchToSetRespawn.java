package com.simplyag.crouchtosetrespawn;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public class CrouchToSetRespawn implements ModInitializer {
	public static final String MOD_ID = "crouchtosetrespawn";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModConfig.load();
		SpawnHint.init();
	}
}
