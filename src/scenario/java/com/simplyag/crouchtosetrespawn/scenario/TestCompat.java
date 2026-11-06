package com.simplyag.crouchtosetrespawn.scenario;

import com.mojang.authlib.GameProfile;
import com.simplyag.crouchtosetrespawn.util.Compat;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.network.Connection;
//? if >=1.20.2 && <1.20.5
/*import net.minecraft.network.ConnectionProtocol;*/
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
//? if <1.17
/*import net.minecraft.server.level.ServerPlayerGameMode;*/
//? if >=1.16 {
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
//?} else {
/*import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.dimension.DimensionType;
*///?}
//? if >=1.20.2 {
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.network.CommonListenerCookie;
//?} else
/*import net.minecraft.advancements.Advancement;*/
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else
/*import net.minecraft.resources.ResourceLocation;*/

/** The scenario harness's own version differences (the mod's are in util.Compat, which the harness also uses). */
final class TestCompat {
	private TestCompat() {
	}

	/**
	 * A real ServerPlayer that joined through PlayerList.placeNewPlayer, the way a client does, over an in-memory
	 * connection. So the mod's join hook runs, and everything sent to it (messages, advancements) is real packets.
	 */
	static ServerPlayer joinPlayer(MinecraftServer server, String name) {
		GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("scenario:" + name).getBytes()), name);
		//? if >=1.16 {
		ServerLevel overworld = server.overworld();
		//?} else
		/*ServerLevel overworld = server.getLevel(DimensionType.OVERWORLD);*/
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		//? if >=1.20.2 && <1.20.5 {
		/*// These versions encode packets with the protocol stored on the channel, which a real login would have set.
		channel.attr(Connection.ATTRIBUTE_SERVERBOUND_PROTOCOL).set(ConnectionProtocol.PLAY.codec(PacketFlow.SERVERBOUND));
		channel.attr(Connection.ATTRIBUTE_CLIENTBOUND_PROTOCOL).set(ConnectionProtocol.PLAY.codec(PacketFlow.CLIENTBOUND));
		*///?}
		//? if >=1.20.5 {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		ServerPlayer player = new ServerPlayer(server, overworld, profile, cookie.clientInformation());
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		//?} else if >=1.20.2 {
		/*CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile);
		ServerPlayer player = new ServerPlayer(server, overworld, profile, cookie.clientInformation());
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		*///?} else if >=1.19.3 {
		/*ServerPlayer player = new ServerPlayer(server, overworld, profile);
		server.getPlayerList().placeNewPlayer(connection, player);
		*///?} else if >=1.19 {
		/*ServerPlayer player = new ServerPlayer(server, overworld, profile, null);
		server.getPlayerList().placeNewPlayer(connection, player);
		*///?} else if >=1.17 {
		/*ServerPlayer player = new ServerPlayer(server, overworld, profile);
		server.getPlayerList().placeNewPlayer(connection, player);
		*///?} else {
		/*ServerPlayer player = new ServerPlayer(server, overworld, profile, new ServerPlayerGameMode(overworld));
		server.getPlayerList().placeNewPlayer(connection, player);
		*///?}
		return player;
	}

	static void command(MinecraftServer server, String command) {
		//? if >=1.19 {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
		//?} else
		/*server.getCommands().performCommand(server.createCommandSourceStack().withSuppressedOutput(), command);*/
	}

	/** Whether a vanilla advancement (e.g. "adventure/sleep_in_bed") is done. */
	static boolean vanillaDone(ServerPlayer player, String path) {
		//? if >=1.21.11 {
		Identifier id = Identifier.withDefaultNamespace(path);
		//?} else if >=1.21 {
		/*ResourceLocation id = ResourceLocation.withDefaultNamespace(path);
		*///?} else {
		/*ResourceLocation id = new ResourceLocation(path);
		*///?}
		//? if >=1.20.2 {
		AdvancementHolder advancement = Compat.server(player).getAdvancements().get(id);
		//?} else
		/*Advancement advancement = Compat.server(player).getAdvancements().getAdvancement(id);*/
		return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
	}

	static boolean isNether(ServerLevel level) {
		//? if >=1.16 {
		return level.dimension() == Level.NETHER;
		//?} else
		/*return level.getDimension().getType() == DimensionType.NETHER;*/
	}

	static void setSneaking(ServerPlayer player, boolean sneaking) {
		//? if >=1.15 {
		player.setShiftKeyDown(sneaking);
		//?} else
		/*player.setSneaking(sneaking);*/
	}

	static boolean isBed(BlockState state) {
		//? if >=1.16 {
		return state.is(BlockTags.BEDS);
		//?} else
		/*return state.getBlock() instanceof BedBlock;*/
	}

	/** Wakes the player the way the end of a slept-through night does (on 1.14 that is what sets spawn). */
	static void wakeInTheMorning(ServerPlayer player) {
		//? if >=1.15 {
		player.stopSleepInBed(false, true);
		//?} else
		/*player.stopSleepInBed(false, false, true);*/
	}

	/** Gets the player out of bed without touching spawn. */
	static void wake(ServerPlayer player) {
		//? if >=1.15 {
		player.stopSleepInBed(false, true);
		//?} else
		/*player.stopSleepInBed(false, true, false);*/
	}
}
