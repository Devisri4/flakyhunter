package dev.flakyhunter.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Weights and thresholds of the flakiness model (ADR-0004), configurable as flakyhunter.scoring.*. */
@ConfigurationProperties("flakyhunter.scoring")
public record ScoringProperties(
		@DefaultValue("30") int windowSize,
		@DefaultValue("5") int minRuns,
		@DefaultValue("0.45") double retryWeight,
		@DefaultValue("0.35") double flipWeight,
		@DefaultValue("0.20") double commitWeight,
		@DefaultValue("0.30") double flakyThreshold,
		@DefaultValue("0.10") double suspectThreshold,
		@DefaultValue("3") int brokenStreak,
		@DefaultValue("10") int suspectRetryLookback) {

	public static ScoringProperties defaults() {
		return new ScoringProperties(30, 5, 0.45, 0.35, 0.20, 0.30, 0.10, 3, 10);
	}

}
