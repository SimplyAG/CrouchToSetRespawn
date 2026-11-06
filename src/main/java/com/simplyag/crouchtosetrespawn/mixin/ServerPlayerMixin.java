package com.simplyag.crouchtosetrespawn.mixin;

import com.mojang.datafixers.util.Either;
import com.simplyag.crouchtosetrespawn.SpawnHint;
import com.simplyag.crouchtosetrespawn.UndercoverSleeper;
import com.simplyag.crouchtosetrespawn.util.Compat;
import com.simplyag.crouchtosetrespawn.util.SleptInBedGate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=26.3 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
//?} else {
/*import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
*///?}
//? if <26.3 && >=1.21.5 {
/*import net.minecraft.server.level.ServerPlayer.RespawnConfig;
*///?} else if <1.21.5 {
/*import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
*///?}

/**
 * Beds only set spawn on a sneak + use. Everything else in startSleepInBed (range, obstruction, the night-only
 * message, monsters, sleeping) stays vanilla. Nether/End beds explode in the bed block's use method before this
 * method is ever reached, so sneaking can't bypass that.
 *
 * <p>26.3 decides "set spawn here" once, in BedRule.canSetSpawn, and the same answer gates the slept_in_bed
 * trigger (Sweet Dreams), so wrapping it covers both. Earlier versions call setRespawnPosition directly and fire
 * slept_in_bed on every sleep, so there the call itself is skipped and SleptInBedGate holds back the trigger.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	/** Set when vanilla would have moved spawn here but the player wasn't sneaking. Read at RETURN. */
	@Unique
	private boolean crouchtosetrespawn$spawnSkipped;
	/** Set when vanilla moved spawn here because the player was sneaking. Read at RETURN. */
	@Unique
	private boolean crouchtosetrespawn$spawnSneakSet;

	/** Records vanilla's decision for this bed use; returns whether spawn may be set. */
	@Unique
	private boolean crouchtosetrespawn$decide(boolean vanillaSets) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		boolean sneaking = Compat.isSneaking(self);
		this.crouchtosetrespawn$spawnSkipped = vanillaSets && !sneaking;
		this.crouchtosetrespawn$spawnSneakSet = vanillaSets && sneaking;
		if (this.crouchtosetrespawn$spawnSkipped) {
			SleptInBedGate.hold(self);
		}
		return vanillaSets && sneaking;
	}

	//? if >=26.3 {
	@WrapOperation(method = "startSleepInBed",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/BedRule;canSetSpawn(Lnet/minecraft/world/level/Level;)Z"))
	private boolean crouchtosetrespawn$onlyWhenSneaking(BedRule rule, Level level, Operation<Boolean> original) {
		// The vanilla rule still has to allow it (straw beds, datapack rules); sneaking only narrows it.
		return this.crouchtosetrespawn$decide(original.call(rule, level));
	}
	//?} else if >=1.21.5 {
	/*@WrapWithCondition(method = "startSleepInBed",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setRespawnPosition(Lnet/minecraft/server/level/ServerPlayer$RespawnConfig;Z)V"))
	private boolean crouchtosetrespawn$onlyWhenSneaking(ServerPlayer self, RespawnConfig config, boolean sendMessage) {
		return this.crouchtosetrespawn$decide(true);
	}
	*///?} else {
	/*@WrapWithCondition(method = "startSleepInBed",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setRespawnPosition(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;FZZ)V"))
	private boolean crouchtosetrespawn$onlyWhenSneaking(ServerPlayer self, ResourceKey<Level> dimension, BlockPos pos, float angle,
		boolean forced, boolean sendMessage) {
		return this.crouchtosetrespawn$decide(true);
	}
	*///?}

	@Inject(method = "startSleepInBed", at = @At("RETURN"))
	//? if >=26.3 {
	private void crouchtosetrespawn$afterBedUse(AbstractBedBlock bedBlock, BlockState bedBlockState, BedRule rule, BlockPos pos,
		CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
	//?} else
	/*private void crouchtosetrespawn$afterBedUse(BlockPos pos, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {*/
		ServerPlayer self = (ServerPlayer) (Object) this;
		boolean skipped = this.crouchtosetrespawn$spawnSkipped;
		boolean sneakSet = this.crouchtosetrespawn$spawnSneakSet;
		this.crouchtosetrespawn$spawnSkipped = false;
		this.crouchtosetrespawn$spawnSneakSet = false;
		SleptInBedGate.release(self);

		// Too far or obstructed: these return before spawn is set, with or without sneaking. Every other problem
		// (not night, monsters nearby) comes after it.
		Player.BedSleepingProblem problem = cir.getReturnValue().left().orElse(null);
		if (Player.BedSleepingProblem.TOO_FAR_AWAY.equals(problem) || Player.BedSleepingProblem.OBSTRUCTED.equals(problem)) {
			return;
		}

		if (sneakSet) {
			UndercoverSleeper.award(self);
		}
		if (skipped && !Compat.isSpawnAt(self, Compat.level(self).dimension(), pos)) {
			SpawnHint.maybeShow(self, pos);
		}
	}
}
