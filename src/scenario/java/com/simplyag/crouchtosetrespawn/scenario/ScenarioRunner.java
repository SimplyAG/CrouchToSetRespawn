package com.simplyag.crouchtosetrespawn.scenario;

import com.simplyag.crouchtosetrespawn.SpawnHint;
import com.simplyag.crouchtosetrespawn.UndercoverSleeper;
import com.simplyag.crouchtosetrespawn.util.Compat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The version-neutral test: the same bed scenarios on every Minecraft version the mod supports, in a real dedicated
 * server (`gradlew :<mc>:runScenarioTest`). Every click goes through ServerPlayerGameMode.useItemOn, the path a
 * client's use-on-block packet takes, with players that joined through PlayerList.placeNewPlayer. Setup uses
 * commands, which barely changed across versions, so the harness needs very few version conditionals itself.
 *
 * <p>Writes scenario-result.txt in the run directory ("PASS name" / "FAIL name: why", then "DONE") and stops the
 * server; the Gradle task fails unless every scenario passed.
 */
public final class ScenarioRunner {
	/** Ticks after server start before the first scenario, so spawn chunks and data packs have settled. */
	private static final int START_DELAY = 40;
	/** Each scenario gets its own strip of the world, far from spawn. */
	private static final int BASE_X = 2000;
	private static final int BASE_Z = 2000;
	private static final int Y = 100;

	private static final Deque<Step> STEPS = new ArrayDeque<>();
	private static final List<String> RESULTS = new ArrayList<>();
	private static int waitUntil = -1;
	private static boolean started;
	private static String current;
	private static boolean currentFailed;
	private static int playerCount;
	private static volatile boolean finished;
	/** How long a finished run may take to stop on its own before the process is ended. */
	private static final long SHUTDOWN_GRACE_MS = 30_000;
	/** A whole run takes well under a minute; past this, something hung. */
	private static final long RUN_TIMEOUT_MS = 5 * 60_000;

	private interface Action {
		void run(MinecraftServer server) throws Exception;
	}

	private static final class Step {
		final int delay;
		final Action action;

		Step(int delay, Action action) {
			this.delay = delay;
			this.action = action;
		}
	}

	private ScenarioRunner() {
	}

	/** End of every server tick (ServerTickMixin). */
	public static void tick(MinecraftServer server) {
		if (!started) {
			if (server.getTickCount() < START_DELAY) {
				return;
			}
			started = true;
			watchdog(RUN_TIMEOUT_MS, 1, "FAIL the scenarios timed out (a step hung or the server stopped ticking)");
			plan();
		}
		if (server.getTickCount() < waitUntil) {
			return;
		}
		Step step = STEPS.poll();
		if (step == null) {
			finish(server);
			return;
		}
		try {
			step.action.run(server);
		} catch (Throwable t) {
			fail(t.toString());
			t.printStackTrace();
		}
		Step next = STEPS.peek();
		waitUntil = server.getTickCount() + (next == null ? 1 : next.delay);
	}

	// --- Scenarios --------------------------------------------------------------------------------------------

