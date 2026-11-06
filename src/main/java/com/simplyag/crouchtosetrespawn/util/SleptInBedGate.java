package com.simplyag.crouchtosetrespawn.util;

import net.minecraft.server.level.ServerPlayer;

/**
 * Holds back vanilla's slept_in_bed trigger (Sweet Dreams) for a sleep that didn't set spawn, so it means the same
 * as on 26.3, where vanilla itself only fires it when spawn is set. Before 26.3 vanilla fires it on every sleep,
 * from inside startSleepInBed, so ServerPlayerMixin holds the gate for that call and PlayerTriggerMixin checks it.
 * Server thread only.
 */
public final class SleptInBedGate {
	private static ServerPlayer held;

	private SleptInBedGate() {
	}

	public static void hold(ServerPlayer player) {
		held = player;
	}

	public static void release(ServerPlayer player) {
		if (held == player) {
			held = null;
		}
	}

	public static boolean isHeld(ServerPlayer player) {
		return held == player;
	}
}
