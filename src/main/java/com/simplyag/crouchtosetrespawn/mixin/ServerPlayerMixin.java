package com.simplyag.crouchtosetrespawn.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import com.simplyag.crouchtosetrespawn.SpawnHint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Beds only set spawn on a sneak + use. Everything else in startSleepInBed (range, obstruction, the night-only
 * message, monsters, sleeping) stays vanilla. Nether/End beds explode in AbstractBedBlock.useWithoutItem before this
 * method is ever reached, so sneaking can't bypass that.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	/** Set when vanilla would have moved spawn here but the player wasn't sneaking. Read at RETURN. */
	@Unique
	private boolean crouchtosetrespawn$spawnSkipped;

	@WrapOperation(method = "startSleepInBed",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/BedRule;canSetSpawn(Lnet/minecraft/world/level/Level;)Z"))
	private boolean crouchtosetrespawn$onlyWhenSneaking(BedRule rule, Level level, Operation<Boolean> original) {
		// The vanilla rule still has to allow it (straw beds, datapack rules); sneaking only narrows it. The same
		// local also gates the slept_in_bed advancement trigger, so that follows spawn being set.
		boolean vanillaSets = original.call(rule, level);
		boolean sneaking = ((ServerPlayer) (Object) this).isSecondaryUseActive();
		this.crouchtosetrespawn$spawnSkipped = vanillaSets && !sneaking;
		return vanillaSets && sneaking;
	}

	@Inject(method = "startSleepInBed", at = @At("RETURN"))
	private void crouchtosetrespawn$hint(AbstractBedBlock bedBlock, BlockState bedBlockState, BedRule rule, BlockPos pos,
		CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
		if (!this.crouchtosetrespawn$spawnSkipped) {
			return;
		}
		this.crouchtosetrespawn$spawnSkipped = false;

		// Too far or obstructed: vanilla wouldn't have set spawn either, so there's nothing to hint about.
		Player.BedSleepingProblem problem = cir.getReturnValue().left().orElse(null);
		if (Player.BedSleepingProblem.TOO_FAR_AWAY.equals(problem) || Player.BedSleepingProblem.OBSTRUCTED.equals(problem)) {
			return;
		}

		ServerPlayer self = (ServerPlayer) (Object) this;
		ServerPlayer.RespawnConfig respawn = self.getRespawnConfig();
		if (respawn != null && respawn.respawnData().globalPos().equals(GlobalPos.of(self.level().dimension(), pos))) {
			return;
		}

		SpawnHint.maybeShow(self);
	}
}