	private static void plan() {
		scenario("normal use at night sleeps without setting spawn, shows the hint toast once", 0, false, false, (server, p, bed) -> {
			use(p, InteractionHand.MAIN_HAND, bed.foot);
			check(p.isSleeping(), "sleeping");
			check(!spawnAt(p, bed), "spawn not set");
			check(Compat.isDone(p, SpawnHint.ADVANCEMENT), "hint toast granted");
			check(Compat.isDone(p, SpawnHint.SEEN_ADVANCEMENT), "first hint remembered");
			check(!Compat.isDone(p, UndercoverSleeper.ADVANCEMENT), "no Undercover Sleeper");
			check(!TestCompat.vanillaDone(p, "adventure/sleep_in_bed"), "no Sweet Dreams without setting spawn");
		}, (server, p, bed) -> {
			check(!Compat.isDone(p, SpawnHint.ADVANCEMENT), "hint toast revoked again");
			// 1.14 vanilla sets spawn on waking up after the night rather than on the click.
			TestCompat.wakeInTheMorning(p);
			check(!p.isSleeping(), "woke up");
			check(!spawnAt(p, bed), "waking up in the morning doesn't set spawn either");
		});

		scenario("sneak + use at night sets spawn and sleeps", 1, true, false, (server, p, bed) -> {
			use(p, InteractionHand.MAIN_HAND, bed.foot);
			check(spawnAt(p, bed), "spawn set at the bed");
			check(p.isSleeping(), "sleeping");
			check(Compat.isDone(p, UndercoverSleeper.ADVANCEMENT), "Undercover Sleeper earned");
			check(TestCompat.vanillaDone(p, "adventure/sleep_in_bed"), "Sweet Dreams earned");
			check(!Compat.isDone(p, SpawnHint.ADVANCEMENT), "no hint on a sneak use");
		}, (server, p, bed) -> {
			TestCompat.wakeInTheMorning(p);
			check(spawnAt(p, bed), "spawn still at the bed after waking up");
		});

		scenario("sneak + use during the day sets spawn", 2, true, true, (server, p, bed) -> {
			use(p, InteractionHand.MAIN_HAND, bed.foot);
			check(spawnAt(p, bed), "spawn set at the bed");
			check(!p.isSleeping(), "not sleeping in the day");
			check(Compat.isDone(p, UndercoverSleeper.ADVANCEMENT), "Undercover Sleeper earned");
		}, null);

		scenario("normal use during the day changes nothing but shows the hint", 3, false, true, (server, p, bed) -> {
			use(p, InteractionHand.MAIN_HAND, bed.foot);
			check(!spawnAt(p, bed), "spawn not set");
			check(!p.isSleeping(), "not sleeping in the day");
			check(Compat.isDone(p, SpawnHint.ADVANCEMENT), "hint toast granted");
		}, null);

		scenario("sneak + use holding a block places it", 4, true, false, (server, p, bed) -> {
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
			use(p, InteractionHand.MAIN_HAND, bed.foot);
			check(Compat.level(p).getBlockState(bed.foot.above()).getBlock() == Blocks.STONE, "stone placed on the bed");
			check(!spawnAt(p, bed), "spawn not set");
			check(!p.isSleeping(), "not sleeping");
		}, null);

		scenario("sneak + use with a shield in the offhand sets spawn", 5, true, false, (server, p, bed) -> {
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
			// What a vanilla client sends: main-hand use-on-block, offhand use-on-block, then offhand use-item.
			use(p, InteractionHand.MAIN_HAND, bed.foot);
			use(p, InteractionHand.OFF_HAND, bed.foot);
			p.gameMode.useItem(p, Compat.level(p), p.getItemInHand(InteractionHand.OFF_HAND), InteractionHand.OFF_HAND);
			check(spawnAt(p, bed), "spawn set at the bed");
			check(p.isSleeping(), "sleeping");
			check(!p.isUsingItem(), "shield not raised");
		}, null);

		scenario("sneak + use from too far away changes nothing", 6, true, false, (server, p, bed) -> {
			TestCompat.command(server, "tp " + p.getScoreboardName() + " " + (bed.foot.getX() + 6.5) + " " + Y + " " + (bed.foot.getZ() + 0.5));
			use(p, InteractionHand.MAIN_HAND, bed.foot);
			check(!spawnAt(p, bed), "spawn not set");
			check(!Compat.isDone(p, UndercoverSleeper.ADVANCEMENT), "no Undercover Sleeper");
		}, null);

		scenario("logging out during the hint toast saves it as not done", 7, false, true, (server, p, bed) -> {
			use(p, InteractionHand.MAIN_HAND, bed.foot);
			check(Compat.isDone(p, SpawnHint.ADVANCEMENT), "hint toast granted");
			server.getPlayerList().remove(p);
			check(!Compat.isDone(p, SpawnHint.ADVANCEMENT), "hint revoked before the player was saved");
		}, null);

		netherScenario();
	}

