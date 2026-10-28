package com.simplyag.crouchtosetrespawn.test;

import com.simplyag.crouchtosetrespawn.SpawnHint;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
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
	private static final String NIGHT = "crouchtosetrespawn-gametest:night";
	private static final String DAY = "crouchtosetrespawn-gametest:day";
	private static final String SET_SPAWN = "block.minecraft.set_spawn";
	private static final String NO_SLEEP = "block.minecraft.bed.no_sleep";

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
		netherBedExplodes(helper, true, 0);
	}

	@GameTest
	public void netherBedExplodesWhenNotSneaking(GameTestHelper helper) {
		netherBedExplodes(helper, false, 4);
	}

	private void netherBedExplodes(GameTestHelper helper, boolean sneaking, int chunkX) {
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		nether.setChunkForced(chunkX, 0, true);
		BlockPos origin = new BlockPos(chunkX * 16 + 4, 110, 4);
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, 0, -4), origin.offset(4, 5, 4))) {
			nether.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		}
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, -1, -4), origin.offset(4, -1, 4))) {
			nether.setBlockAndUpdate(pos, Blocks.NETHERRACK.defaultBlockState());
		}
		Bed bed = placeBedAt(nether, origin);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.teleportTo(nether, bed.foot.getX() - 0.5, bed.foot.getY(), bed.foot.getZ() + 0.5, Set.of(), 0, 0, true);
		player.setShiftKeyDown(sneaking);

		player.gameMode.useItemOn(player, nether, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(bed.foot));

		helper.assertTrue(nether.getBlockState(bed.foot).isAir() && nether.getBlockState(bed.head).isAir(), "bed should be destroyed");
		helper.assertTrue(nether.getBlockState(bed.head.below()).isAir(), "explosion should have blown out the netherrack under the bed");
		helper.assertTrue(player.getRespawnConfig() == null, "a Nether bed must never set spawn");
		helper.assertFalse(player.isSleeping(), "a Nether bed must never sleep");
		helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 0, "no hint for a Nether bed");
		nether.setChunkForced(chunkX, 0, false);
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

	@GameTest(environment = NIGHT, maxTicks = 40)
	public void hintThrottledPerNight(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = playerBeside(helper, bed);

		for (int i = 0; i < 3; i++) {
			use(player, bed.foot);
			player.stopSleepInBed(false, true);
		}
		helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 1, "three clicks in one night should show the hint once");

		helper.runAfterDelay(4, () -> {
			// The next evening (still dark, so the bed still sleeps): the hint is due again.
			helper.setTime(18000 + 24000);
			helper.getLevel().updateSkyBrightness();
			use(player, bed.foot);
			helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 2, "the next night should show the hint again");
			helper.setTime(18000);
			helper.getLevel().updateSkyBrightness();
			done(helper, player);
		});
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

	// --- Helpers ----------------------------------------------------------------------------------------------

	private record Bed(BlockPos foot, BlockPos head) {
	}

	/** A south-facing bed on a stone floor, at relative x = {@code x}, z = 1..2. */
	private static Bed placeBed(GameTestHelper helper, int x) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = 0; dz <= 3; dz++) {
				helper.setBlock(new BlockPos(x + dx, 0, dz), Blocks.STONE);
			}
		}
		return placeBedAt(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 1)));
	}

	private static Bed placeBedAt(ServerLevel level, BlockPos foot) {
		BlockPos head = foot.south();
		BlockState state = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		level.setBlockAndUpdate(foot, state.setValue(BedBlock.PART, BedPart.FOOT));
		level.setBlockAndUpdate(head, state.setValue(BedBlock.PART, BedPart.HEAD));
		return new Bed(foot.immutable(), head.immutable());
	}

	private static ServerPlayer playerBeside(GameTestHelper helper, Bed bed) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		moveBeside(player, bed);
		return player;
	}

	/** Standing on the floor just west of the bed's foot, well within vanilla's 3-block reach check. */
	private static void moveBeside(ServerPlayer player, Bed bed) {
		player.teleportTo((ServerLevel) player.level(), bed.foot.getX() - 0.5, bed.foot.getY(), bed.foot.getZ() + 0.5, Set.of(), 0, 0, true);
	}

	private static void use(ServerPlayer player, BlockPos pos) {
		player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(pos));
	}

	private static BlockHitResult hit(BlockPos pos) {
		return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.0625, 0), Direction.UP, pos, false);
	}

	private static void assertSpawnAt(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		helper.assertTrue(respawn != null && respawn.respawnData().globalPos().equals(GlobalPos.of(player.level().dimension(), pos)),
			"spawn should be at " + pos.toShortString() + " but is " + (respawn == null ? "unset" : respawn.respawnData().globalPos()));
	}

	private static void assertDark(GameTestHelper helper, boolean dark) {
		helper.assertTrue(helper.getLevel().isDarkOutside() == dark, "test environment should be " + (dark ? "night" : "day"));
	}

	private static AdvancementHolder hint(GameTestHelper helper) {
		AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(SpawnHint.ADVANCEMENT);
		helper.assertTrue(holder != null, "hint advancement should be loaded from the mod's data");
		return holder;
	}

	private static boolean sawMessage(ServerPlayer player, String key) {
		List<Component> messages = Recorder.messages(player.getUUID());
		return messages.stream().anyMatch(message -> message.getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}

	/** An advancement announcement for the hint (other tests in the batch legitimately announce Sweet Dreams). */
	private static boolean isAdvancementAnnouncement(Component message) {
		return message.getContents() instanceof TranslatableContents t && t.getKey().startsWith("chat.type.advancement")
			&& message.getString().contains("Bed not set as spawn");
	}

	private static void done(GameTestHelper helper, ServerPlayer player) {
		if (player.isSleeping()) {
			player.stopSleepInBed(false, true);
		}
		player.level().getServer().getPlayerList().remove(player);
		helper.succeed();
	}
}
