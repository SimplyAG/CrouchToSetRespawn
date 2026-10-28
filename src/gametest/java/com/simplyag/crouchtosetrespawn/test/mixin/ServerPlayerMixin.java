package com.simplyag.crouchtosetrespawn.test.mixin;

import com.simplyag.crouchtosetrespawn.test.Recorder;
import java.util.ArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	/** Chat and overlay messages both end up here. */
	@Inject(method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"))
	private void crouchtosetrespawn_test$record(Component message, boolean overlay, CallbackInfo ci) {
		Recorder.MESSAGES.computeIfAbsent(((ServerPlayer) (Object) this).getUUID(), id -> new ArrayList<>()).add(message);
	}
}
