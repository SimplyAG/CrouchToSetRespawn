package com.simplyag.crouchtosetrespawn.test;

import static com.simplyag.crouchtosetrespawn.test.CrouchSpawnGameTest.NIGHT;
import static com.simplyag.crouchtosetrespawn.test.CrouchSpawnGameTest.SET_SPAWN;
import static com.simplyag.crouchtosetrespawn.test.CrouchSpawnGameTest.assertSpawnAt;
import static com.simplyag.crouchtosetrespawn.test.CrouchSpawnGameTest.done;
import static com.simplyag.crouchtosetrespawn.test.CrouchSpawnGameTest.hint;
import static com.simplyag.crouchtosetrespawn.test.CrouchSpawnGameTest.moveBeside;
import static com.simplyag.crouchtosetrespawn.test.CrouchSpawnGameTest.placeBed;
import static com.simplyag.crouchtosetrespawn.test.CrouchSpawnGameTest.placeBedAt;
import static com.simplyag.crouchtosetrespawn.test.CrouchSpawnGameTest.playerBeside;
import static com.simplyag.crouchtosetrespawn.test.CrouchSpawnGameTest.refreshTimeOfDay;
import static com.simplyag.crouchtosetrespawn.test.CrouchSpawnGameTest.sawMessage;
import static com.simplyag.crouchtosetrespawn.test.CrouchSpawnGameTest.use;
import static com.simplyag.crouchtosetrespawn.test.CrouchSpawnGameTest.useItem;
import static com.simplyag.crouchtosetrespawn.test.CrouchSpawnGameTest.useOn;

import com.mojang.authlib.GameProfile;
import com.simplyag.crouchtosetrespawn.ModConfig;
import com.simplyag.crouchtosetrespawn.SpawnHint;
import com.simplyag.crouchtosetrespawn.test.CrouchSpawnGameTest.Bed;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/** The cases around the main behaviour: config parsing, logging out mid-hint, and beds vanilla refuses. */
public class EdgeCaseGameTest {
	private static final String CONFIG_PARSING = "crouchtosetrespawn-gametest:config_parsing";
	private static final String NIGHT_SLEEP_THROUGH = "crouchtosetrespawn-gametest:night_sleep_through";

	// --- Config file ------------------------------------------------------------------------------------------

	/** One test, in its own batch: the config is global, and the environment restores it afterwards. */
	@GameTest(environment = CONFIG_PARSING)
	public void configFileParsing(GameTestHelper helper) {
		Path dir = tempDir();
		Path file = dir.resolve(ModConfig.FILE_NAME);

		ModConfig.load(file);
		helper.assertTrue(Files.isRegularFile(file), "a missing config file gets written with defaults");
		String written = read(file);
		helper.assertTrue(written.contains("sneak_with_items_sets_spawn=true") && written.contains("show_spawn_hint_popup=true"),
			"default file has both settings on");
		helper.assertTrue(written.contains("# false = vanilla rule"), "default file explains the settings");
		assertConfig(helper, true, true, "defaults");

		write(file, "sneak_with_items_sets_spawn=false\nshow_spawn_hint_popup=false\n");
		ModConfig.load(file);
		assertConfig(helper, false, false, "both false");

		write(file, "# comment\n\n  sneak_with_items_sets_spawn =  FALSE  \nshow_spawn_hint_popup=True\n");
		ModConfig.load(file);
		assertConfig(helper, false, true, "spaces and any capitalisation");

		write(file, "sneak_with_items_sets_spawn=flase\nshow_spawn_hint_popup=0\n");
		ModConfig.load(file);
		assertConfig(helper, true, true, "typos fall back to the defaults");

		write(file, "show_spawn_hint_popup=false\nsome_unknown_setting=whatever\n");
		ModConfig.load(file);
		assertConfig(helper, true, false, "a missing setting uses its default, unknown ones are ignored");

		write(file, "");
		ModConfig.load(file);
		assertConfig(helper, true, true, "an empty file means defaults");
		helper.assertTrue(read(file).isEmpty(), "an existing file is never overwritten");

		helper.succeed();
	}

	private static void assertConfig(GameTestHelper helper, boolean sneakWithItems, boolean hint, String what) {
		helper.assertTrue(ModConfig.sneakWithItemsSetsSpawn == sneakWithItems && ModConfig.showSpawnHintPopup == hint,
			what + ": expected sneak_with_items_sets_spawn=" + sneakWithItems + ", show_spawn_hint_popup=" + hint
				+ " but got " + ModConfig.sneakWithItemsSetsSpawn + ", " + ModConfig.showSpawnHintPopup);
	}

