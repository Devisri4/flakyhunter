package dev.flakyhunter.dashboard;

import java.util.List;
import java.util.UUID;

import dev.flakyhunter.common.ApiException;
import dev.flakyhunter.project.ProjectAccess;
import dev.flakyhunter.project.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard endpoints (viewer role is enough; the public demo can be read anonymously). */
@RestController
@RequestMapping("/api/projects/{slug}")
class DashboardController {

	private static final List<Integer> PERIODS = List.of(7, 30, 90);

	private final ProjectAccess access;
	private final DashboardQueries dashboard;

	DashboardController(ProjectAccess access, DashboardQueries dashboard) {
		this.access = access;
		this.dashboard = dashboard;
	}

	@GetMapping("/summary")
	DashboardQueries.Summary summary(@PathVariable String slug, @RequestParam(defaultValue = "30") int days) {
		return dashboard.summary(project(slug), period(days));
	}

	@GetMapping("/trends")
	List<DashboardQueries.TrendPoint> trends(@PathVariable String slug, @RequestParam(defaultValue = "30") int days) {
		return dashboard.trends(project(slug), period(days));
	}

	@GetMapping("/tests/newly-flaky")
	List<DashboardQueries.NewlyFlaky> newlyFlaky(@PathVariable String slug,
			@RequestParam(defaultValue = "7") int days) {
		return dashboard.newlyFlaky(project(slug), Math.clamp(days, 1, 90), 10);
	}

	private UUID project(String slug) {
		return access.require(slug, Role.VIEWER).project().getId();
	}

	private static int period(int days) {
		if (!PERIODS.contains(days)) {
			throw ApiException.badRequest("days must be one of " + PERIODS);
		}
		return days;
	}

}
