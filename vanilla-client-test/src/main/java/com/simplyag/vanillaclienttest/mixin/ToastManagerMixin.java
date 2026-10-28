package com.simplyag.vanillaclienttest.mixin;

import com.simplyag.vanillaclienttest.VanillaClientJoinTest;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
abstract class ToastManagerMixin {
	@Inject(method = "addToast", at = @At("HEAD"))
	private void vanillaclienttest$record(Toast toast, CallbackInfo ci) {
		if (toast instanceof AdvancementToast advancementToast) {
			VanillaClientJoinTest.ADVANCEMENT_TOASTS.add(((AdvancementToastAccessor) advancementToast).vanillaclienttest$advancement());
		}
	}
}
