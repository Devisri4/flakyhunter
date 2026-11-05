package dev.flakyhunter.catalog;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.flakyhunter.analysis.TestState;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Read side for test cases and failure signatures. */
@Component
public class TestQueries {

	private final JdbcClient db;

	TestQueries(JdbcClient db) {
		this.db = db;
	}

	public record TestCaseView(long id, String suite, String name, String file, String state, BigDecimal score,
			String windowOutcomes, int runsInWindow, int flipCount, int retryPassCount, int failStreak,
			OffsetDateTime lastSeen) {
	}

	public record SignatureView(long id, String exceptionType, String normalizedMessage, String sampleStack,
			int occurrences, int affectedTests, OffsetDateTime firstSeen, OffsetDateTime lastSeen) {
	}

	private static final Map<String, String> SORTS = Map.of(
			"score", "score desc, last_seen desc",
			"name", "lower(suite), lower(name)",
			"lastSeen", "last_seen desc",
			"failStreak", "fail_streak desc, score desc");

	public RunQueries.Page<TestCaseView> list(UUID projectId, TestState state, String search, String sort, int page,
			int size) {
		StringBuilder where = new StringBuilder(" where project_id = :project");
		if (state != null) {
			where.append(" and state = :state");
		}
		boolean searching = search != null && !search.isBlank();
		if (searching) {
			where.append(" and (suite ilike :q or name ilike :q)");
		}
		String orderBy = SORTS.getOrDefault(sort, SORTS.get("score"));
		var query = db.sql("""
				select id, suite, name, file, state, score, window_outcomes, runs_in_window, flip_count,
				       retry_pass_count, fail_streak, last_seen
				from test_cases""" + where + " order by " + orderBy + ", id limit :limit offset :offset")
				.param("project", projectId)
				.param("limit", size)
				.param("offset", (long) page * size);
		var count = db.sql("select count(*) from test_cases" + where).param("project", projectId);
		if (state != null) {
			query = query.param("state", state.name());
			count = count.param("state", state.name());
		}
		if (searching) {
			String like = "%" + search.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
			query = query.param("q", like);
			count = count.param("q", like);
		}
		return new RunQueries.Page<>(query.query(TestCaseView.class).list(), page, size,
				count.query(Long.class).single());
	}

	/** How many tests are in each state (all states present, zero if none). */
	public Map<String, Long> stateCounts(UUID projectId) {
		Map<String, Long> counts = new LinkedHashMap<>();
		for (TestState s : TestState.values()) {
			counts.put(s.name(), 0L);
		}
		db.sql("select state, count(*) as n from test_cases where project_id = :project group by state")
				.param("project", projectId)
				.query(rs -> {
					counts.put(rs.getString("state"), rs.getLong("n"));
				});
		return counts;
	}

	public RunQueries.Page<SignatureView> signatures(UUID projectId, int page, int size) {
		List<SignatureView> content = db.sql("""
				select s.id, s.exception_type, s.normalized_message, s.sample_stack, s.occurrences,
				       (select count(distinct r.test_case_id) from test_results r where r.signature_id = s.id)
				           as affected_tests,
				       s.first_seen, s.last_seen
				from failure_signatures s where s.project_id = :project
				order by s.occurrences desc, s.last_seen desc, s.id limit :limit offset :offset""")
				.param("project", projectId)
				.param("limit", size)
				.param("offset", (long) page * size)
				.query(SignatureView.class).list();
		long total = db.sql("select count(*) from failure_signatures where project_id = :project")
				.param("project", projectId).query(Long.class).single();
		return new RunQueries.Page<>(content, page, size, total);
	}

}
