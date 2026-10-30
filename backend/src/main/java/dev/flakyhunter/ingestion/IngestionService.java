package dev.flakyhunter.ingestion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dev.flakyhunter.common.ApiException;
import dev.flakyhunter.project.ApiKeyPrincipal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Accepts uploads: stores them as a QUEUED job and returns at once (the worker does the rest). */
@Service
public class IngestionService {

	private final JdbcClient db;
	private final IngestionProperties props;

	IngestionService(JdbcClient db, IngestionProperties props) {
		this.db = db;
		this.props = props;
	}

	public record UploadMetadata(String format, String commitSha, String branch, String ciProvider, String buildUrl,
			String runId, String runAttempt, String idempotencyKey, OffsetDateTime triggeredAt) {
	}

	public record Accepted(UUID jobId, String status, boolean duplicate) {
	}

	public record JobView(UUID jobId, String status, int attempts, String error, Long runId, int fileCount,
			OffsetDateTime createdAt, OffsetDateTime finishedAt) {
	}

	@Transactional
	public Accepted accept(ApiKeyPrincipal key, List<MultipartFile> files, UploadMetadata meta) {
		if (files == null || files.isEmpty()) {
			throw ApiException.badRequest("Attach at least one report file as 'files'");
		}
		if (files.size() > props.maxFiles()) {
			throw ApiException.badRequest("At most " + props.maxFiles() + " files per upload");
		}
		String format = meta.format() == null || meta.format().isBlank() ? "JUNIT" : meta.format().strip().toUpperCase();
		if (!format.equals("JUNIT")) {
			throw ApiException.badRequest("Unsupported format '" + meta.format() + "'. Supported: junit");
		}
		String idempotencyKey = idempotencyKey(meta);
		UUID jobId = UUID.randomUUID();

		// A retried upload with the same key hits the unique constraint and changes nothing.
		int inserted = db.sql("""
				insert into ingestion_jobs (id, project_id, api_key_id, idempotency_key, format, status, file_count,
				                            commit_sha, branch, ci_provider, build_url, triggered_at)
				values (:id, :project, :key, :idem, :format, 'QUEUED', :files, :commit, :branch, :ci, :build, :at)
				on conflict (project_id, idempotency_key) do nothing""")
				.param("id", jobId)
				.param("project", key.projectId())
				.param("key", key.keyId())
				.param("idem", idempotencyKey)
				.param("format", format)
				.param("files", files.size())
				.param("commit", trimToNull(meta.commitSha()))
				.param("branch", trimToNull(meta.branch()))
				.param("ci", trimToNull(meta.ciProvider()))
				.param("build", trimToNull(meta.buildUrl()))
				.param("at", meta.triggeredAt() != null ? meta.triggeredAt() : OffsetDateTime.now())
				.update();

		if (inserted == 0) {
			JobView existing = db.sql("""
					select id as job_id, status, attempts, error, run_id, file_count, created_at, finished_at
					from ingestion_jobs where project_id = :project and idempotency_key = :idem""")
					.param("project", key.projectId())
					.param("idem", idempotencyKey)
					.query(JobView.class).single();
			return new Accepted(existing.jobId(), existing.status(), true);
		}

		for (int i = 0; i < files.size(); i++) {
			MultipartFile file = files.get(i);
			byte[] bytes = bytes(file);
			if (bytes.length == 0) {
				throw ApiException.badRequest("File '" + file.getOriginalFilename() + "' is empty");
			}
			db.sql("insert into ingestion_job_files (job_id, seq, file_name, content) values (?, ?, ?, ?)")
					.params(jobId, i, nameOf(file, i), Gzip.isGzip(bytes) ? bytes : Gzip.compress(bytes))
					.update();
		}
		return new Accepted(jobId, "QUEUED", false);
	}

	public JobView job(ApiKeyPrincipal key, UUID jobId) {
		return db.sql("""
				select id as job_id, status, attempts, error, run_id, file_count, created_at, finished_at
				from ingestion_jobs where id = :id and project_id = :project""")
				.param("id", jobId)
				.param("project", key.projectId())
				.query(JobView.class).optional()
				.orElseThrow(() -> ApiException.notFound("Job not found"));
	}

	/** Recent uploads of a project, for the web UI (failed uploads show their error there). */
	public List<JobView> recentJobs(UUID projectId) {
		return db.sql("""
				select id as job_id, status, attempts, error, run_id, file_count, created_at, finished_at
				from ingestion_jobs where project_id = :project order by created_at desc limit 20""")
				.param("project", projectId)
				.query(JobView.class).list();
	}

	/**
	 * The caller's key, else "provider:runId:attempt" (a retried CI step gives the same key), else random
	 * (no deduplication possible).
	 */
	static String idempotencyKey(UploadMetadata meta) {
		if (meta.idempotencyKey() != null && !meta.idempotencyKey().isBlank()) {
			return meta.idempotencyKey().strip();
		}
		if (meta.runId() != null && !meta.runId().isBlank()) {
			String provider = meta.ciProvider() == null ? "ci" : meta.ciProvider().strip().toLowerCase();
			String attempt = meta.runAttempt() == null || meta.runAttempt().isBlank() ? "1" : meta.runAttempt().strip();
			return provider + ":" + meta.runId().strip() + ":" + attempt;
		}
		return "random:" + UUID.randomUUID();
	}

	private static byte[] bytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String nameOf(MultipartFile file, int index) {
		String name = file.getOriginalFilename();
		return name == null || name.isBlank() ? "report-" + (index + 1) + ".xml" : name;
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
