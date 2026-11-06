package com.simplyag.crouchtosetrespawn.scenario;

import com.mojang.authlib.GameProfile;
import com.simplyag.crouchtosetrespawn.util.Compat;
import net.minecraft.advancements.AdvancementHolder;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
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
		ServerLevel overworld = server.overworld();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		ServerPlayer player = new ServerPlayer(server, overworld, profile, cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		return player;
	}

	static void command(MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}

	/** Whether a vanilla advancement (e.g. "adventure/sleep_in_bed") is done. */
	static boolean vanillaDone(ServerPlayer player, String path) {
		//? if >=1.21.11 {
		Identifier id = Identifier.withDefaultNamespace(path);
		//?} else
		/*ResourceLocation id = ResourceLocation.withDefaultNamespace(path);*/
		AdvancementHolder holder = Compat.server(player).getAdvancements().get(id);
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}
}
