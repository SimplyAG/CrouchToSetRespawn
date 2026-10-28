package com.simplyag.vanillaclienttest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Joins the staged dedicated server (Crouch to Set Respawn + Fabric API) from a client that doesn't have the mod,
 * and checks the mod's behaviour arrives through vanilla packets only: the hint toast on a normal bed click, and
 * "Respawn point set" plus sleeping on a sneak click.
 */
public class VanillaClientJoinTest implements FabricClientGameTest {
	public static final List<AdvancementHolder> ADVANCEMENT_TOASTS = new CopyOnWriteArrayList<>();
	private static final List<Component> MESSAGES = new CopyOnWriteArrayList<>();
	private static final Identifier HINT = Identifier.fromNamespaceAndPath("crouchtosetrespawn", "spawn_hint");
	private static final String HINT_TITLE = "Bed not set as spawn";
	private static final String SET_SPAWN = "block.minecraft.set_spawn";

	private ClientGameTestContext ctx;

	@Override
	public void runTest(ClientGameTestContext context) {
		this.ctx = context;
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> MESSAGES.add(message));

		String address = System.getProperty("vanillaclienttest.server");
		context.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
			new ServerData("Crouch to Set Respawn test", address, ServerData.Type.OTHER), false, null));
		context.waitFor(mc -> {
			if (mc.gui.screen() instanceof DisconnectedScreen) {
				throw new AssertionError("Vanilla client was refused by the server: " + mc.gui.screen().getNarrationMessage().getString());
			}
			return mc.player != null && mc.level != null && mc.player.onGround();
		}, 20 * 120);
		context.waitTicks(20);
		log("joined " + address + " as " + context.computeOnClient(mc -> mc.player.getGameProfile().name()));

		// Scene: a stone pad with two south-facing beds, two blocks east (A) and west (B) of the player, at night.
		BlockPos stand = context.computeOnClient(mc -> mc.player.blockPosition());
		BlockPos bedA = stand.east(2);
		BlockPos bedB = stand.west(2);
		command("difficulty peaceful");
		command("gamemode survival");
		command("gamerule advance_time false");
		command("time set 18000");
		command(fill(stand.offset(-4, 0, -3), stand.offset(4, 3, 4), "air"));
		command(fill(stand.offset(-4, -1, -3), stand.offset(4, -1, 4), "stone"));
		command(setblock(bedA, "red_bed[facing=south,part=foot]"));
		command(setblock(bedA.south(), "red_bed[facing=south,part=head]"));
		command(setblock(bedB, "blue_bed[facing=south,part=foot]"));
		command(setblock(bedB.south(), "blue_bed[facing=south,part=head]"));
		context.waitFor(mc -> mc.level.getBlockState(bedB.south()).getBlock().getDescriptionId().contains("bed"), 200);
		context.waitTicks(10);

		// 1. Normal click on bed B: sleeps, no spawn message, and the hint toast pops then is removed again.
		MESSAGES.clear();
		ADVANCEMENT_TOASTS.clear();
		lookAt(bedB);
		context.getInput().pressKey(o -> o.keyUse);
		context.waitFor(mc -> mc.player.isSleeping(), 100);
		context.waitFor(mc -> !ADVANCEMENT_TOASTS.isEmpty(), 100);
		check(ADVANCEMENT_TOASTS.stream().anyMatch(a -> a.id().equals(HINT)
			&& a.value().display().orElseThrow().title().getString().equals(HINT_TITLE)), "hint toast '" + HINT_TITLE + "' shown");
		context.waitTicks(10);
		check(context.computeOnClient(mc -> mc.player.connection.getAdvancements().get(HINT) == null),
			"hint advancement removed from the client's advancement tree again");
		check(!sawMessage(SET_SPAWN), "no 'Respawn point set' on a normal sleep");
		check(MESSAGES.stream().noneMatch(m -> m.getString().contains(HINT_TITLE)), "hint never announced in chat");
		wake();

		// 2. Sneak click on bed A: "Respawn point set", then sleeping.
		MESSAGES.clear();
		lookAt(bedA);
		TestInput input = context.getInput();
		input.holdKey(o -> o.keyShift);
		context.waitTicks(3);
		input.pressKey(o -> o.keyUse);
		context.waitFor(mc -> mc.player.isSleeping(), 100);
		input.releaseKey(o -> o.keyShift);
		context.waitFor(mc -> sawMessage(SET_SPAWN), 100);
		check(true, "sneak click set spawn ('Respawn point set') and slept");
		wake();

		context.runOnClient(mc -> mc.disconnect(new TitleScreen(), false));
		context.waitFor(mc -> mc.level == null && mc.gui.screen() instanceof TitleScreen, 200);
		log("disconnected cleanly");
	}

	private void lookAt(BlockPos bedFoot) {
		Vec3 target = Vec3.atCenterOf(bedFoot).add(0, 0.05, 0);
		this.ctx.runOnClient(mc -> mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, target));
		this.ctx.waitTicks(2);
		boolean aimed = this.ctx.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit
			&& (hit.getBlockPos().equals(bedFoot) || hit.getBlockPos().equals(bedFoot.south())));
		check(aimed, "crosshair on the bed at " + bedFoot.toShortString());
	}

	/** What the in-bed screen's "Leave Bed" button sends. */
	private void wake() {
		this.ctx.runOnClient(mc -> mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SLEEPING)));
		this.ctx.waitFor(mc -> !mc.player.isSleeping() && mc.gui.screen() == null, 100);
		this.ctx.waitTicks(5);
	}

	private void command(String command) {
		this.ctx.runOnClient(mc -> mc.player.connection.sendCommand(command));
		this.ctx.waitTicks(2);
	}

	private static String fill(BlockPos from, BlockPos to, String block) {
		return "fill %d %d %d %d %d %d %s".formatted(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(), block);
	}

	private static String setblock(BlockPos pos, String block) {
		return "setblock %d %d %d %s".formatted(pos.getX(), pos.getY(), pos.getZ(), block);
	}

	private static boolean sawMessage(String key) {
		return MESSAGES.stream().anyMatch(m -> m.getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
		log("ok: " + what);
	}

	private static void log(String line) {
		System.out.println("[VanillaClientJoinTest] " + line);
	}
}
