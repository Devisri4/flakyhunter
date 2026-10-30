package dev.flakyhunter.project;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import dev.flakyhunter.IntegrationTest;
import dev.flakyhunter.auth.AuthCookies;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectAccessTests extends IntegrationTest {

	private Cookie alice;
	private Cookie bob;
	private String bobEmail;
	private String slug;

	@BeforeEach
	void setUp() throws Exception {
		alice = findCookie(signup(uniqueEmail()), AuthCookies.ACCESS);
		bobEmail = uniqueEmail();
		bob = findCookie(signup(bobEmail), AuthCookies.ACCESS);
		slug = "proj-" + UUID.randomUUID().toString().substring(0, 8);
		send(post("/api/projects"), alice, """
				{"name":"Acme Shop","slug":"%s"}""".formatted(slug))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("OWNER"))
				.andExpect(jsonPath("$.defaultBranch").value("main"));
	}

	@Test
	void creatorSeesTheProjectInTheirList() throws Exception {
		mvc.perform(get("/api/projects").cookie(alice))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].slug", hasItem(slug)));
	}

	@Test
	void nonMembersGet404OnEveryProjectEndpoint() throws Exception {
		mvc.perform(get("/api/projects/" + slug).cookie(bob)).andExpect(status().isNotFound());
		mvc.perform(get("/api/projects/" + slug + "/members").cookie(bob)).andExpect(status().isNotFound());
		mvc.perform(get("/api/projects/" + slug + "/api-keys").cookie(bob)).andExpect(status().isNotFound());
		send(post("/api/projects/" + slug + "/api-keys"), bob, "{\"name\":\"ci\"}").andExpect(status().isNotFound());
		send(patch("/api/projects/" + slug), bob, "{\"name\":\"Hacked\"}").andExpect(status().isNotFound());
		mvc.perform(delete("/api/projects/" + slug).with(csrf()).cookie(bob)).andExpect(status().isNotFound());
		// and the project does not show up in Bob's list
		mvc.perform(get("/api/projects").cookie(bob)).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void viewersCanReadButNotManage() throws Exception {
		send(post("/api/projects/" + slug + "/members"), alice, """
				{"email":"%s","role":"VIEWER"}""".formatted(bobEmail)).andExpect(status().isCreated());

		mvc.perform(get("/api/projects/" + slug).cookie(bob))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("VIEWER"));
		mvc.perform(get("/api/projects/" + slug + "/api-keys").cookie(bob)).andExpect(status().isForbidden());
		mvc.perform(delete("/api/projects/" + slug).with(csrf()).cookie(bob)).andExpect(status().isForbidden());
	}

	@Test
	void apiKeyIsShownOnceAndOnlyItsPrefixIsListed() throws Exception {
		String body = send(post("/api/projects/" + slug + "/api-keys"), alice, "{\"name\":\"GitHub Actions\"}")
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String key = JsonPath.read(body, "$.key");
		String id = JsonPath.read(body, "$.apiKey.id");
		assertThat(key).startsWith("fh_").hasSize(46);

		mvc.perform(get("/api/projects/" + slug + "/api-keys").cookie(alice))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].prefix").value(key.substring(0, 11)))
				.andExpect(jsonPath("$[0].key").doesNotExist())
				.andExpect(jsonPath("$[0].revokedAt").doesNotExist());

		mvc.perform(delete("/api/projects/" + slug + "/api-keys/" + id).with(csrf()).cookie(alice))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/projects/" + slug + "/api-keys").cookie(alice))
				.andExpect(jsonPath("$[0].revokedAt").exists());
	}

	@Test
	void theLastOwnerCannotBeRemoved() throws Exception {
		String aliceId = JsonPath.read(mvc.perform(get("/api/auth/me").cookie(alice))
				.andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(delete("/api/projects/" + slug + "/members/" + aliceId).with(csrf()).cookie(alice))
				.andExpect(status().isConflict());
	}

	@Test
	void slugsMustBeUniqueAndWellFormed() throws Exception {
		send(post("/api/projects"), bob, "{\"name\":\"Copy\",\"slug\":\"%s\"}".formatted(slug))
				.andExpect(status().isConflict());
		send(post("/api/projects"), bob, "{\"name\":\"Bad\",\"slug\":\"Not A Slug!\"}")
				.andExpect(status().isBadRequest());
	}

	private ResultActions send(MockHttpServletRequestBuilder request, Cookie user, String json) throws Exception {
		return mvc.perform(request.with(csrf()).cookie(user).contentType(MediaType.APPLICATION_JSON).content(json));
	}

}
