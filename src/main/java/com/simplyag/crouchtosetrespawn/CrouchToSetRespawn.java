package com.simplyag.crouchtosetrespawn;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CrouchToSetRespawn implements ModInitializer {
	public static final String MOD_ID = "crouchtosetrespawn";
	/** log4j, not slf4j: it ships with every Minecraft version the mod supports. */
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModConfig.load();
	}
}
