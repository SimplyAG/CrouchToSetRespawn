package com.simplyag.crouchtosetrespawn.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;

/** What the test-only mixins saw, keyed by player. Tests use fresh mock players, so nothing is shared. */
public final class Recorder {
	public static final Map<UUID, List<Component>> MESSAGES = new HashMap<>();
	public static final Map<UUID, AtomicInteger> HINT_GRANTS = new HashMap<>();
	public static final List<Component> BROADCASTS = new ArrayList<>();

	private Recorder() {
	}

	public static List<Component> messages(UUID player) {
		return MESSAGES.getOrDefault(player, List.of());
	}

	public static int hintGrants(UUID player) {
		AtomicInteger count = HINT_GRANTS.get(player);
		return count == null ? 0 : count.get();
	}
}
