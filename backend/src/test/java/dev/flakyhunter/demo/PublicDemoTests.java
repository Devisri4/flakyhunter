package dev.flakyhunter.demo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import dev.flakyhunter.IntegrationTest;
import dev.flakyhunter.auth.AuthCookies;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 4: the generated demo, the dashboard endpoints, test detail, and who may read what. */
class PublicDemoTests extends IntegrationTest {

	private static final int DAYS = 3;
	private static final int TESTS = 40;
	private static final int RUNS_PER_DAY = 4;

	@Autowired
	DemoDataGenerator generator;

	private String slug;

	@BeforeEach
	void generateASmallDemo() {
		slug = "demo-" + UUID.randomUUID().toString().substring(0, 8);
		assertThat(generator.topUp(slug, DAYS, TESTS, RUNS_PER_DAY, Instant.now())).isEqualTo(DAYS * RUNS_PER_DAY);
	}

	@Test
	void topUpIsIdempotent() {
		assertThat(generator.topUp(slug, DAYS, TESTS, RUNS_PER_DAY, Instant.now())).isZero();
	}

	@Test
	void anyoneCanReadTheDemoWithoutAnAccount() throws Exception {
		mvc.perform(get("/api/projects/" + slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("VIEWER"));
		mvc.perform(get("/api/projects/" + slug + "/runs"))
				.andExpect(jsonPath("$.totalElements").value(DAYS * RUNS_PER_DAY));
		mvc.perform(get("/api/projects/" + slug + "/tests?size=100"))
				.andExpect(jsonPath("$.totalElements", greaterThan(30)))
				.andExpect(jsonPath("$.totalElements", lessThanOrEqualTo(TESTS)));
		mvc.perform(get("/api/projects/" + slug + "/signatures")).andExpect(status().isOk());
	}

	@Test
	void butNobodyCanChangeIt() throws Exception {
		// anonymous: writes and owner-only reads need a login
		mvc.perform(post("/api/projects/" + slug + "/api-keys").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/projects/" + slug + "/members")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/projects/" + slug + "/api-keys")).andExpect(status().isUnauthorized());

		// logged in, but not a member: may read, may not manage
		Cookie stranger = findCookie(signup(uniqueEmail()), AuthCookies.ACCESS);
		mvc.perform(get("/api/projects/" + slug).cookie(stranger))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("VIEWER"));
		mvc.perform(get("/api/projects/" + slug + "/api-keys").cookie(stranger)).andExpect(status().isNotFound());
		mvc.perform(post("/api/projects/" + slug + "/api-keys").with(csrf()).cookie(stranger)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void privateProjectsStayPrivateForAnonymousVisitors() throws Exception {
		Cookie owner = findCookie(signup(uniqueEmail()), AuthCookies.ACCESS);
		String privateSlug = "priv-" + UUID.randomUUID().toString().substring(0, 8);
		mvc.perform(post("/api/projects").with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Secret\",\"slug\":\"%s\"}".formatted(privateSlug)))
				.andExpect(status().isCreated());

		// same answer for an existing private project and a missing one: nothing to probe
		mvc.perform(get("/api/projects/" + privateSlug)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/projects/" + privateSlug + "/runs")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/projects/does-not-exist")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
	}

	@Test
	void theDemoSlugIsReserved() throws Exception {
		Cookie user = findCookie(signup(uniqueEmail()), AuthCookies.ACCESS);
		mvc.perform(post("/api/projects").with(csrf()).cookie(user).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Mine\",\"slug\":\"demo\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void dashboardSummaryAndTrends() throws Exception {
		mvc.perform(get("/api/projects/" + slug + "/summary?days=7"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days").value(7))
				.andExpect(jsonPath("$.runs").value(DAYS * RUNS_PER_DAY))
				.andExpect(jsonPath("$.passRate", greaterThan(50.0)))
				.andExpect(jsonPath("$.avgRunDurationMs", greaterThan(0)))
				.andExpect(jsonPath("$.totalTests", greaterThan(30)));
		mvc.perform(get("/api/projects/" + slug + "/trends?days=7"))
				.andExpect(jsonPath("$", hasSize(7)))
				.andExpect(jsonPath("$[6].runs", greaterThan(0)));
		mvc.perform(get("/api/projects/" + slug + "/summary?days=12")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/projects/" + slug + "/tests/newly-flaky")).andExpect(status().isOk());
	}

	@Test
	void testDetailExplainsTheScore() throws Exception {
		String list = mvc.perform(get("/api/projects/" + slug + "/tests?sort=score&size=1"))
				.andReturn().getResponse().getContentAsString();
		int id = JsonPath.<List<Integer>>read(list, "$.content[*].id").getFirst();

		String detail = mvc.perform(get("/api/projects/" + slug + "/tests/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.test.id").value(id))
				.andExpect(jsonPath("$.breakdown.settings.flakyThreshold").value(0.30))
				.andExpect(jsonPath("$.durations.avgMs").exists())
				.andReturn().getResponse().getContentAsString();
		double parts = JsonPath.<Double>read(detail, "$.breakdown.retryPart")
				+ JsonPath.<Double>read(detail, "$.breakdown.flipPart")
				+ JsonPath.<Double>read(detail, "$.breakdown.commitPart");
		double score = JsonPath.<Double>read(detail, "$.breakdown.score");
		assertThat(Math.min(1, parts)).isCloseTo(score, org.assertj.core.data.Offset.offset(0.001));

		mvc.perform(get("/api/projects/" + slug + "/tests/" + id + "/results?limit=5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(5)));
		mvc.perform(get("/api/projects/" + slug + "/tests/999999999")).andExpect(status().isNotFound());
	}

}
