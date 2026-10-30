package dev.flakyhunter.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import dev.flakyhunter.IntegrationTest;
import dev.flakyhunter.auth.AuthCookies;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IngestionFlowTests extends IntegrationTest {

	@Autowired
	IngestionWorker worker;

	private Cookie owner;
	private String slug;
	private String apiKey;
	private String apiKeyId;

	@BeforeEach
	void setUp() throws Exception {
		owner = findCookie(signup(uniqueEmail()), AuthCookies.ACCESS);
		slug = newProject(owner);
		String created = mvc.perform(post("/api/projects/" + slug + "/api-keys").with(csrf()).cookie(owner)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ci\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		apiKey = JsonPath.read(created, "$.key");
		apiKeyId = JsonPath.read(created, "$.apiKey.id");
	}

	@Test
	void uploadedReportBecomesARunWithTotalsAndFailures() throws Exception {
		String jobId = upload(apiKey, req -> req
				.file(report("surefire-basic.xml"))
				.file(report("surefire-reruns.xml"))
				.param("branch", "main")
				.param("commit", "abc1234")
				.param("ciProvider", "github")
				.param("buildUrl", "https://github.com/acme/shop/actions/runs/1"));
		assertThat(awaitJob(jobId)).isEqualTo("DONE");

		String runs = mvc.perform(get("/api/projects/" + slug + "/runs").cookie(owner))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].total").value(7))
				.andExpect(jsonPath("$.content[0].passed").value(2))
				.andExpect(jsonPath("$.content[0].failed").value(3))
				.andExpect(jsonPath("$.content[0].skipped").value(1))
				.andExpect(jsonPath("$.content[0].flaky").value(1))
				.andExpect(jsonPath("$.content[0].branch").value("main"))
				.andExpect(jsonPath("$.content[0].commitSha").value("abc1234"))
				.andReturn().getResponse().getContentAsString();
		int runId = JsonPath.read(runs, "$.content[0].id");

		mvc.perform(get("/api/projects/" + slug + "/runs/" + runId).cookie(owner))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.failed.length()").value(3))
				.andExpect(jsonPath("$.flaky[0].name").value("paysWithCard"))
				.andExpect(jsonPath("$.flaky[0].attempts").value(2))
				.andExpect(jsonPath("$.failed[?(@.name == 'computesTotal')].message")
						.value("expected: <42> but was: <41>"));

		// the job status endpoint links to the run, and the uploaded files are gone
		mvc.perform(get("/api/v1/ingest/jobs/" + jobId).header("X-Api-Key", apiKey))
				.andExpect(jsonPath("$.runId").value(runId));
		mvc.perform(get("/api/projects/" + slug + "/uploads").cookie(owner))
				.andExpect(jsonPath("$[0].status").value("DONE"));
	}

	@Test
	void theSameTestAcrossRunsKeepsOneTestCase() throws Exception {
		awaitJob(upload(apiKey, req -> req.file(report("gradle.xml"))));
		awaitJob(upload(apiKey, req -> req.file(report("gradle.xml"))));
		mvc.perform(get("/api/projects/" + slug + "/runs").cookie(owner))
				.andExpect(jsonPath("$.totalElements").value(2));
		String runs = mvc.perform(get("/api/projects/" + slug + "/runs").cookie(owner))
				.andReturn().getResponse().getContentAsString();
		List<Integer> ids = JsonPath.read(runs, "$.content[*].id");
		String first = mvc.perform(get("/api/projects/" + slug + "/runs/" + ids.get(0)).cookie(owner))
				.andReturn().getResponse().getContentAsString();
		String second = mvc.perform(get("/api/projects/" + slug + "/runs/" + ids.get(1)).cookie(owner))
				.andReturn().getResponse().getContentAsString();
		Integer caseA = JsonPath.read(first, "$.failed[0].testCaseId");
		Integer caseB = JsonPath.read(second, "$.failed[0].testCaseId");
		assertThat(caseA).isEqualTo(caseB);
	}

	@Test
	void parallelUploadsWithOneIdempotencyKeyCreateOneRun() throws Exception {
		byte[] xml = bytes("surefire-basic.xml");
		Callable<String> task = () -> upload(apiKey, req -> req
				.file(new MockMultipartFile("files", "TEST-basic.xml", "application/xml", xml))
				.param("ciProvider", "github").param("runId", "12345").param("runAttempt", "1"));
		List<Future<String>> futures = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
			for (int i = 0; i < 10; i++) {
				futures.add(pool.submit(task));
			}
		}
		Set<String> jobIds = new HashSet<>();
		for (Future<String> f : futures) {
			jobIds.add(f.get());
		}
		assertThat(jobIds).hasSize(1);
		assertThat(awaitJob(jobIds.iterator().next())).isEqualTo("DONE");
		mvc.perform(get("/api/projects/" + slug + "/runs").cookie(owner))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void gzipUploadsWork() throws Exception {
		byte[] gz = Gzip.compress(bytes("pytest.xml"));
		String jobId = upload(apiKey, req -> req.file(new MockMultipartFile("files", "report.xml.gz",
				"application/gzip", gz)));
		assertThat(awaitJob(jobId)).isEqualTo("DONE");
	}

	@Test
	void aBrokenReportFailsWithAReadableError() throws Exception {
		String jobId = upload(apiKey, req -> req.file(report("malformed.xml")));
		assertThat(awaitJob(jobId)).isEqualTo("FAILED");
		mvc.perform(get("/api/v1/ingest/jobs/" + jobId).header("X-Api-Key", apiKey))
				.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.startsWith("Malformed XML")));
	}

	@Test
	void missingWrongOrRevokedKeysAre401() throws Exception {
		mvc.perform(multipart("/api/v1/ingest").file(report("gradle.xml"))).andExpect(status().isUnauthorized());
		mvc.perform(multipart("/api/v1/ingest").file(report("gradle.xml")).header("X-Api-Key", "fh_wrong"))
				.andExpect(status().isUnauthorized());
		// a logged-in browser cookie is not an API key
		mvc.perform(multipart("/api/v1/ingest").file(report("gradle.xml")).cookie(owner))
				.andExpect(status().isUnauthorized());

		mvc.perform(delete("/api/projects/" + slug + "/api-keys/" + apiKeyId).with(csrf()).cookie(owner))
				.andExpect(status().isNoContent());
		mvc.perform(multipart("/api/v1/ingest").file(report("gradle.xml")).header("X-Api-Key", apiKey))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aKeyCannotSeeAnotherProjectsJobs() throws Exception {
		String jobId = upload(apiKey, req -> req.file(report("gradle.xml")));

		Cookie stranger = findCookie(signup(uniqueEmail()), AuthCookies.ACCESS);
		String otherSlug = newProject(stranger);
		String otherKey = JsonPath.read(mvc.perform(post("/api/projects/" + otherSlug + "/api-keys").with(csrf())
				.cookie(stranger).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ci\"}"))
				.andReturn().getResponse().getContentAsString(), "$.key");

		mvc.perform(get("/api/v1/ingest/jobs/" + jobId).header("X-Api-Key", otherKey))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/projects/" + slug + "/runs").cookie(stranger)).andExpect(status().isNotFound());
	}

	@Test
	void uploadWithoutFilesIs400() throws Exception {
		mvc.perform(multipart("/api/v1/ingest").param("branch", "main").header("X-Api-Key", apiKey))
				.andExpect(status().isBadRequest());
	}

	// --- helpers ---

	private interface RequestCustomizer {

		MockMultipartHttpServletRequestBuilder apply(MockMultipartHttpServletRequestBuilder request) throws Exception;

	}

	private String upload(String key, RequestCustomizer customizer) throws Exception {
		String body = mvc.perform(customizer.apply(multipart("/api/v1/ingest")).header("X-Api-Key", key))
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.jobId");
	}

	/** Runs the worker until the job is finished; returns DONE or FAILED. */
	private String awaitJob(String jobId) throws Exception {
		for (int i = 0; i < 50; i++) {
			worker.processAvailable();
			String body = mvc.perform(get("/api/v1/ingest/jobs/" + jobId).header("X-Api-Key", apiKey))
					.andReturn().getResponse().getContentAsString();
			String status = JsonPath.read(body, "$.status");
			if (status.equals("DONE") || status.equals("FAILED")) {
				return status;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("job " + jobId + " did not finish");
	}

	private String newProject(Cookie user) throws Exception {
		String slugName = "p-" + UUID.randomUUID().toString().substring(0, 8);
		mvc.perform(post("/api/projects").with(csrf()).cookie(user).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Shop\",\"slug\":\"%s\"}".formatted(slugName)))
				.andExpect(status().isCreated());
		return slugName;
	}

	private static MockMultipartFile report(String fixture) throws IOException {
		return new MockMultipartFile("files", "TEST-" + fixture, "application/xml", bytes(fixture));
	}

	private static byte[] bytes(String fixture) throws IOException {
		try (InputStream in = IngestionFlowTests.class.getResourceAsStream("/reports/" + fixture)) {
			return in.readAllBytes();
		}
	}

}
