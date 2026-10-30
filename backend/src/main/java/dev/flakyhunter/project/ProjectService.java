package dev.flakyhunter.project;

import java.util.List;

import dev.flakyhunter.common.ApiException;
import dev.flakyhunter.common.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

	private final ProjectRepository projects;
	private final ProjectMemberRepository members;
	private final ProjectAccess access;

	ProjectService(ProjectRepository projects, ProjectMemberRepository members, ProjectAccess access) {
		this.projects = projects;
		this.members = members;
		this.access = access;
	}

	@Transactional(readOnly = true)
	public List<ProjectSummary> myProjects() {
		return members.findProjectsOfUser(CurrentUser.id());
	}

	@Transactional
	public ProjectSummary create(String name, String requestedSlug, String defaultBranch) {
		String slug = requestedSlug == null || requestedSlug.isBlank() ? Slugs.fromName(name) : requestedSlug.strip();
		if (!Slugs.isValid(slug)) {
			throw ApiException.badRequest(
					"Slug must be 3-40 characters: lower-case letters, digits and single hyphens (e.g. acme-shop)");
		}
		if (projects.existsBySlug(slug)) {
			throw ApiException.conflict("The slug '" + slug + "' is already taken");
		}
		String branch = defaultBranch == null || defaultBranch.isBlank() ? "main" : defaultBranch.strip();
		Project project = projects.save(new Project(slug, name.strip(), branch));
		members.save(new ProjectMember(project.getId(), CurrentUser.id(), Role.OWNER));
		return ProjectSummary.of(project, Role.OWNER);
	}

	@Transactional(readOnly = true)
	public ProjectSummary get(String slug) {
		ProjectAccess.Access a = access.require(slug, Role.VIEWER);
		return ProjectSummary.of(a.project(), a.role());
	}

	@Transactional
	public ProjectSummary update(String slug, String name, String defaultBranch) {
		ProjectAccess.Access a = access.require(slug, Role.OWNER);
		Project project = a.project();
		if (name != null && !name.isBlank()) {
			project.setName(name.strip());
		}
		if (defaultBranch != null && !defaultBranch.isBlank()) {
			project.setDefaultBranch(defaultBranch.strip());
		}
		return ProjectSummary.of(projects.save(project), a.role());
	}

	@Transactional
	public void delete(String slug) {
		projects.delete(access.require(slug, Role.OWNER).project());
	}

}
