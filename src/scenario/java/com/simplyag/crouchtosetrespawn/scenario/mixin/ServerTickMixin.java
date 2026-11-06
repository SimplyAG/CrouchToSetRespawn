package com.simplyag.crouchtosetrespawn.scenario.mixin;

import com.simplyag.crouchtosetrespawn.scenario.ScenarioRunner;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drives the scenario harness from the end of every server tick. */
@Mixin(MinecraftServer.class)
abstract class ServerTickMixin {
	@Inject(method = "tickServer", at = @At("TAIL"))
	private void crouchtosetrespawn_scenario$tick(CallbackInfo ci) {
		ScenarioRunner.tick((MinecraftServer) (Object) this);
	}
}
