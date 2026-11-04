package com.simplyag.crouchtosetrespawn.mixin;

import com.simplyag.crouchtosetrespawn.SpawnHint;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The hint's tick and stop hooks: the mod's own, so it needs no Fabric API events on any Minecraft version. */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
	@Inject(method = "tickServer", at = @At("TAIL"))
	private void crouchtosetrespawn$tickHint(CallbackInfo ci) {
		SpawnHint.tick((MinecraftServer) (Object) this);
	}

	@Inject(method = "stopServer", at = @At("TAIL"))
	private void crouchtosetrespawn$resetHint(CallbackInfo ci) {
		SpawnHint.reset();
	}
}
