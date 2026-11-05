package dev.flakyhunter.catalog;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.flakyhunter.analysis.FlakinessScorer;
import dev.flakyhunter.analysis.SignatureNormalizer;
import dev.flakyhunter.analysis.SignatureWriter;
import dev.flakyhunter.analysis.StatsUpdater;
import dev.flakyhunter.parsing.ParsedTest;
import dev.flakyhunter.parsing.ParsedTest.Outcome;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes one parsed run: the run row, upserted test cases, failure signatures, one result per test, the
 * per-test flakiness stats and the day's totals. Uses {@code unnest(array...)} so 1000 rows cost one round
 * trip instead of 1000. Must be called inside the caller's transaction.
 */
@Component
public class RunWriter {

	private static final int CHUNK = 1000;
	private static final int COMMIT_LENGTH = 12;

	private final JdbcTemplate jdbc;
	private final SignatureNormalizer normalizer;
	private final SignatureWriter signatures;
	private final StatsUpdater stats;

	RunWriter(JdbcTemplate jdbc, SignatureNormalizer normalizer, SignatureWriter signatures, StatsUpdater stats) {
		this.jdbc = jdbc;
		this.normalizer = normalizer;
		this.signatures = signatures;
		this.stats = stats;
	}

	/** The new run's id and the tests that became FLAKY with it (for alerts). */
	public record Written(long runId, List<Long> newlyFlaky) {
	}

