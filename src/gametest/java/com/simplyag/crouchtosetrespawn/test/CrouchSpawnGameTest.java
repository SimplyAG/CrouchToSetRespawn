package com.simplyag.crouchtosetrespawn.test;

import com.simplyag.crouchtosetrespawn.SpawnHint;
import com.simplyag.crouchtosetrespawn.UndercoverSleeper;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Runs on the headless dedicated GameTest server (`gradlew runGameTest`). Every click goes through
 * ServerPlayerGameMode.useItemOn, the same path a use-item-on packet from a real client takes.
 */
public class CrouchSpawnGameTest {
	static final String NIGHT = "crouchtosetrespawn-gametest:night";
	static final String DAY = "crouchtosetrespawn-gametest:day";
	static final String NIGHT_ITEMS_NEED_EMPTY_HANDS = "crouchtosetrespawn-gametest:night_items_need_empty_hands";
	static final String NIGHT_NO_HINT = "crouchtosetrespawn-gametest:night_no_hint";
	static final String SET_SPAWN = "block.minecraft.set_spawn";
	static final String NO_SLEEP = "block.minecraft.bed.no_sleep";

	// --- Spawn behavior ---------------------------------------------------------------------------------------

	@GameTest(environment = NIGHT)
	public void normalSleepAtNightDoesNotSetSpawn(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		assertDark(helper, true);

		use(player, bed.foot);

		helper.assertTrue(player.isSleeping(), "normal use at night should sleep");
		helper.assertTrue(player.getRespawnConfig() == null, "normal sleep must not set spawn");
		helper.assertFalse(sawMessage(player, SET_SPAWN), "no 'Respawn point set' on normal sleep");
		done(helper, player);
	}

	@GameTest(environment = DAY)
	public void normalUseDuringDayOnlyShowsMessage(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		assertDark(helper, false);

		use(player, bed.foot);

		helper.assertFalse(player.isSleeping(), "can't sleep during the day");
		helper.assertTrue(player.getRespawnConfig() == null, "normal day use must not set spawn");
		helper.assertTrue(sawMessage(player, NO_SLEEP), "vanilla 'only sleep at night' message");
		helper.assertFalse(sawMessage(player, SET_SPAWN), "no 'Respawn point set' on normal day use");
		done(helper, player);
	}

	@GameTest(environment = NIGHT)
	public void sneakAtNightSetsSpawnAndSleeps(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setShiftKeyDown(true);

		use(player, bed.foot);

		assertSpawnAt(helper, player, bed.head);
		helper.assertTrue(player.isSleeping(), "sneak use at night should still sleep");
		helper.assertTrue(sawMessage(player, SET_SPAWN), "vanilla 'Respawn point set' message");
		done(helper, player);
	}

	@GameTest(environment = DAY)
	public void sneakDuringDaySetsSpawn(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setShiftKeyDown(true);

		use(player, bed.foot);

		assertSpawnAt(helper, player, bed.head);
		helper.assertFalse(player.isSleeping(), "can't sleep during the day");
		helper.assertTrue(sawMessage(player, SET_SPAWN), "vanilla 'Respawn point set' message");
		helper.assertTrue(sawMessage(player, NO_SLEEP), "vanilla 'only sleep at night' message");
		done(helper, player);
	}

	@GameTest(environment = NIGHT)
	public void sneakWithBlockPlacesBlock(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
		player.setShiftKeyDown(true);

		use(player, bed.foot);

		helper.assertTrue(helper.getLevel().getBlockState(bed.foot.above()).is(Blocks.STONE), "sneaking with a block places it on the bed");
		helper.assertTrue(player.getRespawnConfig() == null, "placing a block must not set spawn");
		helper.assertFalse(player.isSleeping(), "placing a block must not sleep");
		done(helper, player);
	}

