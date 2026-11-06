package com.simplyag.crouchtosetrespawn.mixin;

import com.simplyag.crouchtosetrespawn.util.SleptInBedGate;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.2 {
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.PlayerTrigger;
//?} else if >=1.21.11 {
/*import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.criterion.PlayerTrigger;
*///?} else if >=1.19 {
/*import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.PlayerTrigger;
*///?} else {
/*import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.LocationTrigger;
*///?}

/** See SleptInBedGate: skips slept_in_bed for a sleep that didn't set spawn (on 26.3+ vanilla already does). */
// Before 1.19, slept_in_bed was a LocationTrigger with the same trigger(ServerPlayer).
//? if >=1.19 {
@Mixin(PlayerTrigger.class)
//?} else
/*@Mixin(LocationTrigger.class)*/
abstract class PlayerTriggerMixin {
	@Inject(method = "trigger", at = @At("HEAD"), cancellable = true)
	private void crouchtosetrespawn$onlyWhenSpawnSet(ServerPlayer player, CallbackInfo ci) {
		if ((Object) this == CriteriaTriggers.SLEPT_IN_BED && SleptInBedGate.isHeld(player)) {
			ci.cancel();
		}
	}
}
