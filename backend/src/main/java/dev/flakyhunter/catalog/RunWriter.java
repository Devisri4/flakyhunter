package dev.flakyhunter.catalog;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.flakyhunter.parsing.ParsedTest;
import dev.flakyhunter.parsing.ParsedTest.Outcome;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes one parsed run: the run row, upserted test cases and one result per test.
 * Uses {@code unnest(array...)} so 1000 rows cost one round trip instead of 1000.
 * Must be called inside the caller's transaction.
 */
@Component
public class RunWriter {

	private static final int CHUNK = 1000;

	private final JdbcTemplate jdbc;

	RunWriter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public long write(UUID projectId, RunMetadata meta, List<ParsedTest> tests) {
		long runId = insertRun(projectId, meta, tests);
		for (int from = 0; from < tests.size(); from += CHUNK) {
			List<ParsedTest> chunk = tests.subList(from, Math.min(tests.size(), from + CHUNK));
			Map<String, Long> caseIds = upsertCases(projectId, meta, chunk);
			insertResults(runId, chunk, caseIds);
		}
		return runId;
	}

	private long insertRun(UUID projectId, RunMetadata meta, List<ParsedTest> tests) {
		int passed = 0, failed = 0, skipped = 0, flaky = 0;
		long duration = 0;
		for (ParsedTest t : tests) {
			duration += t.durationMs();
			switch (t.outcome()) {
				case PASSED -> passed++;
				case FAILED -> failed++;
				case SKIPPED -> skipped++;
				case PASSED_ON_RETRY -> flaky++;
			}
		}
		Long id = jdbc.queryForObject("""
				insert into test_runs (project_id, commit_sha, branch, ci_provider, build_url, triggered_at,
				                       duration_ms, total, passed, failed, skipped, flaky)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				returning id""", Long.class,
				projectId, meta.commitSha(), meta.branch(), meta.ciProvider(), meta.buildUrl(), meta.triggeredAt(),
				duration, tests.size(), passed, failed, skipped, flaky);
		return id;
	}

	private Map<String, Long> upsertCases(UUID projectId, RunMetadata meta, List<ParsedTest> chunk) {
		String[] fingerprints = new String[chunk.size()];
		String[] suites = new String[chunk.size()];
		String[] names = new String[chunk.size()];
		String[] files = new String[chunk.size()];
		for (int i = 0; i < chunk.size(); i++) {
			ParsedTest t = chunk.get(i);
			fingerprints[i] = Fingerprints.of(t.suite(), t.name());
			suites[i] = t.suite();
			names[i] = t.name();
			files[i] = t.file();
		}
		Map<String, Long> ids = new HashMap<>();
		jdbc.query(con -> {
			PreparedStatement ps = con.prepareStatement("""
					insert into test_cases (project_id, fingerprint, suite, name, file, first_seen, last_seen)
					select ?, t.fp, t.suite, t.name, t.file, ?, ?
					from unnest(?::text[], ?::text[], ?::text[], ?::text[]) as t(fp, suite, name, file)
					on conflict (project_id, fingerprint) do update
					   set last_seen = greatest(test_cases.last_seen, excluded.last_seen),
					       file = coalesce(excluded.file, test_cases.file)
					returning id, fingerprint""");
			ps.setObject(1, projectId);
			ps.setObject(2, meta.triggeredAt());
			ps.setObject(3, meta.triggeredAt());
			ps.setArray(4, array(con, "text", fingerprints));
			ps.setArray(5, array(con, "text", suites));
			ps.setArray(6, array(con, "text", names));
			ps.setArray(7, array(con, "text", files));
			return ps;
		}, rs -> {
			ids.put(rs.getString("fingerprint"), rs.getLong("id"));
		});
		return ids;
	}

	private void insertResults(long runId, List<ParsedTest> chunk, Map<String, Long> caseIds) {
		int n = chunk.size();
		Long[] testCaseIds = new Long[n];
		String[] outcomes = new String[n];
		Integer[] attempts = new Integer[n];
		Integer[] failedAttempts = new Integer[n];
		Long[] durations = new Long[n];
		String[] types = new String[n];
		String[] messages = new String[n];
		String[] stacks = new String[n];
		for (int i = 0; i < n; i++) {
			ParsedTest t = chunk.get(i);
			testCaseIds[i] = caseIds.get(Fingerprints.of(t.suite(), t.name()));
			outcomes[i] = t.outcome().name();
			attempts[i] = Math.min(t.attempts(), Short.MAX_VALUE);
			failedAttempts[i] = Math.min(t.failedAttempts(), Short.MAX_VALUE);
			durations[i] = t.durationMs();
			boolean keepFailure = t.outcome() == Outcome.FAILED || t.outcome() == Outcome.PASSED_ON_RETRY;
			types[i] = keepFailure ? t.failureType() : null;
			messages[i] = keepFailure ? t.message() : null;
			stacks[i] = keepFailure ? t.stack() : null;
		}
		jdbc.update(con -> {
			PreparedStatement ps = con.prepareStatement("""
					insert into test_results (run_id, test_case_id, outcome, attempts, failed_attempts, duration_ms,
					                          failure_type, message, stack)
					select ?, r.case_id, r.outcome, r.attempts, r.failed, r.duration, r.type, r.message, r.stack
					from unnest(?::bigint[], ?::text[], ?::int[], ?::int[], ?::bigint[], ?::text[], ?::text[], ?::text[])
					     as r(case_id, outcome, attempts, failed, duration, type, message, stack)""");
			ps.setLong(1, runId);
			ps.setArray(2, array(con, "bigint", testCaseIds));
			ps.setArray(3, array(con, "text", outcomes));
			ps.setArray(4, array(con, "int", attempts));
			ps.setArray(5, array(con, "int", failedAttempts));
			ps.setArray(6, array(con, "bigint", durations));
			ps.setArray(7, array(con, "text", types));
			ps.setArray(8, array(con, "text", messages));
			ps.setArray(9, array(con, "text", stacks));
			return ps;
		});
	}

	private static Array array(Connection con, String type, Object[] values) throws SQLException {
		return con.createArrayOf(type, values);
	}

}
