package dev.flakyhunter.project;

import java.util.UUID;

import dev.flakyhunter.common.ApiException;
import dev.flakyhunter.common.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * The one place that decides whether the caller may use a project. Every
 * {@code /api/projects/{slug}/**} handler starts with {@link #require}.
 * Non-members get 404 (not 403), so project slugs can't be probed.
 */
@Component
public class ProjectAccess {

	private final ProjectRepository projects;
	private final ProjectMemberRepository members;

	ProjectAccess(ProjectRepository projects, ProjectMemberRepository members) {
		this.projects = projects;
		this.members = members;
	}

	public record Access(Project project, Role role, UUID userId) {
	}

	public Access require(String slug, Role minimum) {
		UUID userId = CurrentUser.id();
		Project project = projects.findBySlug(slug).orElseThrow(ProjectAccess::notFound);
		ProjectMember membership = members.findById(new ProjectMember.Key(project.getId(), userId))
				.orElseThrow(ProjectAccess::notFound);
		if (!membership.getRole().atLeast(minimum)) {
			throw ApiException.forbidden("This needs the " + minimum + " role in this project");
		}
		return new Access(project, membership.getRole(), userId);
	}

	private static ApiException notFound() {
		return ApiException.notFound("Project not found");
	}

}
