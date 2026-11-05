package dev.flakyhunter.notification;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Delivers "test became flaky" alerts to a project's enabled webhooks: Slack ({text}), Discord ({content}) or a
 * generic JSON body signed with HMAC-SHA256 in {@code X-FlakyHunter-Signature: sha256=<hex>}. Deliveries run
 * in the background (never slowing down ingestion), time out after 5 s, and their result is stored as the
 * webhook's last status.
 */
@Component
public class Notifier {

	private static final Logger log = LoggerFactory.getLogger(Notifier.class);
	private static final Duration TIMEOUT = Duration.ofSeconds(5);
	private static final int MAX_TESTS_LISTED = 10;

	private final JdbcClient db;
	private final ObjectMapper json = new ObjectMapper();
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER) // a redirect could point somewhere internal
			.build();
	private final String baseUrl;

	Notifier(JdbcClient db, @Value("${flakyhunter.app-base-url:https://flakyhunter.vercel.app}") String baseUrl) {
		this.db = db;
		this.baseUrl = baseUrl.replaceAll("/+$", "");
	}

	record Hook(UUID id, String kind, String url, String secret) {
	}

	record FlakyTest(long id, String suite, String name, BigDecimal score) {
	}

	record ProjectInfo(String slug, String name) {
	}

	/** Called after a run is committed; returns immediately. */
	public void newlyFlaky(UUID projectId, long runId, List<Long> testIds) {
		Thread.ofVirtual().name("webhooks").start(() -> {
			try {
				deliverNewlyFlaky(projectId, runId, testIds);
			}
			catch (RuntimeException e) {
				log.warn("Webhook delivery failed for project {}", projectId, e);
			}
		});
	}

	void deliverNewlyFlaky(UUID projectId, long runId, List<Long> testIds) {
		List<Hook> hooks = db.sql("select id, kind, url, secret from webhooks where project_id = :p and enabled")
				.param("p", projectId).query(Hook.class).list();
		if (hooks.isEmpty()) {
			return;
		}
		ProjectInfo project = db.sql("select slug, name from projects where id = :p").param("p", projectId)
				.query(ProjectInfo.class).single();
		List<FlakyTest> tests = db.sql("""
				select id, suite, name, score from test_cases where id in (:ids) order by score desc limit :limit""")
				.param("ids", testIds).param("limit", MAX_TESTS_LISTED)
				.query(FlakyTest.class).list();
		for (Hook hook : hooks) {
			deliver(hook, message(hook.kind(), project, runId, tests, testIds.size()));
		}
	}

	/** A sample message so an owner can check the URL works. */
	void sendTest(UUID webhookId) {
		Hook hook = db.sql("select id, kind, url, secret from webhooks where id = :id").param("id", webhookId)
				.query(Hook.class).single();
		String text = "✅ FlakyHunter test message: this webhook works.";
		String body = switch (hook.kind()) {
			case "SLACK" -> write(Map.of("text", text));
			case "DISCORD" -> write(Map.of("content", text));
			default -> write(Map.of("event", "test", "message", text));
		};
		deliver(hook, body);
	}

	String message(String kind, ProjectInfo project, long runId, List<FlakyTest> tests, int total) {
		String runUrl = baseUrl + "/p/" + project.slug() + "/runs/" + runId;
		String lines = tests.stream()
				.map(t -> "• " + t.suite() + " › " + t.name() + " (score " + t.score() + ") " + baseUrl + "/p/"
						+ project.slug() + "/tests/" + t.id())
				.collect(Collectors.joining("\n"));
		String more = total > tests.size() ? "\n…and " + (total - tests.size()) + " more" : "";
		String text = "🔁 " + total + " test" + (total == 1 ? "" : "s") + " became flaky in " + project.name()
				+ "\n" + lines + more + "\nRun: " + runUrl;
		return switch (kind) {
			case "SLACK" -> write(Map.of("text", text));
			case "DISCORD" -> write(Map.of("content", text.length() > 1900 ? text.substring(0, 1900) + "…" : text));
			default -> write(Map.of(
					"event", "tests.became_flaky",
					"project", Map.of("slug", project.slug(), "name", project.name()),
					"runUrl", runUrl,
					"tests", tests.stream().map(t -> Map.of("id", t.id(), "suite", t.suite(), "name", t.name(),
							"score", t.score(), "url", baseUrl + "/p/" + project.slug() + "/tests/" + t.id())).toList(),
					"total", total));
		};
	}

	private void deliver(Hook hook, String body) {
		String status;
		try {
			URI uri = WebhookUrls.requirePublicHttps(hook.url());
			HttpRequest.Builder request = HttpRequest.newBuilder(uri)
					.timeout(TIMEOUT)
					.header("Content-Type", "application/json")
					.header("User-Agent", "FlakyHunter-Webhooks/1.0")
					.POST(HttpRequest.BodyPublishers.ofString(body));
			if (hook.kind().equals("GENERIC")) {
				request.header("X-FlakyHunter-Signature", "sha256=" + hmac(hook.secret(), body));
			}
			HttpResponse<Void> response = http.send(request.build(), HttpResponse.BodyHandlers.discarding());
			status = String.valueOf(response.statusCode());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			status = "error: interrupted";
		}
		catch (Exception e) {
			status = "error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
		}
		db.sql("update webhooks set last_status = :s, last_delivery_at = now() where id = :id")
				.param("s", status.length() > 200 ? status.substring(0, 200) : status).param("id", hook.id()).update();
	}

	static String hmac(String secret, String body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	private String write(Object value) {
		return json.writeValueAsString(value);
	}

}
