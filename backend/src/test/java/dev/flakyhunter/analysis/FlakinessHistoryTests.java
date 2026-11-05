package dev.flakyhunter.analysis;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import dev.flakyhunter.IntegrationTest;
import dev.flakyhunter.auth.AuthCookies;
import dev.flakyhunter.ingestion.IngestionWorker;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 "done when": a history of 12 CI runs with known flaky / broken tests produces the expected states.
 * Each string is one test's outcome per run: P pass, F fail, R failed then passed on rerun, S skipped, - absent.
 */
class FlakinessHistoryTests extends IntegrationTest {

	private static final Map<String, String> HISTORY = new LinkedHashMap<>();
	static {
		HISTORY.put("alwaysGreen",    "PPPPPPPPPPPP");
		HISTORY.put("flakyCheckout",  "PFPPRPFPPRPF");
		HISTORY.put("brokenRefund",   "PPPPPPPPPFFF");
		HISTORY.put("retriedOnce",    "PPPPPPPPPPPR");
		HISTORY.put("brandNew",       "---------PPP");
		HISTORY.put("alwaysSkipped",  "SSSSSSSSSSSS");
	}
	private static final int RUNS = 12;

	@Autowired
	IngestionWorker worker;

	@Autowired
	MaintenanceJobs maintenance;

	@Autowired
	JdbcClient db;

	private Cookie owner;
	private String slug;
	private String apiKey;

