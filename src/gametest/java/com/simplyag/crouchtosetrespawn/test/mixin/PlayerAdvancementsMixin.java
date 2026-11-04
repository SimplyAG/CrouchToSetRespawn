package com.simplyag.crouchtosetrespawn.test.mixin;

import com.simplyag.crouchtosetrespawn.SpawnHint;
import com.simplyag.crouchtosetrespawn.test.Recorder;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
abstract class PlayerAdvancementsMixin {
	@Shadow
	private ServerPlayer player;

	@Inject(method = "award", at = @At("RETURN"))
	private void crouchtosetrespawn_test$countHint(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && holder.id().equals(com.simplyag.crouchtosetrespawn.test.Ids.mod(SpawnHint.ADVANCEMENT))) {
			Recorder.HINT_GRANTS.computeIfAbsent(this.player.getUUID(), id -> new AtomicInteger()).incrementAndGet();
		}
	}
}
