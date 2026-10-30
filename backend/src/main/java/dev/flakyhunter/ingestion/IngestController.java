package dev.flakyhunter.ingestion;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dev.flakyhunter.common.RateLimiter;
import dev.flakyhunter.project.ApiKeyPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** CI-facing endpoints, authenticated with an API key (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/ingest")
class IngestController {

	private final IngestionService ingestion;
	private final RateLimiter rateLimiter;

	IngestController(IngestionService ingestion, RateLimiter rateLimiter) {
		this.ingestion = ingestion;
		this.rateLimiter = rateLimiter;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<IngestionService.Accepted> upload(
			@RequestParam(name = "files", required = false) List<MultipartFile> files,
			@RequestParam(required = false) String format,
			@RequestParam(required = false) String commit,
			@RequestParam(required = false) String branch,
			@RequestParam(required = false) String ciProvider,
			@RequestParam(required = false) String buildUrl,
			@RequestParam(required = false) String runId,
			@RequestParam(required = false) String runAttempt,
			@RequestParam(required = false) String idempotencyKey,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime triggeredAt) {
		ApiKeyPrincipal key = ApiKeyPrincipal.current();
		rateLimiter.check("ingest:" + key.keyId(), rateLimiter.limits().ingestPerMinute(), Duration.ofMinutes(1));
		var meta = new IngestionService.UploadMetadata(format, commit, branch, ciProvider, buildUrl, runId, runAttempt,
				idempotencyKey, triggeredAt);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestion.accept(key, files, meta));
	}

	@GetMapping("/jobs/{id}")
	IngestionService.JobView job(@PathVariable UUID id) {
		return ingestion.job(ApiKeyPrincipal.current(), id);
	}

}
