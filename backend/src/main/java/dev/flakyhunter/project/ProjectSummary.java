package dev.flakyhunter.project;

import java.time.Instant;

/** {@code publicDemo}: the read-only demo anyone can open (the UI shows a banner). */
public record ProjectSummary(String slug, String name, String defaultBranch, Role role, Instant createdAt,
		boolean publicDemo) {

	static ProjectSummary of(Project p, Role role) {
		return new ProjectSummary(p.getSlug(), p.getName(), p.getDefaultBranch(), role, p.getCreatedAt(),
				p.isPublicDemo());
	}

}
