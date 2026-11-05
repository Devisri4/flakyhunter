package dev.flakyhunter.catalog;

import java.util.Map;

import dev.flakyhunter.analysis.TestState;
import dev.flakyhunter.project.ProjectAccess;
import dev.flakyhunter.project.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Browser endpoints for tests and failure signatures of a project (viewer role is enough). */
@RestController
@RequestMapping("/api/projects/{slug}")
class TestController {

	private final ProjectAccess access;
	private final TestQueries tests;

	TestController(ProjectAccess access, TestQueries tests) {
		this.access = access;
		this.tests = tests;
	}

	@GetMapping("/tests")
	RunQueries.Page<TestQueries.TestCaseView> list(@PathVariable String slug,
			@RequestParam(required = false) TestState state,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "score") String sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		var project = access.require(slug, Role.VIEWER).project();
		return tests.list(project.getId(), state, q, sort, Math.max(0, page), Math.clamp(size, 1, 100));
	}

	@GetMapping("/tests/summary")
	Map<String, Long> summary(@PathVariable String slug) {
		return tests.stateCounts(access.require(slug, Role.VIEWER).project().getId());
	}

	@GetMapping("/signatures")
	RunQueries.Page<TestQueries.SignatureView> signatures(@PathVariable String slug,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		var project = access.require(slug, Role.VIEWER).project();
		return tests.signatures(project.getId(), Math.max(0, page), Math.clamp(size, 1, 100));
	}

}
