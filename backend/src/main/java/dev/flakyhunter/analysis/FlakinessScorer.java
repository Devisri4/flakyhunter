package dev.flakyhunter.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * The flakiness model (ADR-0004). Pure functions over a test's recent window, so every rule is unit-testable.
 *
 * <p>Signals (0–1): <b>R</b> passed on retry within a run, <b>F</b> flip rate pass↔fail, <b>C</b> one commit both
 * passed and failed. {@code score = min(1, wR·min(1, 2R) + wF·min(1, 2F) + wC·C)}.
 */
@Component
public class FlakinessScorer {

	public static final char PASS = 'P';
	public static final char FAIL = 'F';
	public static final char RETRY_PASS = 'R';

	private final ScoringProperties props;

	public FlakinessScorer(ScoringProperties props) {
		this.props = props;
	}

	/**
	 * A test's recent executed runs, oldest first.
	 *
	 * @param outcomes one char per run: P, F or R
	 * @param commits  the commit of each run (same length; null when unknown)
	 */
	public record Window(String outcomes, List<String> commits) {

		public static final Window EMPTY = new Window("", List.of());

		public Window {
			if (outcomes.length() != commits.size()) {
				throw new IllegalArgumentException("outcomes and commits must have the same length");
			}
		}

		/** Adds the newest run and drops the oldest ones beyond {@code size}. */
		public Window append(char outcome, String commit, int size) {
			String w = outcomes + outcome;
			List<String> c = new ArrayList<>(commits); // may hold nulls, so no List.copyOf
			c.add(commit);
			int drop = Math.max(0, w.length() - size);
			return new Window(w.substring(drop), Collections.unmodifiableList(new ArrayList<>(c.subList(drop, c.size()))));
		}

	}

	/**
	 * Everything the UI shows to explain a verdict: the raw signals (0–1) and how many score points each one
	 * contributed after weighting ({@code retryPart + flipPart + commitPart = score}, capped at 1).
	 */
	public record Stats(int runsInWindow, int flipCount, int retryPassCount, int failStreak, double retrySignal,
			double flipSignal, double commitSignal, double retryPart, double flipPart, double commitPart,
			BigDecimal score, TestState state) {
	}

	public Stats score(Window window) {
		String w = window.outcomes();
		int n = w.length();
		int retryPasses = count(w, RETRY_PASS);
		int flips = 0;
		for (int i = 1; i < n; i++) {
			if (failed(w.charAt(i)) != failed(w.charAt(i - 1))) {
				flips++;
			}
		}
		int failStreak = 0;
		for (int i = n - 1; i >= 0 && w.charAt(i) == FAIL; i--) {
			failStreak++;
		}

		double r = n == 0 ? 0 : (double) retryPasses / n;
		double f = n < 2 ? 0 : (double) flips / (n - 1);
		double c = commitSignal(window);
		double retryPart = props.retryWeight() * Math.min(1, 2 * r);
		double flipPart = props.flipWeight() * Math.min(1, 2 * f);
		double commitPart = props.commitWeight() * c;
		BigDecimal score = BigDecimal.valueOf(Math.min(1, retryPart + flipPart + commitPart))
				.setScale(3, RoundingMode.HALF_UP);

		return new Stats(n, flips, retryPasses, failStreak, r, f, c, retryPart, flipPart, commitPart, score,
				state(w, score, failStreak));
	}

	/** The rules of ADR-0004, evaluated in order. */
	private TestState state(String w, BigDecimal score, int failStreak) {
		if (w.length() < props.minRuns()) {
			return TestState.NEW;
		}
		// the last runs all failed outright (an R is not an F): a real regression, not flakiness
		if (failStreak >= props.brokenStreak()) {
			return TestState.BROKEN;
		}
		if (score.doubleValue() >= props.flakyThreshold()) {
			return TestState.FLAKY;
		}
		String recent = w.substring(Math.max(0, w.length() - props.suspectRetryLookback()));
		if (score.doubleValue() >= props.suspectThreshold() || recent.indexOf(RETRY_PASS) >= 0) {
			return TestState.SUSPECT;
		}
		return TestState.STABLE;
	}

	/** Share of commits with 2+ runs that both passed and failed. */
	private static double commitSignal(Window window) {
		Map<String, int[]> byCommit = new HashMap<>(); // [runs, passes, fails]
		for (int i = 0; i < window.outcomes().length(); i++) {
			String commit = window.commits().get(i);
			if (commit == null) {
				continue;
			}
			int[] counts = byCommit.computeIfAbsent(commit, k -> new int[3]);
			counts[0]++;
			if (failed(window.outcomes().charAt(i))) {
				counts[2]++;
			}
			else {
				counts[1]++;
			}
		}
		int repeated = 0;
		int inconsistent = 0;
		for (int[] counts : byCommit.values()) {
			if (counts[0] >= 2) {
				repeated++;
				if (counts[1] > 0 && counts[2] > 0) {
					inconsistent++;
				}
			}
		}
		return repeated == 0 ? 0 : (double) inconsistent / repeated;
	}

	private static boolean failed(char outcome) {
		return outcome == FAIL;
	}

	private static int count(String s, char ch) {
		int n = 0;
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == ch) {
				n++;
			}
		}
		return n;
	}

	public int windowSize() {
		return props.windowSize();
	}

	public ScoringProperties settings() {
		return props;
	}

}
