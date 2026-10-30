package dev.flakyhunter.parsing;

/**
 * One test's outcome in one run, independent of the report format.
 *
 * @param attempts       how many times it ran in this run (1 + reruns)
 * @param failedAttempts how many of those attempts failed
 */
public record ParsedTest(String suite, String name, String file, Outcome outcome, int attempts, int failedAttempts,
		long durationMs, String failureType, String message, String stack) {

	public enum Outcome {
		PASSED, FAILED, SKIPPED, PASSED_ON_RETRY
	}

	static final int MAX_MESSAGE = 2 * 1024;
	static final int MAX_STACK = 8 * 1024;

	public ParsedTest {
		message = truncate(message, MAX_MESSAGE);
		stack = truncate(stack, MAX_STACK);
	}

	/** Combines two entries for the same test in one run (e.g. a report that lists a rerun separately). */
	ParsedTest mergeWith(ParsedTest other) {
		int totalAttempts = attempts + other.attempts;
		int totalFailed = failedAttempts + other.failedAttempts;
		Outcome merged;
		if (outcome == Outcome.SKIPPED && other.outcome == Outcome.SKIPPED) {
			merged = Outcome.SKIPPED;
		}
		else if (totalFailed == 0) {
			merged = Outcome.PASSED;
		}
		else if (totalFailed >= nonSkippedAttempts(this) + nonSkippedAttempts(other)) {
			merged = Outcome.FAILED;
		}
		else {
			merged = Outcome.PASSED_ON_RETRY;
		}
		ParsedTest failing = failedAttempts > 0 ? this : other;
		return new ParsedTest(suite, name, file != null ? file : other.file, merged, totalAttempts, totalFailed,
				durationMs + other.durationMs, failing.failureType, failing.message, failing.stack);
	}

	private static int nonSkippedAttempts(ParsedTest t) {
		return t.outcome == Outcome.SKIPPED ? 0 : t.attempts;
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		if (trimmed.isEmpty()) {
			return null;
		}
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}

}
