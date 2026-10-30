package dev.flakyhunter.project;

import java.time.Instant;

public record ProjectSummary(String slug, String name, String defaultBranch, Role role, Instant createdAt) {

	static ProjectSummary of(Project p, Role role) {
		return new ProjectSummary(p.getSlug(), p.getName(), p.getDefaultBranch(), role, p.getCreatedAt());
	}

}