	@BeforeEach
	void uploadTheHistory() throws Exception {
		owner = findCookie(signup(uniqueEmail()), AuthCookies.ACCESS);
		slug = "hist-" + UUID.randomUUID().toString().substring(0, 8);
		mvc.perform(post("/api/projects").with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"History\",\"slug\":\"%s\"}".formatted(slug))).andExpect(status().isCreated());
		apiKey = JsonPath.read(mvc.perform(post("/api/projects/" + slug + "/api-keys").with(csrf()).cookie(owner)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ci\"}"))
				.andReturn().getResponse().getContentAsString(), "$.key");

		OffsetDateTime start = OffsetDateTime.of(2026, 10, 1, 8, 0, 0, 0, ZoneOffset.UTC);
		for (int run = 0; run < RUNS; run++) {
			String body = mvc.perform(multipart("/api/v1/ingest")
					.file(new MockMultipartFile("files", "TEST-run" + run + ".xml", "application/xml",
							reportForRun(run).getBytes(StandardCharsets.UTF_8)))
					.param("commit", "commit" + run)
					.param("branch", "main")
					.param("triggeredAt", start.plusHours(run).toString())
					.header("X-Api-Key", apiKey))
					.andExpect(status().isAccepted())
					.andReturn().getResponse().getContentAsString();
			awaitDone(JsonPath.read(body, "$.jobId"));
		}
	}

	@Test
	void eachTestGetsTheExpectedVerdict() throws Exception {
		assertThat(states()).containsExactlyInAnyOrderEntriesOf(Map.of(
				"alwaysGreen", "STABLE",
				"flakyCheckout", "FLAKY",
				"brokenRefund", "BROKEN",
				"retriedOnce", "SUSPECT",
				"brandNew", "NEW",
				"alwaysSkipped", "NEW"));

		mvc.perform(get("/api/projects/" + slug + "/tests/summary").cookie(owner))
				.andExpect(jsonPath("$.FLAKY").value(1))
				.andExpect(jsonPath("$.BROKEN").value(1))
				.andExpect(jsonPath("$.NEW").value(2));

		// sorted by score: the flaky test comes first, with its window and counters
		mvc.perform(get("/api/projects/" + slug + "/tests?state=FLAKY").cookie(owner))
				.andExpect(jsonPath("$.content[0].name").value("flakyCheckout"))
				.andExpect(jsonPath("$.content[0].windowOutcomes").value("PFPPRPFPPRPF"))
				.andExpect(jsonPath("$.content[0].retryPassCount").value(2))
				.andExpect(jsonPath("$.content[0].score").value(0.468));
	}

	@Test
	void stateChangesAreLoggedOnlyWhenTheVerdictChanges() {
		List<String> transitions = db.sql("""
				select coalesce(sc.from_state, '?') || '>' || sc.to_state
				from state_changes sc join test_cases c on c.id = sc.test_case_id
				join projects p on p.id = c.project_id
				where p.slug = :slug and c.name = 'brokenRefund' order by sc.id""")
				.param("slug", slug).query(String.class).list();
		assertThat(transitions).containsExactly("NEW>STABLE", "STABLE>BROKEN");
	}

	@Test
	void sameRootCauseIsOneSignature() throws Exception {
		// the timeouts differ only in numbers and selectors, so they form one signature: flakyCheckout's 3 fails
		// + 2 failed attempts before a retry, plus retriedOnce's failed attempt => 6 failures in 2 tests.
		// The broken test's 3 assertion failures form another.
		mvc.perform(get("/api/projects/" + slug + "/signatures").cookie(owner))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].exceptionType").value("org.openqa.selenium.TimeoutException"))
				.andExpect(jsonPath("$.content[0].normalizedMessage").value("Timeout <dur> exceeded waiting for <str>"))
				.andExpect(jsonPath("$.content[0].occurrences").value(6))
				.andExpect(jsonPath("$.content[0].affectedTests").value(2))
				.andExpect(jsonPath("$.content[1].occurrences").value(3));
	}

	@Test
	void theNightlyRecomputeAgreesWithTheIncrementalStats() throws Exception {
		Map<String, String> before = states();
		UUID projectId = db.sql("select id from projects where slug = :slug").param("slug", slug)
				.query(UUID.class).single();
		assertThat(maintenance.recompute(projectId)).isZero();
		assertThat(states()).isEqualTo(before);
	}

	@Test
	void dailyStatsAreAggregated() {
		long runs = db.sql("""
				select sum(d.runs) from daily_stats d join projects p on p.id = d.project_id where p.slug = :slug""")
				.param("slug", slug).query(Long.class).single();
		assertThat(runs).isEqualTo(RUNS);
	}

	// --- helpers ---

	private Map<String, String> states() throws Exception {
		String body = mvc.perform(get("/api/projects/" + slug + "/tests?size=100").cookie(owner))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<String> names = JsonPath.read(body, "$.content[*].name");
		List<String> states = JsonPath.read(body, "$.content[*].state");
		Map<String, String> result = new LinkedHashMap<>();
		for (int i = 0; i < names.size(); i++) {
			result.put(names.get(i), states.get(i));
		}
		return result;
	}

	private void awaitDone(String jobId) throws Exception {
		for (int i = 0; i < 50; i++) {
			worker.processAvailable();
			String status = JsonPath.read(mvc.perform(get("/api/v1/ingest/jobs/" + jobId).header("X-Api-Key", apiKey))
					.andReturn().getResponse().getContentAsString(), "$.status");
			if (status.equals("DONE")) {
				return;
			}
			if (status.equals("FAILED")) {
				throw new AssertionError("job failed");
			}
			Thread.sleep(100);
		}
		throw new AssertionError("job did not finish");
	}

	private static String reportForRun(int run) {
		StringBuilder xml = new StringBuilder("<testsuite name=\"com.acme.shop.ShopTest\">\n");
		HISTORY.forEach((name, outcomes) -> {
			char outcome = outcomes.charAt(run);
			String open = "  <testcase classname=\"com.acme.shop.ShopTest\" name=\"" + name + "\" time=\"0.1\"";
			switch (outcome) {
				case 'P' -> xml.append(open).append("/>\n");
				case 'S' -> xml.append(open).append("><skipped/></testcase>\n");
				case 'F' -> xml.append(open).append(">").append(failure("failure", name, run)).append("</testcase>\n");
				case 'R' -> xml.append(open).append(">").append(failure("flakyFailure", name, run)).append("</testcase>\n");
				default -> {
					// '-': the test didn't exist yet
				}
			}
		});
		return xml.append("</testsuite>\n").toString();
	}

	/** Failures whose details change every run (timeouts, ids) but whose root cause stays the same. */
	private static String failure(String element, String test, int run) {
		if (test.equals("brokenRefund")) {
			return "<%s message=\"expected: &lt;%d&gt; but was: &lt;0&gt;\" type=\"java.lang.AssertionError\">"
					.formatted(element, 100 + run)
					+ "java.lang.AssertionError: refund missing\n\tat com.acme.shop.Refunds.check(Refunds.java:" + (20 + run)
					+ ")</" + element + ">";
		}
		String stack = "org.openqa.selenium.TimeoutException: Timeout\n\tat com.acme.shop.CheckoutPage.pay(CheckoutPage.java:"
				+ (40 + run) + ")";
		String message = "Timeout %dms exceeded waiting for &quot;#pay-%d&quot;".formatted(3000 + run * 500, run);
		String inner = element.equals("flakyFailure") ? "<stackTrace>" + stack + "</stackTrace>" : stack;
		return "<%s message=\"%s\" type=\"org.openqa.selenium.TimeoutException\">%s</%s>".formatted(element, message,
				inner, element);
	}

}
