package com.simplyag.crouchtosetrespawn.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.simplyag.crouchtosetrespawn.SpawnHint;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The hint's join and leave hooks. Both run on the server thread on every Minecraft version. */
@Mixin(PlayerList.class)
abstract class PlayerListMixin {
	/**
	 * Clear a hint saved as done (a crash or autosave inside its 2-tick window). placeNewPlayer's other arguments
	 * changed over the versions, so only the player is taken, by type.
	 */
	@Inject(method = "placeNewPlayer", at = @At("TAIL"))
	private void crouchtosetrespawn$revokeHintOnJoin(CallbackInfo ci, @Local(argsOnly = true) ServerPlayer player) {
		SpawnHint.revoke(player);
	}

	/**
	 * Revoke the hint before a leaving player is saved, so a logout inside its 2-tick window can't save it as done.
	 * This always runs on the server thread, just before PlayerList.remove saves (a disconnect event can fire on the
	 * network thread when the client drops the connection).
	 */
	@Inject(method = "remove", at = @At("HEAD"))
	private void crouchtosetrespawn$revokeHintBeforeSave(ServerPlayer player, CallbackInfo ci) {
		SpawnHint.revoke(player);
	}
}
