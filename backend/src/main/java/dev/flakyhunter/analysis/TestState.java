package dev.flakyhunter.analysis;

/** The engine's verdict for one test (ADR-0004). */
public enum TestState {

	/** fewer runs than needed for a verdict */
	NEW,
	STABLE,
	/** a weak signal: worth watching */
	SUSPECT,
	FLAKY,
	/** fails consistently: a real bug, not flakiness */
	BROKEN

}
