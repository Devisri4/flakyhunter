package dev.flakyhunter.catalog;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dev.flakyhunter.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Read side for runs (plain SQL; the dashboard never needs entities). */
@Component
public class RunQueries {

	private final JdbcClient db;

	RunQueries(JdbcClient db) {
		this.db = db;
	}

	public record RunSummary(long id, String commitSha, String branch, String ciProvider, String buildUrl,
			OffsetDateTime triggeredAt, long durationMs, int total, int passed, int failed, int skipped, int flaky) {
	}

	public record ResultView(long testCaseId, String suite, String name, String file, String outcome, int attempts,
			int failedAttempts, long durationMs, String failureType, String message, String stack) {
	}

	public record RunDetail(RunSummary run, List<ResultView> failed, List<ResultView> flaky, List<ResultView> slowest) {
	}

	public record Page<T>(List<T> content, int page, int size, long totalElements) {
	}

	private static final String RUN_COLUMNS = """
			id, commit_sha, branch, ci_provider, build_url, triggered_at, duration_ms,
			total, passed, failed, skipped, flaky""";

	public Page<RunSummary> list(UUID projectId, String branch, int page, int size) {
		String filter = branch == null || branch.isBlank() ? "" : " and branch = :branch";
		var query = db.sql("select " + RUN_COLUMNS + " from test_runs where project_id = :project" + filter
				+ " order by triggered_at desc, id desc limit :limit offset :offset")
				.param("project", projectId)
				.param("limit", size)
				.param("offset", (long) page * size);
		var count = db.sql("select count(*) from test_runs where project_id = :project" + filter)
				.param("project", projectId);
		if (!filter.isEmpty()) {
			query = query.param("branch", branch.strip());
			count = count.param("branch", branch.strip());
		}
		return new Page<>(query.query(RunSummary.class).list(), page, size, count.query(Long.class).single());
	}

	public List<String> branches(UUID projectId) {
		return db.sql("""
				select branch from test_runs where project_id = :project and branch is not null
				group by branch order by max(triggered_at) desc limit 50""")
				.param("project", projectId)
				.query(String.class).list();
	}

	public RunDetail detail(UUID projectId, long runId) {
		RunSummary run = db.sql("select " + RUN_COLUMNS + " from test_runs where id = :id and project_id = :project")
				.param("id", runId)
				.param("project", projectId)
				.query(RunSummary.class).optional()
				.orElseThrow(() -> ApiException.notFound("Run not found"));
		return new RunDetail(run,
				results(runId, "r.outcome = 'FAILED'", "c.suite, c.name", 500),
				results(runId, "r.outcome = 'PASSED_ON_RETRY'", "c.suite, c.name", 500),
				results(runId, "r.outcome <> 'SKIPPED'", "r.duration_ms desc", 10));
	}

	private List<ResultView> results(long runId, String where, String orderBy, int limit) {
		return db.sql("""
				select c.id as test_case_id, c.suite, c.name, c.file, r.outcome, r.attempts, r.failed_attempts,
				       r.duration_ms, r.failure_type, r.message, r.stack
				from test_results r join test_cases c on c.id = r.test_case_id
				where r.run_id = :run and %s
				order by %s limit %d""".formatted(where, orderBy, limit))
				.param("run", runId)
				.query(ResultView.class).list();
	}

}
