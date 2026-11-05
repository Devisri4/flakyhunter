package dev.flakyhunter.analysis;

import java.sql.Array;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.flakyhunter.analysis.FlakinessScorer.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The "nightly" jobs. The free server sleeps when idle, so instead of a fixed clock time an hourly check runs
 * each job when it hasn't run for 24 hours (recorded in maintenance_log), e.g. right after the server wakes up.
 * <ul>
 * <li><b>recompute</b>: rebuilds every test's window from test_results — a safety net for out-of-order uploads,
 * and the way new scoring settings are applied to old data.</li>
 * <li><b>retention</b> (ADR-0005): drops messages/stacks after 30 days and results after 180 days.</li>
 * </ul>
 */
@Component
public class MaintenanceJobs {

	private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);
	private static final Duration EVERY = Duration.ofHours(24);

	private final JdbcClient db;
	private final TransactionTemplate tx;
	private final StatsUpdater stats;
	private final FlakinessScorer scorer;

	MaintenanceJobs(JdbcClient db, TransactionTemplate tx, StatsUpdater stats, FlakinessScorer scorer) {
		this.db = db;
		this.tx = tx;
		this.stats = stats;
		this.scorer = scorer;
	}

	@Scheduled(fixedDelayString = "1h", initialDelayString = "${flakyhunter.maintenance.initial-delay:2m}")
	void runDueJobs() {
		if (due("recompute")) {
			recomputeAll();
			markRun("recompute");
		}
		if (due("retention")) {
			applyRetention();
			markRun("retention");
		}
	}

	/** Recomputes all projects; returns how many tests changed state. */
	public int recomputeAll() {
		int changed = 0;
		for (UUID projectId : db.sql("select id from projects").query(UUID.class).list()) {
			changed += recompute(projectId);
		}
		log.info("Recompute finished: {} tests changed state", changed);
		return changed;
	}

	public int recompute(UUID projectId) {
		Integer changed = tx.execute(status -> {
			// same lock as ingestion: never recompute while a run of this project is being written
			db.sql("select 1 from pg_advisory_xact_lock(hashtextextended(cast(:project as text), 0))")
					.param("project", projectId).query(Integer.class).single();
			Map<Long, Window> windows = new HashMap<>();
			db.sql("""
					with ranked as (
					    select r.test_case_id, r.outcome, left(tr.commit_sha, 12) as commit_sha, tr.triggered_at, r.run_id,
					           row_number() over (partition by r.test_case_id
					                              order by tr.triggered_at desc, r.run_id desc) as rn
					    from test_results r join test_runs tr on tr.id = r.run_id
					    where tr.project_id = :project and r.outcome <> 'SKIPPED')
					select test_case_id,
					       string_agg(case outcome when 'PASSED' then 'P' when 'FAILED' then 'F' else 'R' end, ''
					                  order by triggered_at, run_id) as outcomes,
					       array_agg(coalesce(commit_sha, '') order by triggered_at, run_id) as commits
					from ranked where rn <= :window
					group by test_case_id""")
					.param("project", projectId)
					.param("window", scorer.windowSize())
					.query(rs -> {
						Array array = rs.getArray("commits");
						List<String> commits = new ArrayList<>();
						for (String c : (String[]) array.getArray()) {
							commits.add(c == null || c.isEmpty() ? null : c);
						}
						windows.put(rs.getLong("test_case_id"), new Window(rs.getString("outcomes"), commits));
					});
			return stats.replace(windows);
		});
		return changed == null ? 0 : changed;
	}

	public void applyRetention() {
		int cleared = db.sql("""
				update test_results set message = null, stack = null
				where (message is not null or stack is not null)
				  and run_id in (select id from test_runs where triggered_at < now() - interval '30 days')""").update();
		int runs = db.sql("delete from test_runs where triggered_at < now() - interval '180 days'").update();
		int jobs = db.sql("delete from ingestion_jobs where finished_at < now() - interval '30 days'").update();
		log.info("Retention: cleared {} messages, deleted {} old runs and {} old upload jobs", cleared, runs, jobs);
	}

	private boolean due(String job) {
		return db.sql("select last_run_at from maintenance_log where name = :name")
				.param("name", job)
				.query(java.time.OffsetDateTime.class).optional()
				.map(last -> last.isBefore(java.time.OffsetDateTime.now().minus(EVERY)))
				.orElse(true);
	}

	private void markRun(String job) {
		db.sql("""
				insert into maintenance_log (name, last_run_at) values (:name, now())
				on conflict (name) do update set last_run_at = excluded.last_run_at""")
				.param("name", job).update();
	}

}
