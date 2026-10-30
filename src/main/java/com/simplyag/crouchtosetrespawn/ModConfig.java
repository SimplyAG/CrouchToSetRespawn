package com.simplyag.crouchtosetrespawn;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * config/crouchtosetrespawn.properties, read once at startup. A .properties file so each setting can carry a plain
 * sentence saying what it does. Missing or unreadable settings fall back to the defaults below.
 */
public final class ModConfig {
	public static final String FILE_NAME = CrouchToSetRespawn.MOD_ID + ".properties";
	static final String SNEAK_WITH_ITEMS_SETS_SPAWN = "sneak_with_items_sets_spawn";
	static final String SHOW_SPAWN_HINT_POPUP = "show_spawn_hint_popup";

	private static final Logger LOGGER = LoggerFactory.getLogger(CrouchToSetRespawn.MOD_ID);
	private static final String DEFAULT_FILE = """
		# Crouch to Set Respawn. Changes take effect after a server restart.

		# Sneak + use a bed while holding something (shield, torch, sword, map...) still sets your spawn.
		# Items that do something on a bed keep working as normal: blocks get placed,
		# bone meal, flint and steel and buckets do their thing.
		# false = vanilla rule: both hands must be empty to use a bed while sneaking.
		sneak_with_items_sets_spawn=true

		# On a normal (non-sneaking) bed click, tell the player "Bed not set as spawn - Sneak + use to set respawn".
		# At most once per player per night. The first time ever is a popup, after that it is text above the hotbar.
		show_spawn_hint_popup=true
		""";

	/** Public and mutable so the game tests can flip them; nothing else writes them after load. */
	public static boolean sneakWithItemsSetsSpawn = true;
	public static boolean showSpawnHintPopup = true;

	private ModConfig() {
	}

	static void load() {
		load(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME));
	}

	/** Public for the game tests, which load files with edited values. */
	public static void load(Path path) {
		sneakWithItemsSetsSpawn = true;
		showSpawnHintPopup = true;
		if (!Files.exists(path)) {
			try {
				Files.createDirectories(path.getParent());
				Files.writeString(path, DEFAULT_FILE, StandardCharsets.UTF_8);
			} catch (IOException e) {
				LOGGER.warn("Couldn't write the default {}; using defaults", path, e);
			}
			return;
		}

		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (IOException e) {
			LOGGER.warn("Couldn't read {}; using defaults", path, e);
			return;
		}
		sneakWithItemsSetsSpawn = readBoolean(properties, SNEAK_WITH_ITEMS_SETS_SPAWN, sneakWithItemsSetsSpawn);
		showSpawnHintPopup = readBoolean(properties, SHOW_SPAWN_HINT_POPUP, showSpawnHintPopup);
	}

	private static boolean readBoolean(Properties properties, String key, boolean fallback) {
		String value = properties.getProperty(key);
		if (value == null) {
			return fallback;
		}
		value = value.trim();
		if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
			return Boolean.parseBoolean(value);
		}
		LOGGER.warn("{} in {} should be true or false, not '{}'; using {}", key, FILE_NAME, value, fallback);
		return fallback;
	}
}
