package com.simplyag.crouchtosetrespawn.mixin;

import com.mojang.datafixers.util.Either;
import com.simplyag.crouchtosetrespawn.SpawnHint;
import com.simplyag.crouchtosetrespawn.UndercoverSleeper;
import com.simplyag.crouchtosetrespawn.util.BedUseDecider;
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
//?} else if >=1.16 {
/*import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
*///?}
//? if <26.3 && >=1.21.5 {
/*import net.minecraft.server.level.ServerPlayer.RespawnConfig;
*///?} else if <1.21.5 && >=1.16 {
/*import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
*///?}

/**
 * Beds only set spawn on a sneak + use. Everything else in startSleepInBed (range, obstruction, the night-only
 * message, monsters, sleeping) stays vanilla. Nether/End beds explode in the bed block's use method before this
 * method is ever reached, so sneaking can't bypass that.
 *
 * <p>Where vanilla decides to set spawn, by version:
 * <ul>
 * <li>26.3: once, in BedRule.canSetSpawn, and the same answer gates the slept_in_bed trigger (Sweet Dreams).</li>
 * <li>1.16 - 26.2: a setRespawnPosition call in this method (its arguments changed in 1.16.2, 1.21.5, 1.21.9).</li>
 * <li>1.15: two setRespawnPosition calls in Player (day and night branches), wrapped by PlayerMixin.</li>
 * <li>1.14: only on waking up after a full night. PlayerMixin turns that off, and a sneak + use sets spawn here
 * instead, in the same cases later versions do.</li>
 * </ul>
 * Before 26.3 vanilla fires slept_in_bed on every sleep, so SleptInBedGate holds it back unless spawn was set.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin implements BedUseDecider {
	/** Set when vanilla would have moved spawn here but the player wasn't sneaking. Read at RETURN. */
	@Unique
	private boolean crouchtosetrespawn$spawnSkipped;
	/** Set when vanilla moved spawn here because the player was sneaking. Read at RETURN. */
	@Unique
	private boolean crouchtosetrespawn$spawnSneakSet;

	@Override
	public boolean crouchtosetrespawn$decide(boolean vanillaSets) {
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
	*///?} else if >=1.16.2 {
	/*@WrapWithCondition(method = "startSleepInBed",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setRespawnPosition(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;FZZ)V"))
	private boolean crouchtosetrespawn$onlyWhenSneaking(ServerPlayer self, ResourceKey<Level> dimension, BlockPos pos, float angle,
		boolean forced, boolean sendMessage) {
		return this.crouchtosetrespawn$decide(true);
	}
	*///?} else if >=1.16 {
	/*@WrapWithCondition(method = "startSleepInBed",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setRespawnPosition(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;ZZ)V"))
	private boolean crouchtosetrespawn$onlyWhenSneaking(ServerPlayer self, ResourceKey<Level> dimension, BlockPos pos, boolean forced,
		boolean sendMessage) {
		return this.crouchtosetrespawn$decide(true);
	}
	*///?} else if <1.15 {
	/*@Inject(method = "startSleepInBed", at = @At("HEAD"))
	private void crouchtosetrespawn$decideAtStart(BlockPos pos, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
		// 1.14 sets spawn nowhere in this method, so decide up front. RETURN drops the uses later versions wouldn't
		// set spawn for, and Sweet Dreams (fired inside this method) is held back for a normal use.
		this.crouchtosetrespawn$decide(true);
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
		//? if <1.15 {
		/*// What 1.16+ sets spawn for: a successful sleep, or one refused for the time of day or for monsters nearby.
		if (problem != null && problem != Player.BedSleepingProblem.NOT_POSSIBLE_NOW && problem != Player.BedSleepingProblem.NOT_SAFE) {
			return;
		}
		if (sneakSet) {
			self.setRespawnPosition(pos, false);
			Compat.sendRespawnSet(self);
		}
		*///?}

		if (sneakSet) {
			UndercoverSleeper.award(self);
		}
		if (skipped && !Compat.isSpawnAt(self, Compat.level(self), pos)) {
			SpawnHint.maybeShow(self, pos);
		}
	}
}
