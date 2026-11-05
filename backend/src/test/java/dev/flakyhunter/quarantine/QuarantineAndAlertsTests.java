package dev.flakyhunter.quarantine;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import dev.flakyhunter.IntegrationTest;
import dev.flakyhunter.auth.AuthCookies;
import dev.flakyhunter.catalog.Fingerprints;
import dev.flakyhunter.ingestion.IngestionWorker;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phases 5–6: Playwright uploads, quarantine (UI + CI list), webhooks. */
class QuarantineAndAlertsTests extends IntegrationTest {

	@Autowired
	IngestionWorker worker;

	@Autowired
	QuarantineService quarantine;

	@Autowired
	JdbcClient db;

	private Cookie owner;
	private String slug;
	private String apiKey;
	private long testId;

	@BeforeEach
	void uploadAPlaywrightReport() throws Exception {
		owner = findCookie(signup(uniqueEmail()), AuthCookies.ACCESS);
		slug = "q-" + UUID.randomUUID().toString().substring(0, 8);
		mvc.perform(post("/api/projects").with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Q\",\"slug\":\"%s\"}".formatted(slug))).andExpect(status().isCreated());
		apiKey = JsonPath.read(mvc.perform(post("/api/projects/" + slug + "/api-keys").with(csrf()).cookie(owner)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ci\"}"))
				.andReturn().getResponse().getContentAsString(), "$.key");

		byte[] report;
		try (InputStream in = getClass().getResourceAsStream("/reports/playwright.json")) {
			report = in.readAllBytes();
		}
		// no format given: detected as Playwright from the content
		String jobId = JsonPath.read(mvc.perform(multipart("/api/v1/ingest")
				.file(new MockMultipartFile("files", "results.json", "application/json", report))
				.header("X-Api-Key", apiKey))
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.jobId");
		for (int i = 0; i < 50; i++) {
			worker.processAvailable();
			String s = JsonPath.read(mvc.perform(get("/api/v1/ingest/jobs/" + jobId).header("X-Api-Key", apiKey))
					.andReturn().getResponse().getContentAsString(), "$.status");
			if (s.equals("DONE")) {
				break;
			}
			assertThat(s).isNotEqualTo("FAILED");
			Thread.sleep(100);
		}
		String tests = mvc.perform(get("/api/projects/" + slug + "/tests?q=firefox").cookie(owner))
				.andReturn().getResponse().getContentAsString();
		testId = ((Number) JsonPath.<List<Object>>read(tests, "$.content[*].id").getFirst()).longValue();
	}

	@Test
	void playwrightRetriesAreCounted() throws Exception {
		mvc.perform(get("/api/projects/" + slug + "/runs").cookie(owner))
				.andExpect(jsonPath("$.content[0].total").value(5))
				.andExpect(jsonPath("$.content[0].flaky").value(1))
				.andExpect(jsonPath("$.content[0].failed").value(2))
				.andExpect(jsonPath("$.content[0].skipped").value(1));
	}

	@Test
	void quarantineShowsUpForCiAndCanBeLifted() throws Exception {
		mvc.perform(post("/api/projects/" + slug + "/tests/" + testId + "/quarantine").with(csrf()).cookie(owner)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"times out on firefox\",\"days\":7}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reason").value("times out on firefox"));

		// CI sees it, with the fingerprint the JUnit 5 extension compares
		String fingerprint = Fingerprints.of("checkout.spec.ts › Cart", "adds an item [firefox]");
		mvc.perform(get("/api/v1/quarantine").header("X-Api-Key", apiKey))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].fingerprint").value(fingerprint))
				.andExpect(jsonPath("$[0].name").value("adds an item [firefox]"));

		mvc.perform(get("/api/projects/" + slug + "/tests/" + testId).cookie(owner))
				.andExpect(jsonPath("$.test.quarantined").value(true))
				.andExpect(jsonPath("$.quarantine.reason").value("times out on firefox"));
		mvc.perform(get("/api/projects/" + slug + "/tests?quarantined=true").cookie(owner))
				.andExpect(jsonPath("$.totalElements").value(1));

		// a second quarantine of the same test is refused
		mvc.perform(post("/api/projects/" + slug + "/tests/" + testId + "/quarantine").with(csrf()).cookie(owner)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"again\"}"))
				.andExpect(status().isConflict());

		mvc.perform(delete("/api/projects/" + slug + "/tests/" + testId + "/quarantine").with(csrf()).cookie(owner))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/quarantine").header("X-Api-Key", apiKey)).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/api/projects/" + slug + "/tests/" + testId).cookie(owner))
				.andExpect(jsonPath("$.quarantines[0].liftReason").value("manual"));
	}

	@Test
	void expiredQuarantinesAreLifted() throws Exception {
		mvc.perform(post("/api/projects/" + slug + "/tests/" + testId + "/quarantine").with(csrf()).cookie(owner)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"short\",\"days\":1}"))
				.andExpect(status().isCreated());
		db.sql("update quarantines set expires_at = now() - interval '1 minute' where test_case_id = :id")
				.param("id", testId).update();
		mvc.perform(get("/api/v1/quarantine").header("X-Api-Key", apiKey)).andExpect(jsonPath("$", hasSize(0)));
		assertThat(quarantine.liftExpired()).isGreaterThanOrEqualTo(1);
		mvc.perform(get("/api/projects/" + slug + "/tests/" + testId).cookie(owner))
				.andExpect(jsonPath("$.quarantines[0].liftReason").value("expired"));
	}

	@Test
	void viewersCannotQuarantine() throws Exception {
		String viewerEmail = uniqueEmail();
		Cookie viewer = findCookie(signup(viewerEmail), AuthCookies.ACCESS);
		mvc.perform(post("/api/projects/" + slug + "/members").with(csrf()).cookie(owner)
				.contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"%s\",\"role\":\"VIEWER\"}".formatted(viewerEmail)))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/projects/" + slug + "/tests/" + testId + "/quarantine").with(csrf()).cookie(viewer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"no\"}"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/projects/" + slug + "/tests/" + testId + "/quarantine").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"no\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void webhooksValidateUrlsAndShowTheSecretOnce() throws Exception {
		mvc.perform(post("/api/projects/" + slug + "/webhooks").with(csrf()).cookie(owner)
				.contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"SLACK\",\"url\":\"http://example.com/x\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/projects/" + slug + "/webhooks").with(csrf()).cookie(owner)
				.contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"GENERIC\",\"url\":\"https://127.0.0.1/x\"}"))
				.andExpect(status().isBadRequest());

		String created = mvc.perform(post("/api/projects/" + slug + "/webhooks").with(csrf()).cookie(owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"GENERIC\",\"url\":\"https://example.com/flakyhunter-hook\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.secret").value(org.hamcrest.Matchers.startsWith("whsec_")))
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(created, "$.webhook.id");

		mvc.perform(get("/api/projects/" + slug + "/webhooks").cookie(owner))
				.andExpect(jsonPath("$[0].url").value("https://example.com/flakyhunter-hook"))
				.andExpect(jsonPath("$[0].secret").doesNotExist());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.patch("/api/projects/" + slug + "/webhooks/" + id).with(csrf()).cookie(owner)
				.contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
				.andExpect(jsonPath("$.enabled").value(false));
		mvc.perform(delete("/api/projects/" + slug + "/webhooks/" + id).with(csrf()).cookie(owner))
				.andExpect(status().isNoContent());
	}

}
