package dev.flakyhunter.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Dashboard numbers. Everything over time comes from {@code daily_stats} (one row per day), so the cost does
 * not grow with the number of results (ADR-0005). Days are UTC dates.
 */
@Component
public class DashboardQueries {

	private final JdbcClient db;

	DashboardQueries(JdbcClient db) {
		this.db = db;
	}

	/**
	 * KPIs for the last {@code days} days, with the same numbers for the {@code days} before that so the UI
	 * can show a change. Pass rate counts passed-after-retry as passed and leaves skipped tests out.
	 */
	public record Summary(int days, long runs, double runsPerDay, Double passRate, Double previousPassRate,
			Long avgRunDurationMs, Long previousAvgRunDurationMs, long failedResults, long flakyResults,
			long totalTests, long flakyTests, long brokenTests, long suspectTests, long newlyFlakyTests) {
	}

	public record TrendPoint(LocalDate day, int runs, long executed, long passed, long failed, long flaky,
			Double passRate, Long avgRunDurationMs) {
	}

	public record NewlyFlaky(long id, String suite, String name, BigDecimal score, String fromState,
			OffsetDateTime changedAt) {
	}

	private record Period(long runs, long executed, long passed, long failed, long flaky, long durationMs) {

		Double passRate() {
			return executed == 0 ? null : Math.round((passed + flaky) * 1000.0 / executed) / 10.0;
		}

		Long avgDurationMs() {
			return runs == 0 ? null : durationMs / runs;
		}

	}

	private record StateCounts(long total, long flaky, long broken, long suspect) {
	}

	public Summary summary(UUID projectId, int days) {
		Period current = period(projectId, days, 0);
		Period previous = period(projectId, days, days);
		StateCounts states = db.sql("""
				select count(*) as total,
				       count(*) filter (where state = 'FLAKY') as flaky,
				       count(*) filter (where state = 'BROKEN') as broken,
				       count(*) filter (where state = 'SUSPECT') as suspect
				from test_cases where project_id = :project""")
				.param("project", projectId).query(StateCounts.class).single();
		long newlyFlaky = db.sql("""
				select count(distinct sc.test_case_id)
				from state_changes sc join test_cases c on c.id = sc.test_case_id
				where c.project_id = :project and c.state = 'FLAKY' and sc.to_state = 'FLAKY'
				  and sc.changed_at > now() - interval '7 days'""")
				.param("project", projectId).query(Long.class).single();
		return new Summary(days, current.runs(), Math.round(current.runs() * 10.0 / days) / 10.0, current.passRate(),
				previous.passRate(), current.avgDurationMs(), previous.avgDurationMs(), current.failed(), current.flaky(),
				states.total(), states.flaky(), states.broken(), states.suspect(), newlyFlaky);
	}

	/** One point per day for the last {@code days} days, including days without runs (all zero). */
	public List<TrendPoint> trends(UUID projectId, int days) {
		return db.sql("""
				select d::date as day,
				       coalesce(s.runs, 0) as runs,
				       coalesce(s.tests_executed, 0) as executed,
				       coalesce(s.passed, 0) as passed,
				       coalesce(s.failed, 0) as failed,
				       coalesce(s.flaky, 0) as flaky,
				       case when coalesce(s.tests_executed, 0) = 0 then null
				            else round((s.passed + s.flaky) * 100.0 / s.tests_executed, 1)::float8 end as pass_rate,
				       case when coalesce(s.runs, 0) = 0 then null else s.total_duration_ms / s.runs end
				           as avg_run_duration_ms
				from generate_series((now() at time zone 'UTC')::date - (:days - 1),
				                     (now() at time zone 'UTC')::date, interval '1 day') as d
				left join daily_stats s on s.project_id = :project and s.day = d::date
				order by d""")
				.param("project", projectId)
				.param("days", days)
				.query(TrendPoint.class).list();
	}

	/** Tests that are FLAKY now and became flaky within the last {@code days} days, newest first. */
	public List<NewlyFlaky> newlyFlaky(UUID projectId, int days, int limit) {
		return db.sql("""
				select * from (
				    select distinct on (c.id) c.id, c.suite, c.name, c.score, sc.from_state, sc.changed_at
				    from state_changes sc join test_cases c on c.id = sc.test_case_id
				    where c.project_id = :project and c.state = 'FLAKY' and sc.to_state = 'FLAKY'
				      and sc.changed_at > now() - make_interval(days => :days)
				    order by c.id, sc.changed_at desc) latest
				order by changed_at desc limit :limit""")
				.param("project", projectId)
				.param("days", days)
				.param("limit", limit)
				.query(NewlyFlaky.class).list();
	}

	private Period period(UUID projectId, int days, int offsetDays) {
		return db.sql("""
				select coalesce(sum(runs), 0) as runs, coalesce(sum(tests_executed), 0) as executed,
				       coalesce(sum(passed), 0) as passed, coalesce(sum(failed), 0) as failed,
				       coalesce(sum(flaky), 0) as flaky, coalesce(sum(total_duration_ms), 0) as duration_ms
				from daily_stats
				where project_id = :project
				  and day >  (now() at time zone 'UTC')::date - (:days + :offset)
				  and day <= (now() at time zone 'UTC')::date - :offset""")
				.param("project", projectId)
				.param("days", days)
				.param("offset", offsetDays)
				.query(Period.class).single();
	}

}
