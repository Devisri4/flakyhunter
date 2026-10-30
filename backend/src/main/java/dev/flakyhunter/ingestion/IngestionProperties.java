package dev.flakyhunter.ingestion;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("flakyhunter.ingestion")
public record IngestionProperties(
		@DefaultValue("true") boolean workerEnabled,
		@DefaultValue("2s") Duration pollInterval,
		@DefaultValue("3") int batchSize,
		@DefaultValue("3") int maxAttempts,
		@DefaultValue("5m") Duration stuckAfter,
		@DefaultValue("20") int maxFiles,
		/** zip-bomb guard: total size of all files of one upload after gunzip */
		@DefaultValue("52428800") long maxUncompressedBytes) {
}