	// --- Logging out while the hint is granted ---------------------------------------------------------------

	@GameTest(environment = NIGHT)
	public void logoutInsideHintWindowSavesItNotDone(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		GameProfile profile = new GameProfile(UUID.randomUUID(), "hint-logout");
		ServerPlayer player = join(helper, profile);
		moveBeside(player, bed);
		AdvancementHolder hint = hint(helper);

		use(player, bed.foot());
		helper.assertTrue(player.getAdvancements().getOrStartProgress(hint).isDone(), "hint granted by the click");

		// Same tick as the grant, so the scheduled 2-tick revoke hasn't run: only the leave path can save it right.
		player.connection.onDisconnect(new DisconnectionDetails(Component.literal("test logout")));
		helper.assertTrue(helper.getLevel().getServer().getPlayerList().getPlayer(profile.id()) == null, "player is gone");

		ServerPlayer rejoined = join(helper, profile);
		helper.assertFalse(rejoined.getAdvancements().getOrStartProgress(hint).isDone(), "hint was saved as not done");
		done(helper, rejoined);
	}

	@GameTest(environment = NIGHT)
	public void firstHintRememberedAcrossRejoin(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		GameProfile profile = new GameProfile(UUID.randomUUID(), "hint-once");
		ServerPlayer player = join(helper, profile);
		moveBeside(player, bed);
		use(player, bed.foot());
		helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 1, "first hint is the toast");
		player.connection.onDisconnect(new DisconnectionDetails(Component.literal("test logout")));

