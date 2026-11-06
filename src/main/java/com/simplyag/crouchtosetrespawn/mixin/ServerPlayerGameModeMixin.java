package com.simplyag.crouchtosetrespawn.mixin;

import com.simplyag.crouchtosetrespawn.ModConfig;
import com.simplyag.crouchtosetrespawn.util.Compat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=26.2 {
import net.minecraft.advancements.triggers.CriteriaTriggers;
//?} else
/*import net.minecraft.advancements.CriteriaTriggers;*/
//? if >=26.3 {
import net.minecraft.world.level.block.AbstractBedBlock;
//?} else
/*import net.minecraft.world.level.block.BedBlock;*/

/**
 * sneak_with_items_sets_spawn: vanilla skips the bed on a sneak-click whenever either hand holds anything. When
 * neither held item did anything on the bed, use the bed after all, so a shield or map in the offhand doesn't block
 * setting spawn. "Did nothing" includes FAIL: a torch can't attach to a bed, so it fails rather than passes.
 *
 * <p>A vanilla client sends one use-on-block packet per hand, main hand first, and only sends the offhand one if
 * the main hand did nothing. So the offhand packet is the last chance for an item to act; stepping in there means a
 * held block in either hand still gets placed exactly as in vanilla.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
	/** Game time of the last bed use this mixin made, to swallow the client's follow-up use-item packet. */
	@Unique
	private long crouchtosetrespawn$bedUsedAt = Long.MIN_VALUE;

	@Inject(method = "useItemOn", at = @At("RETURN"), cancellable = true)
	private void crouchtosetrespawn$useBedDespiteItems(ServerPlayer player, Level level, ItemStack itemStack, InteractionHand hand,
		BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
		if (!ModConfig.sneakWithItemsSetsSpawn || hand != InteractionHand.OFF_HAND || cir.getReturnValue().consumesAction()
			|| player.isSpectator() || !Compat.isSneaking(player)) {
			return;
		}
		// Both hands empty means vanilla already used the bed; this is only for the case it skipped.
		if (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
			return;
		}

		BlockState state = level.getBlockState(hitResult.getBlockPos());
		//? if >=26.3 {
		if (!(state.getBlock() instanceof AbstractBedBlock)) {
		//?} else
		/*if (!(state.getBlock() instanceof BedBlock)) {*/
			return;
		}

		InteractionResult use = state.useWithoutItem(level, player, hitResult);
		if (use.consumesAction()) {
			CriteriaTriggers.DEFAULT_BLOCK_USE.trigger(player, hitResult.getBlockPos());
			this.crouchtosetrespawn$bedUsedAt = level.getGameTime();
			cir.setReturnValue(use);
		}
	}

	/**
	 * The client predicted nothing happened, so straight after the offhand use-on-block it sends a plain use for the
	 * offhand item. Drop that one, so a shield isn't raised or food eaten while getting into bed.
	 */
	@Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
	private void crouchtosetrespawn$skipFollowUpUse(ServerPlayer player, Level level, ItemStack itemStack, InteractionHand hand,
		CallbackInfoReturnable<InteractionResult> cir) {
		if (hand == InteractionHand.OFF_HAND && this.crouchtosetrespawn$bedUsedAt == level.getGameTime()) {
			this.crouchtosetrespawn$bedUsedAt = Long.MIN_VALUE;
			cir.setReturnValue(InteractionResult.PASS);
		}
	}
}
