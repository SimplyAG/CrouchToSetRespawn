package com.simplyag.crouchtosetrespawn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The "Sneak + use to set respawn" hint, at most once per player per night. A player's first ever hint is a toast;
 * every later one is action bar text, because vanilla labels any toast "Advancement Made!".
 *
 * <p>Vanilla clients only show toasts for advancements, so the toast grants a hidden advancement
 * (data/crouchtosetrespawn/advancement/spawn_hint.json) and revokes it two ticks later, once the done state has been
 * flushed to the client. Hidden and not done, it is never sent, so it never shows up in the advancements screen, and
 * announce_to_chat is false so nothing goes to chat. "First ever" is stored in the player's own advancement file by
 * spawn_hint_seen, which has no display, so clients never receive it.
 */
public final class SpawnHint {
	public static final Identifier ADVANCEMENT = CrouchToSetRespawn.id("spawn_hint");
	public static final Identifier SEEN_ADVANCEMENT = CrouchToSetRespawn.id("spawn_hint_seen");
	public static final String CRITERION = "hint";
	public static final Component ACTION_BAR_TEXT = Component.literal("Bed not set as spawn - Sneak + use to set respawn");
	/** Ticks between grant and revoke. Two guarantees one ServerPlayer.doTick flush sends the done state. */
	static final int REVOKE_DELAY = 2;
	/**
	 * Ticks before the action bar text. The click's own overlay messages ("You can only sleep at night", sleep status)
	 * are sent right after it and would replace it straight away.
	 */
	public static final int ACTION_BAR_DELAY = 30;

	private static final Logger LOGGER = LoggerFactory.getLogger(CrouchToSetRespawn.MOD_ID);
	/** Night index each player last saw the hint in. In memory only: a restart just lets it show once more. */
	private static final Map<UUID, Long> LAST_NIGHT_SHOWN = new HashMap<>();
	/** Players with the advancement currently granted, and the server tick to revoke it on. */
	private static final Map<UUID, Integer> PENDING_REVOKE = new HashMap<>();
	private static final Map<UUID, PendingText> PENDING_TEXT = new HashMap<>();
	private static boolean warnedMissing;

	private record PendingText(int tick, GlobalPos bed) {
	}

	private SpawnHint() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			revokeDue(server);
			sendTextDue(server);
		});
		// Fires in PlayerList.placeNewPlayer, on the server thread. Leaving is handled by PlayerListMixin.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> revoke(handler.getPlayer()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_NIGHT_SHOWN.clear();
			PENDING_REVOKE.clear();
			PENDING_TEXT.clear();
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
	public static void maybeShow(ServerPlayer player, GlobalPos bed) {
		if (!ModConfig.showSpawnHintPopup) {
			return;
		}

		long night = nightIndex(player.level().getDefaultClockTime());
		Long last = LAST_NIGHT_SHOWN.get(player.getUUID());
		if (last != null && last == night) {
			return;
		}

		MinecraftServer server = player.level().getServer();
		LAST_NIGHT_SHOWN.put(player.getUUID(), night);
		AdvancementHolder holder = holder(server, ADVANCEMENT);
		AdvancementHolder seen = holder(server, SEEN_ADVANCEMENT);
		if (holder != null && seen != null && !player.getAdvancements().getOrStartProgress(seen).isDone()) {
			player.getAdvancements().award(seen, CRITERION);
			player.getAdvancements().award(holder, CRITERION);
			PENDING_REVOKE.put(player.getUUID(), server.getTickCount() + REVOKE_DELAY);
		} else {
			PENDING_TEXT.put(player.getUUID(), new PendingText(server.getTickCount() + ACTION_BAR_DELAY, bed));
		}
	}

	private static void sendTextDue(MinecraftServer server) {
		int now = server.getTickCount();
		Iterator<Map.Entry<UUID, PendingText>> it = PENDING_TEXT.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, PendingText> entry = it.next();
			if (now >= entry.getValue().tick()) {
				it.remove();
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null && !isSpawnAt(player, entry.getValue().bed())) {
					player.sendOverlayMessage(ACTION_BAR_TEXT);
				}
			}
		}
	}

	/** The player may have sneak-clicked this bed while the text was waiting; then it no longer applies. */
	private static boolean isSpawnAt(ServerPlayer player, GlobalPos bed) {
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		return respawn != null && respawn.respawnData().globalPos().equals(bed);
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

	/**
	 * Revoke straight away. On leave this runs before the player is saved, so a logout inside the 2-tick window
	 * can't save it as done; on join it clears one saved as done anyway (a crash or autosave inside the window).
	 */
	public static void revoke(ServerPlayer player) {
		PENDING_REVOKE.remove(player.getUUID());
		PENDING_TEXT.remove(player.getUUID());
		revokeNow(player);
	}

	private static void revokeNow(ServerPlayer player) {
		AdvancementHolder holder = holder(player.level().getServer(), ADVANCEMENT);
		if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
			player.getAdvancements().revoke(holder, CRITERION);
		}
	}

	private static AdvancementHolder holder(MinecraftServer server, Identifier id) {
		AdvancementHolder holder = server.getAdvancements().get(id);
		if (holder == null && !warnedMissing) {
			warnedMissing = true;
			LOGGER.warn("Advancement {} is missing (mod data pack disabled?); the sneak-to-set-spawn hint will be text only", id);
		}
		return holder;
	}
}
