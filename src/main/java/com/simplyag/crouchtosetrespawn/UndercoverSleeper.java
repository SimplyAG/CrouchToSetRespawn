package com.simplyag.crouchtosetrespawn;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** The real, keep-forever advancement for setting spawn with sneak + use, day or night. */
public final class UndercoverSleeper {
	public static final Identifier ADVANCEMENT = CrouchToSetRespawn.id("undercover_sleeper");
	public static final String CRITERION = "set_spawn_sneaking";

	private UndercoverSleeper() {
	}

	/** Does nothing if it's already earned, or if a data pack removed it. */
	public static void award(ServerPlayer player) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(ADVANCEMENT);
		if (holder != null) {
			player.getAdvancements().award(holder, CRITERION);
		}
	}
}
