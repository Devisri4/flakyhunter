package dev.flakyhunter.project;

import java.util.Optional;
import java.util.UUID;

import dev.flakyhunter.common.ApiException;
import dev.flakyhunter.common.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * The one place that decides whether the caller may use a project. Every
 * {@code /api/projects/{slug}/**} handler starts with {@link #require}.
 * <ul>
 * <li>Members get access according to their role.</li>
 * <li>The public demo project can be <b>read</b> (VIEWER) by anyone, logged in or not.</li>
 * <li>Logged-in non-members get 404 (not 403), so project slugs can't be probed.</li>
 * <li>Anonymous visitors get 401 for anything else, whether the project exists or not.</li>
 * </ul>
 */
@Component
public class ProjectAccess {

	private final ProjectRepository projects;
	private final ProjectMemberRepository members;

	ProjectAccess(ProjectRepository projects, ProjectMemberRepository members) {
		this.projects = projects;
		this.members = members;
	}

	/** {@code userId} is null for an anonymous visitor of the public demo. */
	public record Access(Project project, Role role, UUID userId) {
	}

	public Access require(String slug, Role minimum) {
		Optional<UUID> userId = CurrentUser.optionalId();
		Optional<Project> found = projects.findBySlug(slug);
		boolean demoRead = minimum == Role.VIEWER && found.map(Project::isPublicDemo).orElse(false);

		if (userId.isEmpty()) {
			if (demoRead) {
				return new Access(found.get(), Role.VIEWER, null);
			}
			throw ApiException.unauthorized("Login required");
		}

		Project project = found.orElseThrow(ProjectAccess::notFound);
		Optional<ProjectMember> membership = members.findById(new ProjectMember.Key(project.getId(), userId.get()));
		if (membership.isEmpty()) {
			if (demoRead) {
				return new Access(project, Role.VIEWER, userId.get());
			}
			throw notFound();
		}
		if (!membership.get().getRole().atLeast(minimum)) {
			throw ApiException.forbidden("This needs the " + minimum + " role in this project");
		}
		return new Access(project, membership.get().getRole(), userId.get());
	}

	private static ApiException notFound() {
		return ApiException.notFound("Project not found");
	}

}
