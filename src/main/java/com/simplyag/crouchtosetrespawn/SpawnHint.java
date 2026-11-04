package com.simplyag.crouchtosetrespawn;

import com.simplyag.crouchtosetrespawn.util.Compat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The "Sneak + use to set respawn" hint, at most once per player per night. A player's first ever hint is a toast;
 * every later one is action bar text, because vanilla labels any toast "Advancement Made!".
 *
 * <p>Vanilla clients only show toasts for advancements, so the toast grants a hidden advancement
 * (data/crouchtosetrespawn/advancement/spawn_hint.json) and revokes it two ticks later, once the done state has been
 * flushed to the client. Hidden and not done, it is never sent, so it never shows up in the advancements screen, and
 * announce_to_chat is false so nothing goes to chat. "First ever" is stored in the player's own advancement file by
 * spawn_hint_seen, which has no display, so clients never receive it.
 *
 * <p>Driven by the mod's own server hooks (MinecraftServerMixin, PlayerListMixin), not Fabric API events, so it
 * works the same on every Minecraft version.
 */
public final class SpawnHint {
	public static final String ADVANCEMENT = "spawn_hint";
	public static final String SEEN_ADVANCEMENT = "spawn_hint_seen";
	public static final String CRITERION = "hint";
	public static final String ACTION_BAR_STRING = "Bed not set as spawn - Sneak + use to set respawn";
	/** Ticks between grant and revoke. Two guarantees one ServerPlayer.doTick flush sends the done state. */
	static final int REVOKE_DELAY = 2;
	/**
	 * Ticks before the action bar text. The click's own overlay messages ("You can only sleep at night", sleep status)
	 * are sent right after it and would replace it straight away.
	 */
	public static final int ACTION_BAR_DELAY = 30;

	/** Night index each player last saw the hint in. In memory only: a restart just lets it show once more. */
	private static final Map<UUID, Long> LAST_NIGHT_SHOWN = new HashMap<>();
	/** Players with the advancement currently granted, and the server tick to revoke it on. */
	private static final Map<UUID, Integer> PENDING_REVOKE = new HashMap<>();
	private static final Map<UUID, PendingText> PENDING_TEXT = new HashMap<>();
	private static boolean warnedMissing;

	private static final class PendingText {
		final int tick;
		final ResourceKey<Level> dimension;
		final BlockPos bed;

		PendingText(int tick, ResourceKey<Level> dimension, BlockPos bed) {
			this.tick = tick;
			this.dimension = dimension;
			this.bed = bed;
		}
	}

	private SpawnHint() {
	}

	public static Component actionBarText() {
		return Compat.literal(ACTION_BAR_STRING);
	}

	/** End of every server tick. */
	public static void tick(MinecraftServer server) {
		revokeDue(server);
		sendTextDue(server);
	}

	/** Server stopped (single player can start another world in the same game). */
	public static void reset() {
		LAST_NIGHT_SHOWN.clear();
		PENDING_REVOKE.clear();
		PENDING_TEXT.clear();
	}

	/**
	 * Each index runs noon to noon, so it holds exactly one night: clicking again the same night, or the morning
	 * after sleeping through it, doesn't repeat the hint, but the next evening does.
	 */
	public static long nightIndex(long dayTime) {
		return Math.floorDiv(dayTime + 18000L, 24000L);
	}

	/** Called when a normal (non-sneaking) bed use skipped a spawn change vanilla would have made. */
	public static void maybeShow(ServerPlayer player, BlockPos bed) {
		if (!ModConfig.showSpawnHintPopup) {
			return;
		}

		long night = nightIndex(Compat.dayTime(Compat.level(player)));
		Long last = LAST_NIGHT_SHOWN.get(player.getUUID());
		if (last != null && last == night) {
			return;
		}

		MinecraftServer server = Compat.server(player);
		LAST_NIGHT_SHOWN.put(player.getUUID(), night);
		if (advancementsLoaded(server) && !Compat.isDone(player, SEEN_ADVANCEMENT)) {
			Compat.award(player, SEEN_ADVANCEMENT, CRITERION);
			Compat.award(player, ADVANCEMENT, CRITERION);
			PENDING_REVOKE.put(player.getUUID(), server.getTickCount() + REVOKE_DELAY);
		} else {
			PENDING_TEXT.put(player.getUUID(),
				new PendingText(server.getTickCount() + ACTION_BAR_DELAY, Compat.level(player).dimension(), bed));
		}
	}

	private static void sendTextDue(MinecraftServer server) {
		int now = server.getTickCount();
		Iterator<Map.Entry<UUID, PendingText>> it = PENDING_TEXT.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, PendingText> entry = it.next();
			PendingText pending = entry.getValue();
			if (now >= pending.tick) {
				it.remove();
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				// The player may have sneak-clicked this bed while the text was waiting; then it no longer applies.
				if (player != null && !Compat.isSpawnAt(player, pending.dimension, pending.bed)) {
					Compat.sendOverlay(player, actionBarText());
				}
			}
		}
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
		if (Compat.isDone(player, ADVANCEMENT)) {
			Compat.revoke(player, ADVANCEMENT, CRITERION);
		}
	}

	private static boolean advancementsLoaded(MinecraftServer server) {
		boolean loaded = Compat.hasAdvancement(server, ADVANCEMENT) && Compat.hasAdvancement(server, SEEN_ADVANCEMENT);
		if (!loaded && !warnedMissing) {
			warnedMissing = true;
			CrouchToSetRespawn.LOGGER.warn("The {} advancements are missing (mod data pack disabled?); the sneak-to-set-spawn hint will be text only",
				CrouchToSetRespawn.MOD_ID);
		}
		return loaded;
	}
}
