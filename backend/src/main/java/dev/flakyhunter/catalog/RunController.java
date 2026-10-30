package dev.flakyhunter.catalog;

import java.util.List;

import dev.flakyhunter.ingestion.IngestionService;
import dev.flakyhunter.project.ProjectAccess;
import dev.flakyhunter.project.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Browser endpoints for runs and uploads of a project (viewer role is enough). */
@RestController
@RequestMapping("/api/projects/{slug}")
class RunController {

	private final ProjectAccess access;
	private final RunQueries runs;
	private final IngestionService ingestion;

	RunController(ProjectAccess access, RunQueries runs, IngestionService ingestion) {
		this.access = access;
		this.runs = runs;
		this.ingestion = ingestion;
	}

	@GetMapping("/runs")
	RunQueries.Page<RunQueries.RunSummary> list(@PathVariable String slug,
			@RequestParam(required = false) String branch,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		var project = access.require(slug, Role.VIEWER).project();
		return runs.list(project.getId(), branch, Math.max(0, page), Math.clamp(size, 1, 100));
	}

	@GetMapping("/runs/{id}")
	RunQueries.RunDetail detail(@PathVariable String slug, @PathVariable long id) {
		return runs.detail(access.require(slug, Role.VIEWER).project().getId(), id);
	}

	@GetMapping("/branches")
	List<String> branches(@PathVariable String slug) {
		return runs.branches(access.require(slug, Role.VIEWER).project().getId());
	}

	@GetMapping("/uploads")
	List<IngestionService.JobView> uploads(@PathVariable String slug) {
		return ingestion.recentJobs(access.require(slug, Role.VIEWER).project().getId());
	}

}