	public Written write(UUID projectId, RunMetadata meta, List<ParsedTest> tests) {
		long runId = insertRun(projectId, meta, tests);
		String commit = meta.commitSha() == null ? null
				: meta.commitSha().substring(0, Math.min(COMMIT_LENGTH, meta.commitSha().length()));
		List<Long> newlyFlaky = new ArrayList<>();
		for (int from = 0; from < tests.size(); from += CHUNK) {
			List<ParsedTest> chunk = tests.subList(from, Math.min(tests.size(), from + CHUNK));
			Map<String, Long> caseIds = upsertCases(projectId, meta, chunk);
			Map<Integer, Long> signatureIds = upsertSignatures(projectId, meta, chunk);
			insertResults(runId, chunk, caseIds, signatureIds);
			newlyFlaky.addAll(stats.apply(runId, commit, meta.triggeredAt(), observations(chunk, caseIds)));
		}
		return new Written(runId, newlyFlaky);
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
		jdbc.update("""
				insert into daily_stats (project_id, day, runs, tests_executed, passed, failed, flaky, total_duration_ms)
				values (?, (cast(? as timestamptz) at time zone 'UTC')::date, 1, ?, ?, ?, ?, ?)
				on conflict (project_id, day) do update set
				    runs = daily_stats.runs + 1,
				    tests_executed = daily_stats.tests_executed + excluded.tests_executed,
				    passed = daily_stats.passed + excluded.passed,
				    failed = daily_stats.failed + excluded.failed,
				    flaky = daily_stats.flaky + excluded.flaky,
				    total_duration_ms = daily_stats.total_duration_ms + excluded.total_duration_ms""",
				projectId, meta.triggeredAt(), passed + failed + flaky, passed, failed, flaky, duration);
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

	/** Signature id per index in the chunk, for failed and passed-on-retry results. */
	private Map<Integer, Long> upsertSignatures(UUID projectId, RunMetadata meta, List<ParsedTest> chunk) {
		List<SignatureWriter.Failure> failures = new ArrayList<>();
		Map<Integer, String> hashByIndex = new HashMap<>();
		for (int i = 0; i < chunk.size(); i++) {
			ParsedTest t = chunk.get(i);
			if (hasFailure(t) && (t.message() != null || t.stack() != null || t.failureType() != null)) {
				var signature = normalizer.normalize(t.failureType(), t.message(), t.stack());
				failures.add(new SignatureWriter.Failure(signature, t.stack()));
				hashByIndex.put(i, signature.hash());
			}
		}
		Map<String, Long> idByHash = signatures.upsert(projectId, meta.triggeredAt(), failures);
		Map<Integer, Long> result = new HashMap<>();
		hashByIndex.forEach((index, hash) -> result.put(index, idByHash.get(hash)));
		return result;
	}

	private void insertResults(long runId, List<ParsedTest> chunk, Map<String, Long> caseIds,
			Map<Integer, Long> signatureIds) {
		int n = chunk.size();
		Long[] testCaseIds = new Long[n];
		String[] outcomes = new String[n];
		Integer[] attempts = new Integer[n];
		Integer[] failedAttempts = new Integer[n];
		Long[] durations = new Long[n];
		String[] types = new String[n];
		String[] messages = new String[n];
		String[] stacks = new String[n];
		Long[] signatureColumn = new Long[n];
		for (int i = 0; i < n; i++) {
			ParsedTest t = chunk.get(i);
			testCaseIds[i] = caseIds.get(Fingerprints.of(t.suite(), t.name()));
			outcomes[i] = t.outcome().name();
			attempts[i] = Math.min(t.attempts(), Short.MAX_VALUE);
			failedAttempts[i] = Math.min(t.failedAttempts(), Short.MAX_VALUE);
			durations[i] = t.durationMs();
			boolean keepFailure = hasFailure(t);
			types[i] = keepFailure ? t.failureType() : null;
			messages[i] = keepFailure ? t.message() : null;
			stacks[i] = keepFailure ? t.stack() : null;
			signatureColumn[i] = signatureIds.get(i);
		}
		jdbc.update(con -> {
			PreparedStatement ps = con.prepareStatement("""
					insert into test_results (run_id, test_case_id, outcome, attempts, failed_attempts, duration_ms,
					                          failure_type, message, stack, signature_id)
					select ?, r.case_id, r.outcome, r.attempts, r.failed, r.duration, r.type, r.message, r.stack, r.signature
					from unnest(?::bigint[], ?::text[], ?::int[], ?::int[], ?::bigint[], ?::text[], ?::text[], ?::text[],
					            ?::bigint[])
					     as r(case_id, outcome, attempts, failed, duration, type, message, stack, signature)""");
			ps.setLong(1, runId);
			ps.setArray(2, array(con, "bigint", testCaseIds));
			ps.setArray(3, array(con, "text", outcomes));
			ps.setArray(4, array(con, "int", attempts));
			ps.setArray(5, array(con, "int", failedAttempts));
			ps.setArray(6, array(con, "bigint", durations));
			ps.setArray(7, array(con, "text", types));
			ps.setArray(8, array(con, "text", messages));
			ps.setArray(9, array(con, "text", stacks));
			ps.setArray(10, array(con, "bigint", signatureColumn));
			return ps;
		});
	}

	/** Skipped tests don't enter a test's window: "skips ignored" (ADR-0004). */
	private static List<StatsUpdater.Observation> observations(List<ParsedTest> chunk, Map<String, Long> caseIds) {
		List<StatsUpdater.Observation> list = new ArrayList<>(chunk.size());
		for (ParsedTest t : chunk) {
			char outcome = switch (t.outcome()) {
				case PASSED -> FlakinessScorer.PASS;
				case FAILED -> FlakinessScorer.FAIL;
				case PASSED_ON_RETRY -> FlakinessScorer.RETRY_PASS;
				case SKIPPED -> 0;
			};
			if (outcome != 0) {
				list.add(new StatsUpdater.Observation(caseIds.get(Fingerprints.of(t.suite(), t.name())), outcome));
			}
		}
		return list;
	}

	private static boolean hasFailure(ParsedTest t) {
		return t.outcome() == Outcome.FAILED || t.outcome() == Outcome.PASSED_ON_RETRY;
	}

	private static Array array(Connection con, String type, Object[] values) throws SQLException {
		return con.createArrayOf(type, values);
	}

}
