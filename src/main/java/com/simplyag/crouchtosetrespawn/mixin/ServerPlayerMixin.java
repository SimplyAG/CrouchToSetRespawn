package com.simplyag.crouchtosetrespawn.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import com.simplyag.crouchtosetrespawn.SpawnHint;
import com.simplyag.crouchtosetrespawn.UndercoverSleeper;
import com.simplyag.crouchtosetrespawn.util.Compat;
import net.minecraft.core.BlockPos;
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
 * message, monsters, sleeping) stays vanilla. Nether/End beds explode in the bed block's use method before this
 * method is ever reached, so sneaking can't bypass that.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	/** Set when vanilla would have moved spawn here but the player wasn't sneaking. Read at RETURN. */
	@Unique
	private boolean crouchtosetrespawn$spawnSkipped;
	/** Set when vanilla allows moving spawn here and the player was sneaking. Read at RETURN. */
	@Unique
	private boolean crouchtosetrespawn$spawnSneakSet;

	@WrapOperation(method = "startSleepInBed",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/BedRule;canSetSpawn(Lnet/minecraft/world/level/Level;)Z"))
	private boolean crouchtosetrespawn$onlyWhenSneaking(BedRule rule, Level level, Operation<Boolean> original) {
		// The vanilla rule still has to allow it (straw beds, datapack rules); sneaking only narrows it. The same
		// local also gates the slept_in_bed advancement trigger, so that follows spawn being set.
		boolean vanillaSets = original.call(rule, level);
		boolean sneaking = Compat.isSneaking((ServerPlayer) (Object) this);
		this.crouchtosetrespawn$spawnSkipped = vanillaSets && !sneaking;
		this.crouchtosetrespawn$spawnSneakSet = vanillaSets && sneaking;
		return vanillaSets && sneaking;
	}

	@Inject(method = "startSleepInBed", at = @At("RETURN"))
	private void crouchtosetrespawn$afterBedUse(AbstractBedBlock bedBlock, BlockState bedBlockState, BedRule rule, BlockPos pos,
		CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
		boolean skipped = this.crouchtosetrespawn$spawnSkipped;
		boolean sneakSet = this.crouchtosetrespawn$spawnSneakSet;
		this.crouchtosetrespawn$spawnSkipped = false;
		this.crouchtosetrespawn$spawnSneakSet = false;

		// Too far or obstructed: these return before spawn is set, with or without sneaking. Every other problem
		// (not night, monsters nearby) comes after it.
		Player.BedSleepingProblem problem = cir.getReturnValue().left().orElse(null);
		if (Player.BedSleepingProblem.TOO_FAR_AWAY.equals(problem) || Player.BedSleepingProblem.OBSTRUCTED.equals(problem)) {
			return;
		}

		ServerPlayer self = (ServerPlayer) (Object) this;
		if (sneakSet) {
			UndercoverSleeper.award(self);
		}
		if (skipped && !Compat.isSpawnAt(self, Compat.level(self).dimension(), pos)) {
			SpawnHint.maybeShow(self, pos);
		}
	}
}
