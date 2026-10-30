package dev.flakyhunter.catalog;

import dev.flakyhunter.common.Tokens;

/**
 * A test's stable identity: SHA-256 of {@code suite + "::" + name}. The file path is left out on
 * purpose, so moving a test to another file keeps its history.
 */
public final class Fingerprints {

	private Fingerprints() {
	}

	public static String of(String suite, String name) {
		return Tokens.sha256Hex(suite.strip() + "::" + name.strip());
	}

}
