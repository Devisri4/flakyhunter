package dev.flakyhunter.analysis;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.flakyhunter.analysis.FlakinessScorer.Window;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Applies new results to the per-test stats columns: appends to each window, re-scores, and logs a
 * state change only when the verdict changes. Runs inside the ingestion transaction (which already holds
 * the per-project advisory lock), so concurrent runs of one project can't race on these rows.
 */
@Component
public class StatsUpdater {

	/** One executed result: P, F or R (skipped results don't enter the window). */
	public record Observation(long testCaseId, char outcome) {
	}

	private record Current(long id, String window, String[] commits, String state) {
	}

	private final JdbcTemplate jdbc;
	private final FlakinessScorer scorer;

	StatsUpdater(JdbcTemplate jdbc, FlakinessScorer scorer) {
		this.jdbc = jdbc;
		this.scorer = scorer;
	}

	public void apply(Long runId, String commit, OffsetDateTime runTime, List<Observation> observations) {
		if (observations.isEmpty()) {
			return;
		}
		Map<Long, Current> current = load(observations.stream().map(Observation::testCaseId).toArray(Long[]::new));
		List<Update> updates = new ArrayList<>(observations.size());
		for (Observation o : observations) {
			Current c = current.get(o.testCaseId());
			Window window = windowOf(c).append(o.outcome(), commit, scorer.windowSize());
			updates.add(new Update(o.testCaseId(), window, scorer.score(window), c.state()));
		}
		save(updates, runId, runTime);
	}

	/** Recomputes from scratch (nightly safety net): the caller provides each test's rebuilt window. */
	public int replace(Map<Long, Window> windows) {
		if (windows.isEmpty()) {
			return 0;
		}
		Map<Long, Current> current = load(windows.keySet().toArray(Long[]::new));
		List<Update> updates = new ArrayList<>(windows.size());
		windows.forEach((id, window) -> updates.add(new Update(id, window, scorer.score(window), current.get(id).state())));
		return save(updates, null, null);
	}

	record Update(long id, Window window, FlakinessScorer.Stats stats, String previousState) {

		boolean stateChanged() {
			return !stats.state().name().equals(previousState);
		}

	}

	private Map<Long, Current> load(Long[] ids) {
		Map<Long, Current> rows = new HashMap<>();
		jdbc.query(con -> {
			PreparedStatement ps = con.prepareStatement(
					"select id, window_outcomes, window_commits, state from test_cases where id = any(?) for update");
			ps.setArray(1, con.createArrayOf("bigint", ids));
			return ps;
		}, rs -> {
			Array commits = rs.getArray("window_commits");
			rows.put(rs.getLong("id"), new Current(rs.getLong("id"), rs.getString("window_outcomes"),
					commits == null ? new String[0] : (String[]) commits.getArray(), rs.getString("state")));
		});
		return rows;
	}

	/** Writes all updates with one statement, plus state_changes rows for changed verdicts. Returns that count. */
	private int save(List<Update> updates, Long runId, OffsetDateTime changedAt) {
		int n = updates.size();
		Long[] ids = new Long[n];
		String[] windows = new String[n];
		String[] commits = new String[n];
		Integer[] runs = new Integer[n];
		Integer[] flips = new Integer[n];
		Integer[] retries = new Integer[n];
		Integer[] streaks = new Integer[n];
		BigDecimal[] scores = new BigDecimal[n];
		String[] states = new String[n];
		for (int i = 0; i < n; i++) {
			Update u = updates.get(i);
			ids[i] = u.id();
			windows[i] = u.window().outcomes();
			// commits travel as one comma-separated string per row (Postgres can't unnest a 2-D array per row)
			commits[i] = String.join(",", u.window().commits().stream().map(c -> c == null ? "" : c.replace(",", "")).toList());
			runs[i] = u.stats().runsInWindow();
			flips[i] = u.stats().flipCount();
			retries[i] = u.stats().retryPassCount();
			streaks[i] = u.stats().failStreak();
			scores[i] = u.stats().score();
			states[i] = u.stats().state().name();
		}
		jdbc.update(con -> {
			PreparedStatement ps = con.prepareStatement("""
					update test_cases t set
					    window_outcomes = u.w,
					    window_commits = case when u.c = '' and u.runs <= 1 then array[u.c] else string_to_array(u.c, ',') end,
					    runs_in_window = u.runs, flip_count = u.flips, retry_pass_count = u.retries,
					    fail_streak = u.streak, score = u.score, state = u.state, stats_updated_at = now()
					from unnest(?::bigint[], ?::text[], ?::text[], ?::int[], ?::int[], ?::int[], ?::int[], ?::numeric[], ?::text[])
					     as u(id, w, c, runs, flips, retries, streak, score, state)
					where t.id = u.id""");
			ps.setArray(1, con.createArrayOf("bigint", ids));
			ps.setArray(2, con.createArrayOf("text", windows));
			ps.setArray(3, con.createArrayOf("text", commits));
			ps.setArray(4, con.createArrayOf("int", runs));
			ps.setArray(5, con.createArrayOf("int", flips));
			ps.setArray(6, con.createArrayOf("int", retries));
			ps.setArray(7, con.createArrayOf("int", streaks));
			ps.setArray(8, con.createArrayOf("numeric", scores));
			ps.setArray(9, con.createArrayOf("text", states));
			return ps;
		});

		List<Update> changed = updates.stream().filter(Update::stateChanged).toList();
		if (!changed.isEmpty()) {
			jdbc.update(con -> {
				// dated with the CI run's time (not the upload time), so "newly flaky this week" means the runs
				PreparedStatement ps = con.prepareStatement("""
						insert into state_changes (test_case_id, from_state, to_state, score, run_id, changed_at)
						select u.id, u.from_state, u.to_state, u.score, ?, coalesce(?, now())
						from unnest(?::bigint[], ?::text[], ?::text[], ?::numeric[]) as u(id, from_state, to_state, score)""");
				if (runId == null) {
					ps.setNull(1, java.sql.Types.BIGINT);
				}
				else {
					ps.setLong(1, runId);
				}
				ps.setObject(2, changedAt, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
				ps.setArray(3, con.createArrayOf("bigint", changed.stream().map(Update::id).toArray(Long[]::new)));
				ps.setArray(4, con.createArrayOf("text", changed.stream().map(Update::previousState).toArray(String[]::new)));
				ps.setArray(5, con.createArrayOf("text", changed.stream().map(u -> u.stats().state().name()).toArray(String[]::new)));
				ps.setArray(6, con.createArrayOf("numeric", changed.stream().map(u -> u.stats().score()).toArray(BigDecimal[]::new)));
				return ps;
			});
		}
		return changed.size();
	}

	/** Rebuilds the stored window; commits are padded or cut to the outcomes' length if they ever disagree. */
	private static Window windowOf(Current c) {
		List<String> commits = new ArrayList<>(c.window().length());
		for (int i = 0; i < c.window().length(); i++) {
			String commit = i < c.commits().length ? c.commits()[i] : null;
			commits.add(commit == null || commit.isEmpty() ? null : commit);
		}
		return new Window(c.window(), commits);
	}

}
