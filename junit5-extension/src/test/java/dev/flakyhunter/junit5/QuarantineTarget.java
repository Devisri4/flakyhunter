package dev.flakyhunter.junit5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A test class that the extension tests run through the JUnit test kit. Its name doesn't end in "Test", so
 * Maven doesn't run it on its own.
 */
@ExtendWith(FlakyHunterExtension.class)
class QuarantineTarget {

	@Test
	void stableOne() {
	}

	@Test
	void flakyOne() {
	}

}
