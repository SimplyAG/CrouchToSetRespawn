package com.simplyag.crouchtosetrespawn.util;

import com.simplyag.crouchtosetrespawn.CrouchToSetRespawn;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Every Minecraft API the mod uses that differs between the versions it supports. The rest of the mod calls these
 * instead of the game, so version conditionals live here (and in the mixins' own targets) and nowhere else.
 */
public final class Compat {
	private Compat() {
	}

	public static MinecraftServer server(ServerPlayer player) {
		return level(player).getServer();
	}

	public static ServerLevel level(ServerPlayer player) {
		return player.level();
	}

	/** Time of day in ticks, counting up across days (one day is 24000). */
	public static long dayTime(ServerLevel level) {
		return level.getDefaultClockTime();
	}

	/** "Use while sneaking" in vanilla's own sense: the key that makes block use pass to the held item. */
	public static boolean isSneaking(ServerPlayer player) {
		return player.isSecondaryUseActive();
	}

	public static Component literal(String text) {
		return Component.literal(text);
	}

	/** Text above the hotbar. */
	public static void sendOverlay(ServerPlayer player, Component text) {
		player.sendOverlayMessage(text);
	}

	/** Whether the player's respawn point is the bed at {@code pos} in {@code dimension}. */
	public static boolean isSpawnAt(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos) {
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		return respawn != null && respawn.respawnData().globalPos().equals(GlobalPos.of(dimension, pos));
	}

	// --- Advancements (the holder type changed in 1.20.2, so it never leaves this class) --------------------------

	public static boolean hasAdvancement(MinecraftServer server, String path) {
		return advancement(server, path) != null;
	}

	/** Grants one criterion; does nothing if the advancement is missing (a data pack removed it). */
	public static void award(ServerPlayer player, String path, String criterion) {
		AdvancementHolder holder = advancement(server(player), path);
		if (holder != null) {
			player.getAdvancements().award(holder, criterion);
		}
	}

	public static void revoke(ServerPlayer player, String path, String criterion) {
		AdvancementHolder holder = advancement(server(player), path);
		if (holder != null) {
			player.getAdvancements().revoke(holder, criterion);
		}
	}

	public static boolean isDone(ServerPlayer player, String path) {
		AdvancementHolder holder = advancement(server(player), path);
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	private static AdvancementHolder advancement(MinecraftServer server, String path) {
		return server.getAdvancements().get(Identifier.fromNamespaceAndPath(CrouchToSetRespawn.MOD_ID, path));
	}
}