	/** A bed in the Nether explodes, sneaking or not, and never sets spawn. */
	private static void netherScenario() {
		final BedPos bed = new BedPos(BASE_X + 8 * 16, BASE_Z);
		final ServerPlayer[] player = new ServerPlayer[1];
		STEPS.add(new Step(1, server -> {
			begin("a sneak + use on a Nether bed explodes it");
			String in = "execute in minecraft:the_nether run ";
			TestCompat.command(server, in + "forceload add " + bed.foot.getX() + " " + bed.foot.getZ());
			TestCompat.command(server, in + "fill " + (bed.foot.getX() - 3) + " " + (Y - 1) + " " + (bed.foot.getZ() - 3) + " "
				+ (bed.foot.getX() + 3) + " " + (Y + 4) + " " + (bed.foot.getZ() + 4) + " air");
			TestCompat.command(server, in + "fill " + (bed.foot.getX() - 3) + " " + (Y - 1) + " " + (bed.foot.getZ() - 3) + " "
				+ (bed.foot.getX() + 3) + " " + (Y - 1) + " " + (bed.foot.getZ() + 4) + " obsidian");
			placeBed(server, in, bed);
			player[0] = TestCompat.joinPlayer(server, "nether" + (playerCount++));
			TestCompat.command(server, in + "tp " + player[0].getScoreboardName() + " " + (bed.foot.getX() - 0.5) + " " + Y + " " + (bed.foot.getZ() + 0.5));
		}));
		STEPS.add(new Step(2, server -> {
			ServerPlayer p = player[0];
			check(TestCompat.isNether(Compat.level(p)), "player is in the Nether");
			TestCompat.setSneaking(p, true);
			use(p, InteractionHand.MAIN_HAND, bed.foot);
			// The explosion can leave fire where the bed stood, so check that no bed is left rather than for air.
			check(!TestCompat.isBed(Compat.level(p).getBlockState(bed.foot)) && !TestCompat.isBed(Compat.level(p).getBlockState(bed.head)), "bed destroyed");
			check(!spawnAt(p, bed), "spawn not set");
			check(!p.isSleeping(), "not sleeping");
		}));
		STEPS.add(new Step(1, server -> end(server, player[0])));
	}

	private interface Check {
		void run(MinecraftServer server, ServerPlayer player, BedPos bed) throws Exception;
	}

	/** Places a bed on a stone pad in its own strip, adds a fresh player beside it, runs act, then later. */
	private static void scenario(String name, int index, boolean sneaking, boolean day, Check act, Check later) {
		final BedPos bed = new BedPos(BASE_X + index * 16, BASE_Z);
		final ServerPlayer[] player = new ServerPlayer[1];
		STEPS.add(new Step(1, server -> {
			begin(name);
			TestCompat.command(server, "forceload add " + bed.foot.getX() + " " + bed.foot.getZ());
			TestCompat.command(server, "fill " + (bed.foot.getX() - 7) + " " + (Y - 1) + " " + (bed.foot.getZ() - 3) + " "
				+ (bed.foot.getX() + 7) + " " + (Y + 4) + " " + (bed.foot.getZ() + 4) + " air");
			TestCompat.command(server, "fill " + (bed.foot.getX() - 7) + " " + (Y - 1) + " " + (bed.foot.getZ() - 3) + " "
				+ (bed.foot.getX() + 7) + " " + (Y - 1) + " " + (bed.foot.getZ() + 4) + " stone");
			placeBed(server, "", bed);
			TestCompat.command(server, "time set " + (day ? 6000 : 18000));
			player[0] = TestCompat.joinPlayer(server, "scen" + (playerCount++));
			TestCompat.command(server, "tp " + player[0].getScoreboardName() + " " + (bed.foot.getX() - 0.5) + " " + Y + " " + (bed.foot.getZ() + 0.5));
		}));
		// A couple of ticks so the time of day and the teleport have taken effect.
		STEPS.add(new Step(3, server -> {
			TestCompat.setSneaking(player[0], sneaking);
			act.run(server, player[0], bed);
		}));
		if (later != null) {
			STEPS.add(new Step(5, server -> later.run(server, player[0], bed)));
		}
		STEPS.add(new Step(1, server -> end(server, player[0])));
	}