	// --- Sneaking with items (sneak_with_items_sets_spawn) --------------------------------------------------------
	// These send exactly what a vanilla client sends for one sneak-click (checked against Minecraft.startUseItem):
	// per hand, main first, a use-on-block packet, then a use-item packet if that hand isn't empty, stopping at the
	// first hand the client thinks did something. The client never predicts the mod's bed fallback, so after the
	// offhand use-on-block it still sends the offhand use-item.

	@GameTest(environment = NIGHT)
	public void sneakWithShieldInOffhandSetsSpawn(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		player.setShiftKeyDown(true);

		helper.assertTrue(useOn(player, InteractionHand.MAIN_HAND, bed.foot) == InteractionResult.PASS, "empty main hand does nothing");
		useOn(player, InteractionHand.OFF_HAND, bed.foot);
		useItem(player, InteractionHand.OFF_HAND);

		assertSpawnAt(helper, player, bed.head);
		helper.assertTrue(player.isSleeping(), "sneak use with a shield in the offhand should sleep");
		helper.assertTrue(sawMessage(player, SET_SPAWN), "vanilla 'Respawn point set' message");
		helper.assertFalse(player.isUsingItem(), "the shield must not be raised while getting into bed");
		done(helper, player);
	}

	@GameTest(environment = NIGHT)
	public void sneakWithSwordInMainHandSetsSpawn(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		player.setShiftKeyDown(true);

		helper.assertTrue(useOn(player, InteractionHand.MAIN_HAND, bed.foot) == InteractionResult.PASS, "sword does nothing on a bed");
		useItem(player, InteractionHand.MAIN_HAND);
		useOn(player, InteractionHand.OFF_HAND, bed.foot);

		assertSpawnAt(helper, player, bed.head);
		helper.assertTrue(player.isSleeping(), "sneak use with a sword should sleep");
		done(helper, player);
	}

