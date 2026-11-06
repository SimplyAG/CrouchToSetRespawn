package com.simplyag.crouchtosetrespawn.util;

/**
 * Implemented by ServerPlayer (through ServerPlayerMixin), so code in other mixins can ask it whether the bed use in
 * progress may set spawn. Needed on 1.15, where the spawn calls live in Player rather than ServerPlayer.
 */
public interface BedUseDecider {
	/** Records vanilla's decision for this bed use; returns whether spawn may be set. */
	boolean crouchtosetrespawn$decide(boolean vanillaSets);
}
