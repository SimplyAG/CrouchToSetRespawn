package com.simplyag.crouchtosetrespawn;

import com.simplyag.crouchtosetrespawn.util.Compat;
import net.minecraft.server.level.ServerPlayer;

/** The real, keep-forever advancement for setting spawn with sneak + use, day or night. */
public final class UndercoverSleeper {
	public static final String ADVANCEMENT = "undercover_sleeper";
	public static final String CRITERION = "set_spawn_sneaking";

	private UndercoverSleeper() {
	}

	/** Does nothing if it's already earned, or if a data pack removed it. */
	public static void award(ServerPlayer player) {
		Compat.award(player, ADVANCEMENT, CRITERION);
	}
}
