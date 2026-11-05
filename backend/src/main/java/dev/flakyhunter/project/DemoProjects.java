package dev.flakyhunter.project;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Lets the demo module create and find the public demo project without reaching into this module's repositories. */
@Component
public class DemoProjects {

	private final ProjectRepository projects;

	DemoProjects(ProjectRepository projects) {
		this.projects = projects;
	}

	/**
	 * The id of the public demo project with this slug, creating it if needed. Returns null when a normal
	 * (non-demo) project already uses the slug — that project is never touched.
	 */
	@Transactional
	public UUID findOrCreate(String slug, String name) {
		return projects.findBySlug(slug)
				.map(p -> p.isPublicDemo() ? p.getId() : null)
				.orElseGet(() -> projects.save(Project.publicDemo(slug, name)).getId());
	}

}
