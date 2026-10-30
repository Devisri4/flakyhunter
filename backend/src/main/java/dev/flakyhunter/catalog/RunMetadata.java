package dev.flakyhunter.catalog;

import java.time.OffsetDateTime;

/** What the uploader tells us about the CI run (all optional except the time). */
public record RunMetadata(String commitSha, String branch, String ciProvider, String buildUrl,
		OffsetDateTime triggeredAt) {
}
