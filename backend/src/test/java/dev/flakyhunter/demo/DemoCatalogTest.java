package dev.flakyhunter.demo;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.flakyhunter.analysis.FlakinessScorer;
import dev.flakyhunter.analysis.ScoringProperties;
import dev.flakyhunter.analysis.TestState;
import dev.flakyhunter.parsing.ParsedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoCatalogTest {

	private final DemoCatalog catalog = new DemoCatalog(300);

	@Test
	void hasThreeHundredUniqueTests() {
		assertThat(catalog.tests()).hasSize(300);
		assertThat(catalog.tests().stream().map(t -> t.suite() + "::" + t.name()).distinct()).hasSize(300);
	}

	@Test
	void theSameSlotAlwaysGivesTheSameResults() {
		assertThat(catalog.run(123_456, 0)).isEqualTo(new DemoCatalog(300).run(123_456, 0));
	}

	@Test
	void commitsAreShasAndEveryFifthRunRepeatsTheLastCommit() {
		assertThat(DemoCatalog.commitFor(101)).matches("[0-9a-f]{40}");
		assertThat(DemoCatalog.commitFor(100)).isEqualTo(DemoCatalog.commitFor(99));
		assertThat(DemoCatalog.commitFor(101)).isNotEqualTo(DemoCatalog.commitFor(102));
	}

	/** Runs 60 days of history through the real scoring model: the demo must show every interesting state. */
	@Test
	void sixtyDaysOfHistoryProduceAllStates() {
		FlakinessScorer scorer = new FlakinessScorer(ScoringProperties.defaults());
		Map<String, FlakinessScorer.Window> windows = new HashMap<>();
		long last = 500_000;
		long first = last - 239;
		for (long slot = first; slot <= last; slot++) {
			for (ParsedTest t : catalog.run(slot, last - 9)) {
				char c = switch (t.outcome()) {
					case PASSED -> 'P';
					case FAILED -> 'F';
					case PASSED_ON_RETRY -> 'R';
					case SKIPPED -> 0;
				};
				String key = t.suite() + "::" + t.name();
				FlakinessScorer.Window w = windows.getOrDefault(key, FlakinessScorer.Window.EMPTY);
				windows.put(key, c == 0 ? w : w.append(c, DemoCatalog.commitFor(slot), 30));
			}
		}
		Map<TestState, Integer> states = new EnumMap<>(TestState.class);
		windows.values().forEach(w -> states.merge(scorer.score(w).state(), 1, Integer::sum));

		assertThat(states.getOrDefault(TestState.STABLE, 0)).isGreaterThan(150);
		assertThat(states.getOrDefault(TestState.FLAKY, 0)).isBetween(8, 60);
		assertThat(states.getOrDefault(TestState.SUSPECT, 0)).isPositive();
		assertThat(states.getOrDefault(TestState.NEW, 0)).isPositive();
		// broken episodes really are consecutive failures (not random noise)
		assertThat(catalog.tests().stream().filter(t -> t.kind() == DemoCatalog.Kind.BROKEN_EPISODES)).isNotEmpty();
		List<DemoCatalog.DemoTest> broken = catalog.tests().stream()
				.filter(t -> t.kind() == DemoCatalog.Kind.BROKEN_EPISODES).toList();
		assertThat(broken.stream().anyMatch(t -> windows.get(t.suite() + "::" + t.name()).outcomes().contains("FFF")))
				.isTrue();
	}

}
