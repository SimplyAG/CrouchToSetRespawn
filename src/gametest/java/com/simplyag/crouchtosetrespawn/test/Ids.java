package com.simplyag.crouchtosetrespawn.test;

import com.simplyag.crouchtosetrespawn.CrouchToSetRespawn;
import net.minecraft.resources.Identifier;

public final class Ids {
	private Ids() {
	}

	/** The mod's own advancement ids, which the mod itself keeps as plain paths. */
	public static Identifier mod(String path) {
		return Identifier.fromNamespaceAndPath(CrouchToSetRespawn.MOD_ID, path);
	}
}
