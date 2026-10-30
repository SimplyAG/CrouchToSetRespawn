package com.simplyag.crouchtosetrespawn.mixin;

import com.simplyag.crouchtosetrespawn.SpawnHint;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Revoke the hint before a leaving player is saved, so a logout inside its 2-tick window can't save it as done.
 * Fabric's DISCONNECT event isn't used for this: it can fire on the network thread when the client drops the
 * connection, while this always runs on the server thread, just before PlayerList.remove saves.
 */
@Mixin(PlayerList.class)
abstract class PlayerListMixin {
	@Inject(method = "remove", at = @At("HEAD"))
	private void crouchtosetrespawn$revokeHintBeforeSave(ServerPlayer player, CallbackInfo ci) {
		SpawnHint.revoke(player);
	}
}
