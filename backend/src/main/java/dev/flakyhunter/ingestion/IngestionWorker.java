package dev.flakyhunter.ingestion;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.flakyhunter.catalog.RunMetadata;
import dev.flakyhunter.catalog.RunWriter;
import dev.flakyhunter.parsing.JUnitXmlParser;
import dev.flakyhunter.parsing.ParsedTest;
import dev.flakyhunter.parsing.ReportParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Background worker: claims QUEUED jobs with {@code FOR UPDATE SKIP LOCKED} (several app instances could
 * run side by side without taking the same job), then processes each in its own transaction while holding
 * a per-project advisory lock, so two runs of one project never update the same rows at the same time.
 */
@Component
public class IngestionWorker {

	private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);

	private final JdbcClient db;
	private final TransactionTemplate tx;
	private final JUnitXmlParser parser;
	private final RunWriter runWriter;
	private final IngestionProperties props;

	IngestionWorker(JdbcClient db, TransactionTemplate tx, JUnitXmlParser parser, RunWriter runWriter,
			IngestionProperties props) {
		this.db = db;
		this.tx = tx;
		this.parser = parser;
		this.runWriter = runWriter;
		this.props = props;
	}

	record Claimed(UUID id, UUID projectId, int attempts, String commitSha, String branch, String ciProvider,
			String buildUrl, OffsetDateTime triggeredAt) {
	}

	@Scheduled(fixedDelayString = "${flakyhunter.ingestion.poll-interval:2s}")
	void poll() {
		if (props.workerEnabled()) {
			processAvailable();
		}
	}

	/** Claims and processes one batch; returns how many jobs were claimed. */
	public int processAvailable() {
		List<Claimed> claimed = tx.execute(status -> db.sql("""
				update ingestion_jobs set status = 'PROCESSING', locked_at = now(), attempts = attempts + 1
				where id in (select id from ingestion_jobs where status = 'QUEUED'
				             order by created_at for update skip locked limit :limit)
				returning id, project_id, attempts, commit_sha, branch, ci_provider, build_url, triggered_at""")
				.param("limit", props.batchSize())
				.query(Claimed.class).list());
		if (claimed == null || claimed.isEmpty()) {
			return 0;
		}
		try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
			for (Claimed job : claimed) {
				pool.submit(() -> process(job));
			}
		}
		return claimed.size();
	}

	void process(Claimed job) {
		try {
			tx.executeWithoutResult(status -> {
				db.sql("select 1 from pg_advisory_xact_lock(hashtextextended(cast(:project as text), 0))")
						.param("project", job.projectId()).query(Integer.class).single();
				List<ParsedTest> tests = parser.parse(loadFiles(job.id()));
				if (tests.isEmpty()) {
					throw new ReportParseException("The report contains no test cases");
				}
				RunMetadata meta = new RunMetadata(job.commitSha(), job.branch(), job.ciProvider(), job.buildUrl(),
						job.triggeredAt());
				long runId = runWriter.write(job.projectId(), meta, tests);
				finish(job.id(), "DONE", null, runId);
			});
			log.info("Ingested job {} for project {}", job.id(), job.projectId());
		}
		catch (ReportParseException e) {
			// a broken report stays broken: fail at once with a message the user can act on
			tx.executeWithoutResult(status -> finish(job.id(), "FAILED", e.getMessage(), null));
			log.info("Job {} rejected: {}", job.id(), e.getMessage());
		}
		catch (RuntimeException e) {
			log.warn("Job {} failed (attempt {})", job.id(), job.attempts(), e);
			tx.executeWithoutResult(status -> {
				if (job.attempts() >= props.maxAttempts()) {
					finish(job.id(), "FAILED", "Processing failed after " + job.attempts() + " attempts", null);
				}
				else {
					db.sql("update ingestion_jobs set status = 'QUEUED', locked_at = null, error = :error where id = :id")
							.param("error", "Attempt " + job.attempts() + " failed, will retry")
							.param("id", job.id())
							.update();
				}
			});
		}
	}

	/** Jobs whose worker died (e.g. the server restarted mid-job) go back to the queue, or fail after 3 tries. */
	@Scheduled(fixedDelayString = "60s", initialDelayString = "30s")
	public int recoverStuckJobs() {
		return db.sql("""
				update ingestion_jobs
				set status = case when attempts >= :max then 'FAILED' else 'QUEUED' end,
				    error = case when attempts >= :max then 'Processing timed out' else error end,
				    finished_at = case when attempts >= :max then now() else null end,
				    locked_at = null
				where status = 'PROCESSING' and locked_at < now() - make_interval(secs => :stuckSeconds)""")
				.param("max", props.maxAttempts())
				.param("stuckSeconds", props.stuckAfter().toSeconds())
				.update();
	}

	private List<InputStream> loadFiles(UUID jobId) {
		List<byte[]> compressed = db.sql("select content from ingestion_job_files where job_id = :id order by seq")
				.param("id", jobId)
				.query((rs, n) -> rs.getBytes(1)).list();
		long budget = props.maxUncompressedBytes();
		List<InputStream> files = new ArrayList<>();
		for (byte[] gz : compressed) {
			byte[] plain = Gzip.decompress(gz, budget);
			budget -= plain.length;
			files.add(new ByteArrayInputStream(plain));
		}
		return files;
	}

	private void finish(UUID jobId, String status, String error, Long runId) {
		db.sql("""
				update ingestion_jobs set status = :status, error = :error, run_id = :run, finished_at = now(),
				       locked_at = null
				where id = :id""")
				.param("status", status)
				.param("error", error)
				.param("run", runId)
				.param("id", jobId)
				.update();
		// the report is not needed any more (ADR-0005)
		db.sql("delete from ingestion_job_files where job_id = :id").param("id", jobId).update();
	}

}