	// --- Helpers ----------------------------------------------------------------------------------------------

	private static final class BedPos {
		final BlockPos foot;
		final BlockPos head;

		BedPos(int x, int z) {
			this.foot = new BlockPos(x, Y, z);
			this.head = this.foot.south();
		}
	}

	private static void placeBed(MinecraftServer server, String prefix, BedPos bed) {
		TestCompat.command(server, prefix + "setblock " + bed.foot.getX() + " " + Y + " " + bed.foot.getZ() + " red_bed[facing=south,part=foot]");
		TestCompat.command(server, prefix + "setblock " + bed.head.getX() + " " + Y + " " + bed.head.getZ() + " red_bed[facing=south,part=head]");
	}

	private static void use(ServerPlayer player, InteractionHand hand, BlockPos pos) {
		BlockHitResult hit = new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5625, pos.getZ() + 0.5), Direction.UP, pos, false);
		player.gameMode.useItemOn(player, Compat.level(player), player.getItemInHand(hand), hand, hit);
	}

	private static boolean spawnAt(ServerPlayer player, BedPos bed) {
		return Compat.isSpawnAt(player, Compat.level(player), bed.head);
	}

	private static void begin(String name) {
		current = name;
		currentFailed = false;
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			fail(what);
		}
	}

	/** Records a failed check. Every failed check in a scenario is listed, so one result shows the whole picture. */
	private static void fail(String why) {
		if (!currentFailed) {
			RESULTS.add("FAIL " + current + ": " + why);
		} else {
			int last = RESULTS.size() - 1;
			RESULTS.set(last, RESULTS.get(last) + "; " + why);
		}
		currentFailed = true;
	}

	private static void end(MinecraftServer server, ServerPlayer player) {
		if (player != null && server.getPlayerList().getPlayer(player.getUUID()) != null) {
			if (player.isSleeping()) {
				TestCompat.wake(player);
			}
			server.getPlayerList().remove(player);
		}
		if (!currentFailed) {
			RESULTS.add("PASS " + current);
		}
	}

	private static void finish(MinecraftServer server) {
		RESULTS.add("DONE");
		Path out = Paths.get("scenario-result.txt");
		try {
			Files.write(out, String.join("\n", RESULTS).concat("\n").getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			e.printStackTrace();
		}
		for (String line : RESULTS) {
			System.out.println("[Scenario] " + line);
		}
		finished = true;
		server.halt(false);
		waitUntil = Integer.MAX_VALUE;
		// Some versions save and stop but leave a non-daemon thread behind, so the JVM never exits and the Gradle
		// task never ends. The result is already written, so give the normal shutdown a while, then end the process.
		watchdog(SHUTDOWN_GRACE_MS, 0, null);
	}

	/**
	 * Ends the JVM after {@code delayMs}. With a {@code timeoutResult}, only if the scenarios still haven't finished:
	 * then the result file says so, so a hung scenario fails the task instead of blocking it forever.
	 */
	private static void watchdog(final long delayMs, final int exitCode, final String timeoutResult) {
		Thread thread = new Thread(() -> {
			try {
				Thread.sleep(delayMs);
			} catch (InterruptedException e) {
				return;
			}
			if (timeoutResult != null) {
				if (finished) {
					return;
				}
				System.out.println("[Scenario] " + timeoutResult);
				try {
					Files.write(Paths.get("scenario-result.txt"), (timeoutResult + "\n").getBytes(StandardCharsets.UTF_8));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			Runtime.getRuntime().halt(exitCode);
		}, "Scenario watchdog");
		thread.setDaemon(true);
		thread.start();
	}
}
