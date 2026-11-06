package com.simplyag.crouchtosetrespawn.scenario.mixin;

import com.simplyag.crouchtosetrespawn.scenario.ScenarioRunner;
import com.simplyag.crouchtosetrespawn.util.Compat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records every advancement grant and action bar message the mod sends, so the scenarios can count hint toasts and
 * hint texts. The mod sends both through Compat on every Minecraft version, so one spy works everywhere.
 */
@Mixin(value = Compat.class, remap = false)
abstract class CompatSpyMixin {
	@Inject(method = "award", at = @At("HEAD"))
	private static void crouchtosetrespawn_scenario$award(ServerPlayer player, String path, String criterion, CallbackInfo ci) {
		ScenarioRunner.recordAward(player, path);
	}

	@Inject(method = "sendOverlay", at = @At("HEAD"))
	private static void crouchtosetrespawn_scenario$overlay(ServerPlayer player, Component text, CallbackInfo ci) {
		ScenarioRunner.recordOverlay(player, text.getString());
	}
}