		ServerPlayer rejoined = join(helper, profile);
		AdvancementHolder seen = helper.getLevel().getServer().getAdvancements().get(SpawnHint.SEEN_ADVANCEMENT);
		helper.assertTrue(rejoined.getAdvancements().getOrStartProgress(seen).isDone(), "the toast is remembered in the save");
		done(helper, rejoined);
	}

	@GameTest(environment = NIGHT)
	public void hintSavedAsDoneIsClearedOnJoin(GameTestHelper helper) {
		AdvancementHolder hint = hint(helper);
		// A save file with the hint done, as an autosave or crash inside the 2-tick window would leave it.
		ServerPlayer saver = join(helper, new GameProfile(UUID.randomUUID(), "hint-saver"));
		saver.getAdvancements().award(hint, "hint");
		saver.getAdvancements().save();
		Path advancements = helper.getLevel().getServer().getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR);
		GameProfile profile = new GameProfile(UUID.randomUUID(), "hint-joiner");
		copy(advancements.resolve(saver.getUUID() + ".json"), advancements.resolve(profile.id() + ".json"));
		helper.getLevel().getServer().getPlayerList().remove(saver);

		ServerPlayer joined = join(helper, profile);
		helper.assertFalse(joined.getAdvancements().getOrStartProgress(hint).isDone(), "joining clears a hint saved as done");
		done(helper, joined);
	}

	// --- Beds vanilla refuses -------------------------------------------------------------------------------

	@GameTest(environment = NIGHT)
	public void strawBedNeverSetsSpawn(GameTestHelper helper) {
		Bed bed = strawBed(helper);
		ServerPlayer player = playerBeside(helper, bed);
		player.setShiftKeyDown(true);

		use(player, bed.foot());

		helper.assertTrue(player.isSleeping(), "straw beds still sleep");
		helper.assertTrue(player.getRespawnConfig() == null, "the straw bed rule never sets spawn, sneaking or not");
		helper.assertFalse(sawMessage(player, SET_SPAWN), "no 'Respawn point set' for a straw bed");
		helper.assertFalse(CrouchSpawnGameTest.hasUndercoverSleeper(helper, player), "no spawn set, so no Undercover Sleeper");
		done(helper, player);
	}

	@GameTest(environment = NIGHT)
	public void strawBedNormalUseNoHint(GameTestHelper helper) {
		Bed bed = strawBed(helper);
		ServerPlayer player = playerBeside(helper, bed);

		use(player, bed.foot());

		helper.assertTrue(player.isSleeping(), "straw beds still sleep");
		helper.assertTrue(Recorder.hintGrants(player.getUUID()) == 0, "no hint where vanilla wouldn't set spawn either");
		done(helper, player);
	}

	@GameTest(environment = NIGHT)
	public void occupiedBedChangesNothing(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer sleeper = playerBeside(helper, bed);
		use(sleeper, bed.foot());
		helper.assertTrue(sleeper.isSleeping(), "first player is in the bed");

		ServerPlayer other = playerBeside(helper, bed);
		other.setShiftKeyDown(true);
		use(other, bed.foot());
		ServerPlayer normal = playerBeside(helper, bed);
		use(normal, bed.foot());

		helper.assertTrue(sawMessage(other, "block.minecraft.bed.occupied"), "vanilla 'This bed is occupied' message");
		helper.assertTrue(other.getRespawnConfig() == null && normal.getRespawnConfig() == null, "an occupied bed sets nobody's spawn");
		helper.assertTrue(Recorder.hintGrants(normal.getUUID()) == 0, "no hint for an occupied bed");
		done(helper, other);
		done(helper, normal);
		done(helper, sleeper);
	}

	@GameTest(environment = NIGHT)
	public void tooFarAwayChangesNothing(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer sneaker = playerBeside(helper, bed);
		ServerPlayer normal = playerBeside(helper, bed);
		for (ServerPlayer player : new ServerPlayer[]{sneaker, normal}) {
			player.teleportTo((ServerLevel) player.level(), bed.foot().getX() + 5.5, bed.foot().getY(), bed.foot().getZ() + 0.5, Set.of(), 0, 0, true);
		}
		sneaker.setShiftKeyDown(true);

		use(sneaker, bed.foot());
		use(normal, bed.foot());

		helper.assertTrue(sawMessage(sneaker, "block.minecraft.bed.too_far_away"), "vanilla 'too far away' message");
		helper.assertTrue(sneaker.getRespawnConfig() == null, "too far away: no spawn, even sneaking");
		helper.assertFalse(CrouchSpawnGameTest.hasUndercoverSleeper(helper, sneaker), "too far away: no Undercover Sleeper");
		helper.assertTrue(Recorder.hintGrants(normal.getUUID()) == 0, "too far away: vanilla wouldn't set spawn, so no hint");
		done(helper, sneaker);
		done(helper, normal);
	}

	@GameTest(environment = NIGHT)
	public void obstructedBedChangesNothing(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		helper.getLevel().setBlockAndUpdate(bed.head().above(), Blocks.STONE.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(bed.foot().above(), Blocks.STONE.defaultBlockState());
		ServerPlayer sneaker = playerBeside(helper, bed);
		ServerPlayer normal = playerBeside(helper, bed);
		sneaker.setShiftKeyDown(true);

		use(sneaker, bed.foot());
		use(normal, bed.foot());

		helper.assertTrue(sawMessage(sneaker, "block.minecraft.bed.obstructed"), "vanilla 'This bed is obstructed' message");
		helper.assertTrue(sneaker.getRespawnConfig() == null, "obstructed: no spawn, even sneaking");
		helper.assertFalse(CrouchSpawnGameTest.hasUndercoverSleeper(helper, sneaker), "obstructed: no Undercover Sleeper");
		helper.assertTrue(Recorder.hintGrants(normal.getUUID()) == 0, "obstructed: vanilla wouldn't set spawn, so no hint");
		done(helper, sneaker);
		done(helper, normal);
	}

	/** Vanilla sets spawn before the monster check, so a sneak still sets it; a normal click gets the hint. */
	@GameTest(environment = NIGHT)
	public void monstersNearbyLikeVanilla(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(5, 1, 3));
		zombie.setNoAi(true);
		ServerPlayer sneaker = survivalBeside(helper, bed, "zombie-sneak");
		ServerPlayer normal = survivalBeside(helper, bed, "zombie-normal");
		sneaker.setShiftKeyDown(true);

		use(sneaker, bed.foot());
		use(normal, bed.foot());

		helper.assertTrue(sawMessage(sneaker, "block.minecraft.bed.not_safe"), "vanilla 'monsters nearby' message");
		helper.assertFalse(sneaker.isSleeping() || normal.isSleeping(), "nobody sleeps with a monster nearby");
		assertSpawnAt(helper, sneaker, bed.head());
		helper.assertTrue(CrouchSpawnGameTest.hasUndercoverSleeper(helper, sneaker), "spawn was set, so it's earned even without sleeping");
		helper.assertTrue(normal.getRespawnConfig() == null, "normal click with monsters nearby: no spawn");
		helper.assertTrue(Recorder.hintGrants(normal.getUUID()) == 1, "vanilla would have set spawn here, so the hint shows");
		zombie.discard();
		helper.runAfterDelay(3, () -> {
			done(helper, sneaker);
			done(helper, normal);
		});
	}

	@GameTest(environment = NIGHT)
	public void spectatorCantUseBeds(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = survivalBeside(helper, bed, "spectator");
		player.setGameMode(GameType.SPECTATOR);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		player.setShiftKeyDown(true);

		useOn(player, InteractionHand.MAIN_HAND, bed.foot());
		useOn(player, InteractionHand.OFF_HAND, bed.foot());

		helper.assertFalse(player.isSleeping(), "spectators never use beds");
		helper.assertTrue(player.getRespawnConfig() == null, "spectators never set spawn");
		done(helper, player);
	}

	/**
	 * Food in the main hand: a vanilla client starts eating and never sends the offhand click, so the bed is never
	 * reached. Documented behaviour, pinned here so a change to it is noticed.
	 */
	@GameTest(environment = NIGHT)
	public void sneakWithFoodInMainHandEatsInstead(GameTestHelper helper) {
		Bed bed = placeBed(helper, 1);
		ServerPlayer player = survivalBeside(helper, bed, "main-food");
		player.getFoodData().setFoodLevel(10);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
		player.setShiftKeyDown(true);

		useOn(player, InteractionHand.MAIN_HAND, bed.foot());
		useItem(player, InteractionHand.MAIN_HAND);

		helper.assertTrue(player.isUsingItem(), "eating starts, as in vanilla");
		helper.assertTrue(player.getRespawnConfig() == null, "the bed is never reached, so no spawn");
		done(helper, player);
	}

	// --- Several players --------------------------------------------------------------------------------------

	/**
	 * Two players sleep through the night, one sneaking, one not. The night is skipped as in vanilla (100% of the
	 * only two players), both wake, and only the sneaker's spawn moved. Own batch, so no other test's players count.
	 */
	@GameTest(environment = NIGHT_SLEEP_THROUGH, maxTicks = 200)
	public void twoPlayersSleepThroughTheNight(GameTestHelper helper) {
		Bed bedA = placeBed(helper, 1);
		Bed bedB = placeBed(helper, 5);
		ServerPlayer sneaker = survivalBeside(helper, bedA, "night-sneak");
		ServerPlayer normal = survivalBeside(helper, bedB, "night-normal");
		sneaker.setShiftKeyDown(true);

		use(sneaker, bedA.foot());
		use(normal, bedB.foot());
		helper.assertTrue(sneaker.isSleeping() && normal.isSleeping(), "both in bed");

		// Mock connections are not ticked by the server, and that tick is what advances the sleep timer.
		helper.onEachTick(() -> {
			sneaker.doTick();
			normal.doTick();
		});

		helper.succeedWhen(() -> {
			refreshTimeOfDay(helper.getLevel());
			helper.assertFalse(helper.getLevel().isDarkOutside(), "the night should be skipped");
			helper.assertFalse(sneaker.isSleeping() || normal.isSleeping(), "both woke up");
			assertSpawnAt(helper, sneaker, bedA.head());
			helper.assertTrue(normal.getRespawnConfig() == null, "the normal sleeper's spawn didn't move");
			helper.getLevel().getServer().getPlayerList().remove(sneaker);
			helper.getLevel().getServer().getPlayerList().remove(normal);
		});
	}

	// --- Helpers ----------------------------------------------------------------------------------------------

	private static Bed strawBed(GameTestHelper helper) {
		// The floor comes with a red bed; clear it first, or replacing one half at a time makes vanilla break the new bed.
		Bed floor = placeBed(helper, 1);
		helper.getLevel().setBlockAndUpdate(floor.foot(), Blocks.AIR.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(floor.head(), Blocks.AIR.defaultBlockState());
		return placeBedAt(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), Blocks.STRAW_BED);
	}

	/** A player in the server's default game mode (survival), unlike the mock players, which are creative. */
	private static ServerPlayer survivalBeside(GameTestHelper helper, Bed bed, String name) {
		ServerPlayer player = join(helper, new GameProfile(UUID.randomUUID(), name));
		player.setGameMode(GameType.SURVIVAL);
		moveBeside(player, bed);
		return player;
	}

	/** Joins like GameTestHelper.makeMockServerPlayerInLevel, but with a chosen profile, so the same player can rejoin. */
	private static ServerPlayer join(GameTestHelper helper, GameProfile profile) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		return player;
	}

	private static Path tempDir() {
		try {
			return Files.createTempDirectory("crouchtosetrespawn-config");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void write(Path file, String text) {
		try {
			Files.writeString(file, text, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void copy(Path from, Path to) {
		try {
			Files.copy(from, to);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
