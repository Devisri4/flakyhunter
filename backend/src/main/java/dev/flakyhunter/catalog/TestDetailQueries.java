package dev.flakyhunter.catalog;

import java.math.BigDecimal;
import java.sql.Array;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.flakyhunter.analysis.FlakinessScorer;
import dev.flakyhunter.analysis.ScoringProperties;
import dev.flakyhunter.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Read side for one test's detail page. */
@Component
public class TestDetailQueries {

	private final JdbcClient db;
	private final FlakinessScorer scorer;

	TestDetailQueries(JdbcClient db, FlakinessScorer scorer) {
		this.db = db;
		this.scorer = scorer;
	}

	/** Why the test has its score: each signal and the points it adds, plus the rules' thresholds. */
	public record Breakdown(double retrySignal, double flipSignal, double commitSignal, double retryPart,
			double flipPart, double commitPart, BigDecimal score, String state, ScoringProperties settings) {
	}

	public record StateChange(String fromState, String toState, BigDecimal score, Long runId, OffsetDateTime changedAt) {
	}

	public record TestSignature(long id, String exceptionType, String normalizedMessage, int occurrences,
			OffsetDateTime lastSeen) {
	}

	public record DurationStats(Long avgMs, Long p95Ms, Long maxMs) {
	}

	/** One quarantine of the test; {@code liftedAt == null} means it is active. */
	public record QuarantineView(long id, String reason, OffsetDateTime createdAt, String createdBy,
			OffsetDateTime expiresAt, OffsetDateTime liftedAt, String liftedBy, String liftReason) {
	}

	/** {@code quarantine}: the active one (or null); {@code quarantines}: the history, newest first. */
	public record TestDetail(TestQueries.TestCaseView test, OffsetDateTime firstSeen, Breakdown breakdown,
			DurationStats durations, List<StateChange> history, List<TestSignature> signatures,
			QuarantineView quarantine, List<QuarantineView> quarantines) {
	}

	public record TestResult(long runId, OffsetDateTime triggeredAt, String branch, String commitSha, String outcome,
			int attempts, int failedAttempts, long durationMs, String message) {
	}

	private record Row(TestQueries.TestCaseView view, OffsetDateTime firstSeen, List<String> commits) {
	}

	public TestDetail detail(UUID projectId, long testId) {
		Row row = db.sql("""
				select id, suite, name, file, state, score, window_outcomes, runs_in_window, flip_count,
				       retry_pass_count, fail_streak, last_seen, first_seen, window_commits, %s
				from test_cases where id = :id and project_id = :project""".formatted(
				TestQueries.quarantinedColumn("test_cases")))
				.param("id", testId)
				.param("project", projectId)
				.query((rs, n) -> {
					Array array = rs.getArray("window_commits");
					List<String> commits = new ArrayList<>();
					for (String c : array == null ? new String[0] : (String[]) array.getArray()) {
						commits.add(c == null || c.isEmpty() ? null : c);
					}
					return new Row(new TestQueries.TestCaseView(rs.getLong("id"), rs.getString("suite"),
							rs.getString("name"), rs.getString("file"), rs.getString("state"), rs.getBigDecimal("score"),
							rs.getString("window_outcomes"), rs.getInt("runs_in_window"), rs.getInt("flip_count"),
							rs.getInt("retry_pass_count"), rs.getInt("fail_streak"),
							rs.getObject("last_seen", OffsetDateTime.class), rs.getBoolean("quarantined")),
							rs.getObject("first_seen", OffsetDateTime.class), commits);
				})
				.optional()
				.orElseThrow(() -> ApiException.notFound("Test not found"));

		String outcomes = row.view().windowOutcomes();
		List<String> commits = new ArrayList<>(row.commits());
		while (commits.size() < outcomes.length()) {
			commits.add(null);
		}
		FlakinessScorer.Stats stats = scorer.score(new FlakinessScorer.Window(outcomes, commits.subList(0, outcomes.length())));
		Breakdown breakdown = new Breakdown(stats.retrySignal(), stats.flipSignal(), stats.commitSignal(),
				stats.retryPart(), stats.flipPart(), stats.commitPart(), row.view().score(), row.view().state(),
				scorer.settings());

		DurationStats durations = db.sql("""
				select avg(duration_ms)::bigint as avg_ms,
				       (percentile_cont(0.95) within group (order by duration_ms))::bigint as p95_ms,
				       max(duration_ms) as max_ms
				from (select r.duration_ms from test_results r join test_runs tr on tr.id = r.run_id
				      where r.test_case_id = :id and r.outcome <> 'SKIPPED'
				      order by tr.triggered_at desc limit 50) recent""")
				.param("id", testId).query(DurationStats.class).single();

		List<StateChange> history = db.sql("""
				select from_state, to_state, score, run_id, changed_at from state_changes
				where test_case_id = :id order by changed_at desc, id desc limit 50""")
				.param("id", testId).query(StateChange.class).list();

		List<TestSignature> signatures = db.sql("""
				select s.id, s.exception_type, s.normalized_message, count(*) as occurrences,
				       max(tr.triggered_at) as last_seen
				from test_results r
				join failure_signatures s on s.id = r.signature_id
				join test_runs tr on tr.id = r.run_id
				where r.test_case_id = :id
				group by s.id, s.exception_type, s.normalized_message
				order by occurrences desc, last_seen desc limit 10""")
				.param("id", testId).query(TestSignature.class).list();

		List<QuarantineView> quarantines = db.sql("""
				select q.id, q.reason, q.created_at, cu.display_name as created_by, q.expires_at, q.lifted_at,
				       lu.display_name as lifted_by, q.lift_reason
				from quarantines q
				left join users cu on cu.id = q.created_by
				left join users lu on lu.id = q.lifted_by
				where q.test_case_id = :id order by q.created_at desc limit 20""")
				.param("id", testId).query(QuarantineView.class).list();
		QuarantineView active = row.view().quarantined()
				? quarantines.stream().filter(q -> q.liftedAt() == null).findFirst().orElse(null)
				: null;

		return new TestDetail(row.view(), row.firstSeen(), breakdown, durations, history, signatures, active,
				quarantines);
	}

	/** The test's most recent results (skipped ones included), newest first. */
	public List<TestResult> results(UUID projectId, long testId, int limit) {
		requireTest(projectId, testId);
		return db.sql("""
				select r.run_id, tr.triggered_at, tr.branch, tr.commit_sha, r.outcome, r.attempts, r.failed_attempts,
				       r.duration_ms, left(r.message, 300) as message
				from test_results r join test_runs tr on tr.id = r.run_id
				where r.test_case_id = :id
				order by tr.triggered_at desc, r.run_id desc limit :limit""")
				.param("id", testId)
				.param("limit", limit)
				.query(TestResult.class).list();
	}

	private void requireTest(UUID projectId, long testId) {
		boolean exists = db.sql("select exists(select 1 from test_cases where id = :id and project_id = :project)")
				.param("id", testId).param("project", projectId).query(Boolean.class).single();
		if (!exists) {
			throw ApiException.notFound("Test not found");
		}
	}

}
