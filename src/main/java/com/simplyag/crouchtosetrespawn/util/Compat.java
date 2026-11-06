package com.simplyag.crouchtosetrespawn.util;

import com.simplyag.crouchtosetrespawn.CrouchToSetRespawn;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
//? if >=1.16
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
//? if >=1.20.2 {
import net.minecraft.advancements.AdvancementHolder;
//?} else
/*import net.minecraft.advancements.Advancement;*/
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else
/*import net.minecraft.resources.ResourceLocation;*/
//? if >=1.21.9
import net.minecraft.core.GlobalPos;
//? if >=1.20
import net.minecraft.world.entity.Entity;
//? if <1.19
/*import net.minecraft.network.chat.TextComponent;*/

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
		//? if >=1.20 {
		// Called through Entity on purpose: ServerPlayer's own level()/serverLevel() changed in 1.21.6, but
		// Entity.level() has been the same method since 1.20, so this compiles to the same call on all of them.
		return (ServerLevel) ((Entity) player).level();
		//?} else
		/*return player.getLevel();*/
	}

	/** Time of day in ticks, counting up across days (one day is 24000). */
	public static long dayTime(ServerLevel level) {
		//? if >=26.1 {
		return level.getDefaultClockTime();
		//?} else
		/*return level.getDayTime();*/
	}

	/** "Use while sneaking" in vanilla's own sense: the key that makes block use pass to the held item. */
	public static boolean isSneaking(ServerPlayer player) {
		//? if >=1.15 {
		return player.isSecondaryUseActive();
		//?} else
		/*return player.isSneaking();*/
	}

	/** Whether a use did something (1.14 has only SUCCESS for that; CONSUME came in 1.15). */
	public static boolean consumesAction(InteractionResult result) {
		//? if >=1.15 {
		return result.consumesAction();
		//?} else
		/*return result == InteractionResult.SUCCESS;*/
	}

	public static Component literal(String text) {
		//? if >=1.19 {
		return Component.literal(text);
		//?} else
		/*return new TextComponent(text);*/
	}

	/** Text above the hotbar. */
	public static void sendOverlay(ServerPlayer player, Component text) {
		//? if >=26.1 {
		player.sendOverlayMessage(text);
		//?} else
		/*player.displayClientMessage(text, true);*/
	}

	/**
	 * The chat line vanilla 1.16+ sends when a bed sets spawn, for the versions where the mod sets it (1.14) or where
	 * vanilla set it silently (1.15). Literal text: those clients have no "block.minecraft.set_spawn" translation.
	 */
	//? if <1.16 {
	/*public static void sendRespawnSet(ServerPlayer player) {
		player.displayClientMessage(literal("Respawn point set"), false);
	}
	*///?}

	/**
	 * Whether the player's respawn point is the bed at {@code pos} in {@code level}. Before 1.16 a respawn point
	 * has no dimension (beds only work in the Overworld), so only the position is compared there.
	 */
	public static boolean isSpawnAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
		//? if >=1.16
		ResourceKey<Level> dimension = level.dimension();
		//? if >=1.21.9 {
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		return respawn != null && respawn.respawnData().globalPos().equals(GlobalPos.of(dimension, pos));
		//?} else if >=1.21.5 {
		/*ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		return respawn != null && respawn.dimension() == dimension && pos.equals(respawn.pos());
		*///?} else if >=1.16 {
		/*return player.getRespawnDimension() == dimension && pos.equals(player.getRespawnPosition());
		*///?} else {
		/*return pos.equals(player.getRespawnPosition());
		*///?}
	}

	// --- Advancements (AdvancementHolder replaced Advancement in 1.20.2, so neither leaves this class) ------------

	public static boolean hasAdvancement(MinecraftServer server, String path) {
		return advancement(server, path) != null;
	}

	/** Grants one criterion; does nothing if the advancement is missing (a data pack removed it). */
	public static void award(ServerPlayer player, String path, String criterion) {
		//? if >=1.20.2 {
		AdvancementHolder advancement = advancement(server(player), path);
		//?} else
		/*Advancement advancement = advancement(server(player), path);*/
		if (advancement != null) {
			player.getAdvancements().award(advancement, criterion);
		}
	}

	public static void revoke(ServerPlayer player, String path, String criterion) {
		//? if >=1.20.2 {
		AdvancementHolder advancement = advancement(server(player), path);
		//?} else
		/*Advancement advancement = advancement(server(player), path);*/
		if (advancement != null) {
			player.getAdvancements().revoke(advancement, criterion);
		}
	}

	public static boolean isDone(ServerPlayer player, String path) {
		//? if >=1.20.2 {
		AdvancementHolder advancement = advancement(server(player), path);
		//?} else
		/*Advancement advancement = advancement(server(player), path);*/
		return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
	}

	//? if >=1.20.2 {
	private static AdvancementHolder advancement(MinecraftServer server, String path) {
	//?} else
	/*private static Advancement advancement(MinecraftServer server, String path) {*/
		//? if >=1.21.11 {
		return server.getAdvancements().get(Identifier.fromNamespaceAndPath(CrouchToSetRespawn.MOD_ID, path));
		//?} else if >=1.21 {
		/*return server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(CrouchToSetRespawn.MOD_ID, path));
		*///?} else if >=1.20.2 {
		/*return server.getAdvancements().get(new ResourceLocation(CrouchToSetRespawn.MOD_ID, path));
		*///?} else {
		/*return server.getAdvancements().getAdvancement(new ResourceLocation(CrouchToSetRespawn.MOD_ID, path));
		*///?}
	}
}
