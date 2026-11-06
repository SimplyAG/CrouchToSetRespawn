package com.simplyag.crouchtosetrespawn.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.16 && >=1.15 {
/*import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.simplyag.crouchtosetrespawn.util.BedUseDecider;
import com.simplyag.crouchtosetrespawn.util.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.injection.At;
*///?} else if <1.15 {
/*import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
*///?}

/**
 * Before 1.16 vanilla sets spawn in Player, not ServerPlayer (ServerPlayerMixin has the whole picture). From 1.16
 * on this mixin is empty.
 */
@Mixin(Player.class)
abstract class PlayerMixin {
	//? if <1.16 && >=1.15 {
	/*// 1.15 sets spawn in startSleepInBed's daytime branch and in startSleeping at night. On the server both run inside
	// ServerPlayer.startSleepInBed, so ServerPlayerMixin's RETURN sees the decision.
	@WrapWithCondition(method = {"startSleepInBed", "startSleeping"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setRespawnPosition(Lnet/minecraft/core/BlockPos;ZZ)V"))
	private boolean crouchtosetrespawn$onlyWhenSneaking(Player self, BlockPos pos, boolean forced, boolean sendMessage) {
		if (!((Object) this instanceof BedUseDecider)) {
			return true;
		}
		boolean allowed = ((BedUseDecider) (Object) this).crouchtosetrespawn$decide(true);
		if (allowed) {
			// 1.15 sets spawn silently; later versions say so, and so does the mod.
			Compat.sendRespawnSet((ServerPlayer) (Object) this);
		}
		return allowed;
	}
	*///?} else if <1.15 {
	/*// 1.14 sets spawn when waking up after a full night; ServerPlayerMixin sets it on a sneak + use instead.
	@ModifyVariable(method = "stopSleepInBed", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private boolean crouchtosetrespawn$neverOnWakeUp(boolean setSpawn) {
		return false;
	}
	*///?}
}