	@GameTest(environment = NIGHT)
	public void sneakWithFoodInOffhandSetsSpawnWithoutEating(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BREAD));
		player.setShiftKeyDown(true);

		useOn(player, InteractionHand.MAIN_HAND, bed.foot);
		useOn(player, InteractionHand.OFF_HAND, bed.foot);
		useItem(player, InteractionHand.OFF_HAND);

		assertSpawnAt(helper, player, bed.head);
		helper.assertFalse(player.isUsingItem(), "must not start eating while getting into bed");
		done(helper, player);
	}

	@GameTest(environment = NIGHT)
	public void sneakWithTorchInOffhandSetsSpawn(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
		player.setShiftKeyDown(true);

		useOn(player, InteractionHand.MAIN_HAND, bed.foot);
		useOn(player, InteractionHand.OFF_HAND, bed.foot);
		useItem(player, InteractionHand.OFF_HAND);

		// A torch can't attach to a bed, so it does nothing there and the bed gets used.
		assertSpawnAt(helper, player, bed.head);
		helper.assertTrue(player.isSleeping(), "sneak use with a torch in the offhand should sleep");
		helper.assertTrue(helper.getLevel().getBlockState(bed.foot.above()).isAir(), "no torch placed on the bed");
		done(helper, player);
	}

	@GameTest(environment = DAY)
	public void sneakWithShieldDuringDaySetsSpawn(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		player.setShiftKeyDown(true);

		useOn(player, InteractionHand.MAIN_HAND, bed.foot);
		useOn(player, InteractionHand.OFF_HAND, bed.foot);
		useItem(player, InteractionHand.OFF_HAND);

		assertSpawnAt(helper, player, bed.head);
		helper.assertFalse(player.isSleeping(), "can't sleep during the day");
		helper.assertTrue(sawMessage(player, NO_SLEEP), "vanilla 'only sleep at night' message");
		helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 0, "sneak use never shows the hint");
		done(helper, player);
	}

	@GameTest(environment = NIGHT)
	public void sneakWithBlockInOffhandPlacesBlock(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));
		player.setShiftKeyDown(true);

		useOn(player, InteractionHand.MAIN_HAND, bed.foot);
		helper.assertTrue(useOn(player, InteractionHand.OFF_HAND, bed.foot).consumesAction(), "offhand stone gets placed");

		helper.assertTrue(helper.getLevel().getBlockState(bed.foot.above()).is(Blocks.STONE), "offhand block placed on the bed");
		helper.assertTrue(player.getRespawnConfig() == null, "placing a block must not set spawn");
		helper.assertFalse(player.isSleeping(), "placing a block must not sleep");
		done(helper, player);
	}

	@GameTest(environment = NIGHT)
	public void sneakWithBlockInMainHandAndShieldInOffhandPlacesBlock(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		player.setShiftKeyDown(true);

		// The main hand places the stone, so a vanilla client sends nothing for the offhand.
		helper.assertTrue(useOn(player, InteractionHand.MAIN_HAND, bed.foot).consumesAction(), "main-hand stone gets placed");

		helper.assertTrue(helper.getLevel().getBlockState(bed.foot.above()).is(Blocks.STONE), "main-hand block placed on the bed");
		helper.assertTrue(player.getRespawnConfig() == null, "placing a block must not set spawn");
		done(helper, player);
	}

	@GameTest(environment = NIGHT)
	public void normalUseWithShieldStillSleepsWithoutSettingSpawn(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));

		helper.assertTrue(useOn(player, InteractionHand.MAIN_HAND, bed.foot).consumesAction(), "not sneaking: vanilla uses the bed");

		helper.assertTrue(player.isSleeping(), "normal use sleeps");
		helper.assertTrue(player.getRespawnConfig() == null, "normal use must not set spawn");
		done(helper, player);
	}

	@GameTest(environment = NIGHT_ITEMS_NEED_EMPTY_HANDS)
	public void settingOffShieldInOffhandBlocksBed(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		player.setShiftKeyDown(true);

		useOn(player, InteractionHand.MAIN_HAND, bed.foot);
		useOn(player, InteractionHand.OFF_HAND, bed.foot);
		useItem(player, InteractionHand.OFF_HAND);

		helper.assertTrue(player.getRespawnConfig() == null, "with the setting off, vanilla skips the bed when a hand holds anything");
		helper.assertFalse(player.isSleeping(), "with the setting off, sneaking with a shield doesn't sleep");
		helper.assertTrue(player.isUsingItem(), "with the setting off, the shield is raised as in vanilla");
		done(helper, player);
	}

	@GameTest(environment = NIGHT_ITEMS_NEED_EMPTY_HANDS)
	public void settingOffEmptyHandsStillSetSpawn(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setShiftKeyDown(true);

		use(player, bed.foot);

		assertSpawnAt(helper, player, bed.head);
		helper.assertTrue(player.isSleeping(), "empty-handed sneak use works with the setting off too");
		done(helper, player);
	}

	@GameTest(environment = NIGHT)
	public void existingSpawnSurvivesNormalSleep(GameTestHelper helper) {
		Bed bedA = placeBed(helper, 1);
		Bed bedB = placeBed(helper, 5);
		ServerPlayer player = playerBeside(helper, bedA);
		player.setShiftKeyDown(true);
		use(player, bedA.foot);
		assertSpawnAt(helper, player, bedA.head);

		player.stopSleepInBed(false, true);
		player.setShiftKeyDown(false);
		moveBeside(player, bedB);
		use(player, bedB.foot);

		helper.assertTrue(player.isSleeping(), "normal sleep in the second bed");
		assertSpawnAt(helper, player, bedA.head);
		done(helper, player);
	}

	@GameTest
	public void netherBedExplodesWhenSneaking(GameTestHelper helper) {
		bedExplodes(helper, Level.NETHER, true, ItemStack.EMPTY, 0);
	}

	@GameTest
	public void netherBedExplodesWhenNotSneaking(GameTestHelper helper) {
		bedExplodes(helper, Level.NETHER, false, ItemStack.EMPTY, 4);
	}

	@GameTest
	public void netherBedExplodesWhenSneakingWithShield(GameTestHelper helper) {
		bedExplodes(helper, Level.NETHER, true, new ItemStack(Items.SHIELD), 8);
	}

	@GameTest
	public void endBedExplodesWhenSneaking(GameTestHelper helper) {
		bedExplodes(helper, Level.END, true, ItemStack.EMPTY, 200);
	}

	@GameTest
	public void endBedExplodesWhenNotSneaking(GameTestHelper helper) {
		bedExplodes(helper, Level.END, false, ItemStack.EMPTY, 204);
	}

	/**
	 * A bed in a dimension whose bed rule is destroy_on_use, on a netherrack floor (weak enough that the blast
	 * visibly removes it). The packets are what a vanilla client sends for the held items; with an offhand item
	 * the mod's fallback is what reaches the bed, and that must explode it like vanilla too.
	 */
	private void bedExplodes(GameTestHelper helper, ResourceKey<Level> dimension, boolean sneaking, ItemStack offhand, int chunkX) {
		ServerLevel level = helper.getLevel().getServer().getLevel(dimension);
		level.setChunkForced(chunkX, 0, true);
		BlockPos origin = new BlockPos(chunkX * 16 + 4, 110, 4);
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, 0, -4), origin.offset(4, 5, 4))) {
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		}
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, -1, -4), origin.offset(4, -1, 4))) {
			level.setBlockAndUpdate(pos, Blocks.NETHERRACK.defaultBlockState());
		}
		Bed bed = placeBedAt(level, origin);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.teleportTo(level, bed.foot.getX() - 0.5, bed.foot.getY(), bed.foot.getZ() + 0.5, Set.of(), 0, 0, true);
		player.setShiftKeyDown(sneaking);
		player.setItemInHand(InteractionHand.OFF_HAND, offhand);

		player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(bed.foot));
		if (!offhand.isEmpty() && sneaking) {
			player.gameMode.useItemOn(player, level, player.getOffhandItem(), InteractionHand.OFF_HAND, hit(bed.foot));
		}

		helper.assertTrue(level.getBlockState(bed.foot).isAir() && level.getBlockState(bed.head).isAir(), "bed should be destroyed");
		helper.assertTrue(level.getBlockState(bed.head.below()).isAir(), "explosion should have blown out the netherrack under the bed");
		helper.assertTrue(player.getRespawnConfig() == null, "a destroy_on_use bed must never set spawn");
		helper.assertFalse(player.isSleeping(), "a destroy_on_use bed must never sleep");
		helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 0, "no hint for a destroy_on_use bed");
		level.setChunkForced(chunkX, 0, false);
		done(helper, player);
	}

	// --- Hint toast -------------------------------------------------------------------------------------------

	@GameTest(environment = NIGHT)
	public void hintGrantedThenRevokedOnNormalUse(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		AdvancementHolder hint = hint(helper);

		use(player, bed.foot);

		helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 1, "normal use should grant the hint once");
		helper.assertTrue(player.getAdvancements().getOrStartProgress(hint).isDone(), "hint is done right after the click");
		helper.runAfterDelay(4, () -> {
			helper.assertFalse(player.getAdvancements().getOrStartProgress(hint).isDone(), "hint should be revoked again");
			done(helper, player);
		});
	}

	@GameTest(environment = NIGHT)
	public void hintNotShownOnSneakUse(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setShiftKeyDown(true);

		use(player, bed.foot);

		helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 0, "sneak use must never show the hint");
		done(helper, player);
	}

	@GameTest(environment = DAY)
	public void hintNotShownOnSneakUseDuringDay(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setShiftKeyDown(true);

		use(player, bed.foot);

		helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 0, "sneak use must never show the hint");
		done(helper, player);
	}

	@GameTest(environment = NIGHT, maxTicks = 120)
	public void hintThrottledPerNight(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);

		for (int i = 0; i < 3; i++) {
			use(player, bed.foot);
			player.stopSleepInBed(false, true);
		}
		helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 1, "three clicks in one night should show the hint once");

		helper.runAfterDelay(4, () -> {
			// The next evening (still dark, so the bed still sleeps): due again, now as text rather than a toast.
			player.stopSleepInBed(false, true);
			helper.setTime(18000 + 24000);
			refreshTimeOfDay(helper.getLevel());
			use(player, bed.foot);
			helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 1, "only the first ever hint is a toast");
			helper.assertFalse(sawHintText(player), "the text waits for the click's own messages");
			helper.setTime(18000);
			refreshTimeOfDay(helper.getLevel());
			helper.runAfterDelay(SpawnHint.ACTION_BAR_DELAY + 2, () -> {
				helper.assertTrue(sawHintText(player), "the next night should show the hint text");
				done(helper, player);
			});
		});
	}

	@GameTest(environment = DAY, maxTicks = 120)
	public void hintTextSkippedIfSpawnSetMeanwhile(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		AdvancementHolder seen = helper.getLevel().getServer().getAdvancements().get(Ids.mod(SpawnHint.SEEN_ADVANCEMENT));
		player.getAdvancements().award(seen, SpawnHint.CRITERION);

		use(player, bed.foot);
		player.setShiftKeyDown(true);
		use(player, bed.foot);
		assertSpawnAt(helper, player, bed.head);

		helper.runAfterDelay(SpawnHint.ACTION_BAR_DELAY + 2, () -> {
			helper.assertFalse(sawHintText(player), "spawn is set now, so the hint no longer applies");
			done(helper, player);
		});
	}

	static boolean sawHintText(ServerPlayer player) {
		return Recorder.messages(player.getUUID()).contains(SpawnHint.actionBarText());
	}

	@GameTest(environment = NIGHT)
	public void hintNotShownWhenBedIsAlreadySpawn(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setShiftKeyDown(true);
		use(player, bed.foot);
		assertSpawnAt(helper, player, bed.head);

		player.stopSleepInBed(false, true);
		player.setShiftKeyDown(false);
		use(player, bed.foot);

		helper.assertTrue(player.isSleeping(), "normal sleep in the spawn bed");
		helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 0, "no hint when the bed already is the spawn");
		done(helper, player);
	}

	@GameTest(environment = DAY)
	public void hintShownOnNormalDayUse(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);

		use(player, bed.foot);

		helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 1, "a normal day click is where the hint helps most");
		helper.runAfterDelay(4, () -> done(helper, player));
	}

	@GameTest(environment = NIGHT_NO_HINT)
	public void settingOffNoHint(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);

		use(player, bed.foot);

		helper.assertTrue(player.isSleeping(), "normal sleep still works");
		helper.assertTrue(player.getRespawnConfig() == null, "normal sleep still doesn't set spawn");
		helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 0, "show_spawn_hint_popup=false shows no hint");
		done(helper, player);
	}

	@GameTest(environment = NIGHT)
	public void hintNoChatAnnouncement(GameTestHelper helper) {
		AdvancementHolder hint = hint(helper);
		DisplayInfo display = hint.value().display().orElseThrow();
		helper.assertFalse(display.announceToChat(), "announce_to_chat must be false");
		helper.assertTrue(display.hidden(), "hint must be hidden");
		helper.assertTrue(display.showToast(), "hint must show a toast");
		helper.assertTrue(hint.value().isRoot(), "hint is its own root, so it never joins a vanilla tab");

		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		int broadcastsBefore = Recorder.BROADCASTS.size();
		use(player, bed.foot);

		helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 1, "hint granted");
		helper.runAfterDelay(4, () -> {
			for (Component message : Recorder.BROADCASTS.subList(broadcastsBefore, Recorder.BROADCASTS.size())) {
				helper.assertFalse(isAdvancementAnnouncement(message), "hint must not be announced in chat: " + message.getString());
			}
			for (Component message : Recorder.messages(player.getUUID())) {
				helper.assertFalse(isAdvancementAnnouncement(message), "hint must not send chat: " + message.getString());
			}
			done(helper, player);
		});
	}

	// --- Undercover Sleeper advancement -------------------------------------------------------------------------

	@GameTest(environment = NIGHT)
	public void undercoverSleeperIsARealAdvancement(GameTestHelper helper) {
		AdvancementHolder holder = undercoverSleeper(helper);
		DisplayInfo display = holder.value().display().orElseThrow();
		helper.assertTrue(display.title().getString().equals("Undercover Sleeper"), "title");
		helper.assertTrue(display.description().getString().equals("Your spawn point has been compromised."), "description");
		helper.assertTrue(display.showToast(), "shows a toast");
		helper.assertFalse(display.hidden(), "listed in the advancements screen");
		helper.assertTrue(helper.getLevel().getServer().getAdvancements().tree().get(holder).parent().holder().id()
			.equals(Identifier.withDefaultNamespace("adventure/sleep_in_bed")), "sits under Sweet Dreams");
		helper.succeed();
	}

	@GameTest(environment = NIGHT)
	public void undercoverSleeperOnSneakAtNight(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setShiftKeyDown(true);

		use(player, bed.foot);

		helper.assertTrue(hasUndercoverSleeper(helper, player), "sneak-setting spawn at night earns it");
		done(helper, player);
	}

	@GameTest(environment = DAY)
	public void undercoverSleeperOnSneakDuringDay(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setShiftKeyDown(true);

		use(player, bed.foot);

		helper.assertTrue(hasUndercoverSleeper(helper, player), "sneak-setting spawn during the day earns it");
		done(helper, player);
	}

	@GameTest(environment = NIGHT)
	public void undercoverSleeperWithShieldInOffhand(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		player.setShiftKeyDown(true);

		useOn(player, InteractionHand.MAIN_HAND, bed.foot);
		useOn(player, InteractionHand.OFF_HAND, bed.foot);

		helper.assertTrue(hasUndercoverSleeper(helper, player), "the held-item fallback earns it too");
		done(helper, player);
	}

	@GameTest(environment = NIGHT)
	public void undercoverSleeperNotOnNormalSleep(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);

		use(player, bed.foot);

		helper.assertTrue(player.isSleeping(), "normal sleep");
		helper.assertFalse(hasUndercoverSleeper(helper, player), "a normal sleep doesn't earn it");
		helper.runAfterDelay(4, () -> done(helper, player));
	}

	@GameTest(environment = NIGHT)
	public void undercoverSleeperNotWhenPlacingABlock(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
		player.setShiftKeyDown(true);

		use(player, bed.foot);

		helper.assertFalse(hasUndercoverSleeper(helper, player), "placing a block doesn't earn it");
		done(helper, player);
	}

	static AdvancementHolder undercoverSleeper(GameTestHelper helper) {
		AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(Ids.mod(UndercoverSleeper.ADVANCEMENT));
		helper.assertTrue(holder != null, "Undercover Sleeper should be loaded from the mod's data");
		return holder;
	}

	static boolean hasUndercoverSleeper(GameTestHelper helper, ServerPlayer player) {
		return player.getAdvancements().getOrStartProgress(undercoverSleeper(helper)).isDone();
	}

	// --- Helpers ----------------------------------------------------------------------------------------------

	record Bed(BlockPos foot, BlockPos head) {
	}

	/**
	 * The batch's clock_time environment changes the clock, but day/night (sky darkness and the environment
	 * attributes behind it) is only recomputed in the next level tick. Without this a test running on the batch's
	 * first tick can still see the previous batch's time.
	 */
	static void refreshTimeOfDay(ServerLevel level) {
		level.environmentAttributes().invalidateTickCache();
		level.updateSkyBrightness();
	}

	/** A south-facing bed on a stone floor, at relative x = {@code x}, z = 1..2. */
	static Bed placeBed(GameTestHelper helper, int x) {
		refreshTimeOfDay(helper.getLevel());
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = 0; dz <= 3; dz++) {
				helper.setBlock(new BlockPos(x + dx, 0, dz), Blocks.STONE);
			}
		}
		return placeBedAt(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 1)));
	}

	static Bed placeBedAt(ServerLevel level, BlockPos foot) {
		return placeBedAt(level, foot, Blocks.BED.red());
	}

	/** Any AbstractBedBlock (colored or straw), south-facing. */
	static Bed placeBedAt(ServerLevel level, BlockPos foot, Block bedBlock) {
		BlockPos head = foot.south();
		BlockState state = bedBlock.defaultBlockState().setValue(AbstractBedBlock.FACING, Direction.SOUTH);
		level.setBlockAndUpdate(foot, state.setValue(AbstractBedBlock.PART, BedPart.FOOT));
		level.setBlockAndUpdate(head, state.setValue(AbstractBedBlock.PART, BedPart.HEAD));
		return new Bed(foot.immutable(), head.immutable());
	}

	static ServerPlayer playerBeside(GameTestHelper helper, Bed bed) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		moveBeside(player, bed);
		return player;
	}

	/** Standing on the floor just west of the bed's foot, well within vanilla's 3-block reach check. */
	static void moveBeside(ServerPlayer player, Bed bed) {
		player.teleportTo((ServerLevel) player.level(), bed.foot.getX() - 0.5, bed.foot.getY(), bed.foot.getZ() + 0.5, Set.of(), 0, 0, true);
	}

	static void use(ServerPlayer player, BlockPos pos) {
		useOn(player, InteractionHand.MAIN_HAND, pos);
	}

	/** One use-on-block packet. */
	static InteractionResult useOn(ServerPlayer player, InteractionHand hand, BlockPos pos) {
		return player.gameMode.useItemOn(player, player.level(), player.getItemInHand(hand), hand, hit(pos));
	}

	/** One use-item packet. */
	static InteractionResult useItem(ServerPlayer player, InteractionHand hand) {
		return player.gameMode.useItem(player, player.level(), player.getItemInHand(hand), hand);
	}

	static BlockHitResult hit(BlockPos pos) {
		return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.0625, 0), Direction.UP, pos, false);
	}

	static void assertSpawnAt(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		helper.assertTrue(respawn != null && respawn.respawnData().globalPos().equals(GlobalPos.of(player.level().dimension(), pos)),
			"spawn should be at " + pos.toShortString() + " but is " + (respawn == null ? "unset" : respawn.respawnData().globalPos()));
	}

	static void assertDark(GameTestHelper helper, boolean dark) {
		helper.assertTrue(helper.getLevel().isDarkOutside() == dark, "test environment should be " + (dark ? "night" : "day"));
	}

	static AdvancementHolder hint(GameTestHelper helper) {
		AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(Ids.mod(SpawnHint.ADVANCEMENT));
		helper.assertTrue(holder != null, "hint advancement should be loaded from the mod's data");
		return holder;
	}

	static boolean sawMessage(ServerPlayer player, String key) {
		List<Component> messages = Recorder.messages(player.getUUID());
		return messages.stream().anyMatch(message -> message.getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}

	/** An advancement announcement for the hint (other tests in the batch legitimately announce Sweet Dreams). */
	static boolean isAdvancementAnnouncement(Component message) {
		return message.getContents() instanceof TranslatableContents t && t.getKey().startsWith("chat.type.advancement")
			&& message.getString().contains("Bed not set as spawn");
	}

	static void done(GameTestHelper helper, ServerPlayer player) {
		if (player.isSleeping()) {
			player.stopSleepInBed(false, true);
		}
		player.level().getServer().getPlayerList().remove(player);
		helper.succeed();
	}
}
