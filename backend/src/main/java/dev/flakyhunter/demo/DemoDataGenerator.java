package dev.flakyhunter.demo;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import dev.flakyhunter.catalog.RunMetadata;
import dev.flakyhunter.catalog.RunWriter;
import dev.flakyhunter.project.DemoProjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps the public demo project filled with a rolling window of realistic CI runs: on startup it fills
 * whatever is missing (60 days the first time), then every hour it adds the runs due since the last one
 * and drops runs older than the window. Runs go through the same {@link RunWriter} as real uploads, so the
 * demo shows exactly what the engine computes.
 */
@Component
public class DemoDataGenerator {

	private static final Logger log = LoggerFactory.getLogger(DemoDataGenerator.class);

	private final DemoProperties props;
	private final DemoProjects demoProjects;
	private final RunWriter runWriter;
	private final JdbcClient db;
	private final TransactionTemplate tx;

	DemoDataGenerator(DemoProperties props, DemoProjects demoProjects, RunWriter runWriter, JdbcClient db,
			TransactionTemplate tx) {
		this.props = props;
		this.demoProjects = demoProjects;
		this.runWriter = runWriter;
		this.db = db;
		this.tx = tx;
	}

	@EventListener(ApplicationReadyEvent.class)
	void onStartup() {
		if (props.enabled()) {
			// in the background: the app answers health checks while the first 60 days are generated
			Thread.ofVirtual().name("demo-data").start(this::topUpSafely);
		}
	}

	@Scheduled(fixedDelayString = "1h", initialDelayString = "1h")
	void hourly() {
		if (props.enabled()) {
			topUpSafely();
		}
	}

	private void topUpSafely() {
		try {
			topUp(props.slug(), props.days(), props.tests(), props.runsPerDay(), Instant.now());
		}
		catch (RuntimeException e) {
			log.warn("Demo data generation failed", e);
		}
	}

	/** Adds the runs that are due up to {@code now}; returns how many runs were added. */
	public synchronized int topUp(String slug, int days, int testCount, int runsPerDay, Instant now) {
		UUID projectId = demoProjects.findOrCreate(slug, "Acme Shop (demo)");
		if (projectId == null) {
			log.warn("Slug '{}' belongs to a normal project; the demo is not generated", slug);
			return 0;
		}
		long slotSeconds = Duration.ofDays(1).toSeconds() / runsPerDay;
		long nowSlot = now.getEpochSecond() / slotSeconds;
		long oldestSlot = nowSlot - (long) days * runsPerDay + 1;

		OffsetDateTime last = db.sql("select max(triggered_at) from test_runs where project_id = :p")
				.param("p", projectId).query(OffsetDateTime.class).single();
		OffsetDateTime first = db.sql("select min(triggered_at) from test_runs where project_id = :p")
				.param("p", projectId).query(OffsetDateTime.class).single();
		long fromSlot = last == null ? oldestSlot : Math.max(oldestSlot, last.toEpochSecond() / slotSeconds + 1);
		long firstSlot = first == null ? fromSlot : first.toEpochSecond() / slotSeconds;
		// NEW tests show up in the runs just before the demo was first created
		long newSince = Math.max(firstSlot, firstSlot + (long) days * runsPerDay - 10);

		DemoCatalog catalog = new DemoCatalog(testCount);
		int added = 0;
		for (long slot = fromSlot; slot <= nowSlot; slot++) {
			long runSlot = slot;
			// a few minutes of deterministic jitter so runs don't all start on the hour
			Instant at = Instant.ofEpochSecond(runSlot * slotSeconds + Math.floorMod(runSlot * 7919, 1800));
			RunMetadata meta = new RunMetadata(DemoCatalog.commitFor(runSlot), DemoCatalog.branchFor(runSlot), "github",
					"https://github.com/acme/shop/actions/runs/" + runSlot, OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
			tx.executeWithoutResult(status -> {
				// the same per-project lock as real uploads and the recompute job
				db.sql("select 1 from pg_advisory_xact_lock(hashtextextended(cast(:project as text), 0))")
						.param("project", projectId).query(Integer.class).single();
				runWriter.write(projectId, meta, catalog.run(runSlot, newSince));
			});
			added++;
		}

		OffsetDateTime cutoff = OffsetDateTime.ofInstant(now.minus(Duration.ofDays(days)), ZoneOffset.UTC);
		int removed = db.sql("delete from test_runs where project_id = :p and triggered_at < :cutoff")
				.param("p", projectId).param("cutoff", cutoff).update();
		db.sql("delete from daily_stats where project_id = :p and day < cast(:cutoff as date)")
				.param("p", projectId).param("cutoff", cutoff).update();
		if (added > 0 || removed > 0) {
			log.info("Demo '{}': added {} runs, removed {} old runs", slug, added, removed);
		}
		return added;
	}

}
