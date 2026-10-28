package com.simplyag.crouchtosetrespawn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The "Sneak + use to set respawn" toast. Vanilla clients only show toasts for advancements, so this grants a
 * hidden advancement (data/crouchtosetrespawn/advancement/spawn_hint.json) and revokes it two ticks later, once
 * the done state has been flushed to the client. Hidden and not done, it is never sent, so it never shows up in
 * the advancements screen, and announce_to_chat is false so nothing goes to chat.
 */
public final class SpawnHint {
	public static final Identifier ADVANCEMENT = CrouchToSetRespawn.id("spawn_hint");
	public static final String CRITERION = "hint";
	/** Ticks between grant and revoke. Two guarantees one ServerPlayer.doTick flush sends the done state. */
	static final int REVOKE_DELAY = 2;

	private static final Logger LOGGER = LoggerFactory.getLogger(CrouchToSetRespawn.MOD_ID);
	/** Night index each player last saw the hint in. In memory only: a restart just lets it show once more. */
	private static final Map<UUID, Long> LAST_NIGHT_SHOWN = new HashMap<>();
	/** Players with the advancement currently granted, and the server tick to revoke it on. */
	private static final Map<UUID, Integer> PENDING_REVOKE = new HashMap<>();
	private static boolean warnedMissing;

	private SpawnHint() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SpawnHint::revokeDue);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> revoke(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> revoke(handler.getPlayer()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_NIGHT_SHOWN.clear();
			PENDING_REVOKE.clear();
		});
	}

	/**
	 * Each index runs noon to noon, so it holds exactly one night: clicking again the same night, or the morning
	 * after sleeping through it, doesn't repeat the hint, but the next evening does.
	 */
	public static long nightIndex(long clockTime) {
		return Math.floorDiv(clockTime + 18000L, 24000L);
	}

	/** Called when a normal (non-sneaking) bed use skipped a spawn change vanilla would have made. */
	public static void maybeShow(ServerPlayer player) {
		if (!ModConfig.showSpawnHintPopup) {
			return;
		}

		long night = nightIndex(player.level().getDefaultClockTime());
		Long last = LAST_NIGHT_SHOWN.get(player.getUUID());
		if (last != null && last == night) {
			return;
		}

		AdvancementHolder holder = holder(player.level().getServer());
		if (holder == null) {
			return;
		}

		LAST_NIGHT_SHOWN.put(player.getUUID(), night);
		player.getAdvancements().award(holder, CRITERION);
		PENDING_REVOKE.put(player.getUUID(), player.level().getServer().getTickCount() + REVOKE_DELAY);
	}

	private static void revokeDue(MinecraftServer server) {
		int now = server.getTickCount();
		Iterator<Map.Entry<UUID, Integer>> it = PENDING_REVOKE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> entry = it.next();
			if (now >= entry.getValue()) {
				it.remove();
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null) {
					revokeNow(player);
				}
			}
		}
	}

	/** Revoke straight away (join/leave), so a logout inside the 2-tick window can't save it as done. */
	private static void revoke(ServerPlayer player) {
		PENDING_REVOKE.remove(player.getUUID());
		revokeNow(player);
	}

	private static void revokeNow(ServerPlayer player) {
		AdvancementHolder holder = holder(player.level().getServer());
		if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
			player.getAdvancements().revoke(holder, CRITERION);
		}
	}

	private static AdvancementHolder holder(MinecraftServer server) {
		AdvancementHolder holder = server.getAdvancements().get(ADVANCEMENT);
		if (holder == null && !warnedMissing) {
			warnedMissing = true;
			LOGGER.warn("Advancement {} is missing (mod data pack disabled?); the sneak-to-set-spawn hint won't show", ADVANCEMENT);
		}
		return holder;
	}
}
