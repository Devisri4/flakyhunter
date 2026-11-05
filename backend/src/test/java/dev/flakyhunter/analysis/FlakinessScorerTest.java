package dev.flakyhunter.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.flakyhunter.analysis.FlakinessScorer.Stats;
import dev.flakyhunter.analysis.FlakinessScorer.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The scoring model as a table: P pass, F fail, R passed after a retry — oldest first. */
class FlakinessScorerTest {

	private final FlakinessScorer scorer = new FlakinessScorer(ScoringProperties.defaults());

	@ParameterizedTest(name = "{0} => {1} ({2})")
	@CsvSource({
			// too little history for a verdict
			"PPPP,               NEW,     0.000",
			"FFFR,               NEW,     0.458", // the score is still computed and shown, the verdict waits
			// always green
			"PPPPPPPPPP,         STABLE,  0.000",
			// a real regression: 3 straight failures, only one flip => broken, not flaky
			"PPPPPPPFFF,         BROKEN,  0.078",
			"FFFFFFFFFF,         BROKEN,  0.000",
			// classic flaky test: retries and frequent flips
			"PPFPPRPFPP,         FLAKY,   0.401",
			"PFPFPFPFPF,         FLAKY,   0.350",
			"RRRRRRRRRR,         FLAKY,   0.450",
			// weak signals => suspect
			"PPPPPPPPPR,         SUSPECT, 0.090",
			"PPPPPPPPFP,         SUSPECT, 0.156",
			// a regression that was fixed: two flips; shows as suspect until it ages out of the window
			"PPPPPFFFP,          SUSPECT, 0.175",
			// ... and in a full 30-run window the same story is stable again
			"PPPPPPPPPPPPPPPPPPPPFFFPPPPPPP, STABLE, 0.048",
	})
	void verdicts(String outcomes, TestState expectedState, String expectedScore) {
		Stats stats = scorer.score(new Window(outcomes, nulls(outcomes.length())));
		assertThat(stats.state()).isEqualTo(expectedState);
		assertThat(stats.score()).isEqualByComparingTo(expectedScore);
	}

	@Test
	void countersExplainTheScore() {
		Stats stats = scorer.score(new Window("PPFPPRPFPP", nulls(10)));
		assertThat(stats.runsInWindow()).isEqualTo(10);
		assertThat(stats.flipCount()).isEqualTo(4);      // P→F, F→P, P→F, F→P (R counts as a pass)
		assertThat(stats.retryPassCount()).isEqualTo(1);
		assertThat(stats.failStreak()).isZero();
		assertThat(stats.retrySignal()).isEqualTo(0.1);
	}

	@Test
	void sameCommitPassingAndFailingIsAStrongSignal() {
		// commit "a" ran twice: once green, once red
		Window window = new Window("PFPPPP", List.of("a", "a", "b", "c", "d", "e"));
		Stats stats = scorer.score(window);
		assertThat(stats.commitSignal()).isEqualTo(1.0);
		assertThat(stats.score()).isEqualByComparingTo("0.480");
		assertThat(stats.state()).isEqualTo(TestState.FLAKY);
	}

	@Test
	void commitsWithOneRunDontCount() {
		Stats stats = scorer.score(new Window("PFPFP", List.of("a", "b", "c", "d", "e")));
		assertThat(stats.commitSignal()).isZero();
	}

	@Test
	void appendKeepsOnlyTheNewestRuns() {
		Window w = Window.EMPTY;
		for (char c : "PPFRP".toCharArray()) {
			w = w.append(c, c == 'F' ? null : "sha-" + c, 3);
		}
		assertThat(w.outcomes()).isEqualTo("FRP");
		assertThat(w.commits()).containsExactly(null, "sha-R", "sha-P");
	}

	@Test
	void thresholdsComeFromConfiguration() {
		var strict = new FlakinessScorer(new ScoringProperties(30, 5, 0.45, 0.35, 0.20, 0.10, 0.05, 3, 10));
		assertThat(strict.score(new Window("PPPPPPPPFP", nulls(10))).state()).isEqualTo(TestState.FLAKY);
	}

	private static List<String> nulls(int n) {
		return new ArrayList<>(Collections.nCopies(n, (String) null));
	}

}
