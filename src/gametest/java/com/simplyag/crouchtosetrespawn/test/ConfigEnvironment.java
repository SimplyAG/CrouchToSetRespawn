package com.simplyag.crouchtosetrespawn.test;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simplyag.crouchtosetrespawn.ModConfig;
import java.util.Optional;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.server.level.ServerLevel;

/**
 * Test environment that sets the mod's config for one batch and restores it afterwards. Tests in different
 * environments never share a batch, so a test with the setting off can't leak into one that expects it on.
 */
public record ConfigEnvironment(Optional<Boolean> sneakWithItemsSetsSpawn, Optional<Boolean> showSpawnHintPopup)
	implements TestEnvironmentDefinition<boolean[]> {
	public static final MapCodec<ConfigEnvironment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.BOOL.optionalFieldOf("sneak_with_items_sets_spawn").forGetter(ConfigEnvironment::sneakWithItemsSetsSpawn),
			Codec.BOOL.optionalFieldOf("show_spawn_hint_popup").forGetter(ConfigEnvironment::showSpawnHintPopup)
		).apply(i, ConfigEnvironment::new));

	@Override
	public boolean[] setup(ServerLevel level) {
		boolean[] previous = {ModConfig.sneakWithItemsSetsSpawn, ModConfig.showSpawnHintPopup};
		this.sneakWithItemsSetsSpawn.ifPresent(value -> ModConfig.sneakWithItemsSetsSpawn = value);
		this.showSpawnHintPopup.ifPresent(value -> ModConfig.showSpawnHintPopup = value);
		return previous;
	}

	@Override
	public void teardown(ServerLevel level, boolean[] previous) {
		ModConfig.sneakWithItemsSetsSpawn = previous[0];
		ModConfig.showSpawnHintPopup = previous[1];
	}

	@Override
	public MapCodec<ConfigEnvironment> codec() {
		return CODEC;
	}
}
