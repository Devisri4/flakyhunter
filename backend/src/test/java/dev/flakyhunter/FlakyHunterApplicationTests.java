package dev.flakyhunter;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FlakyHunterApplicationTests extends IntegrationTest {

	@Test
	void contextLoads() {
	}

	@Test
	void healthIsPublicAndDatabaseIsUp() throws Exception {
		mvc.perform(get("/api/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components.db.status").value("UP"));
	}

	@Test
	void apiDocsArePublic() throws Exception {
		mvc.perform(get("/api/docs/openapi"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/ingest']").exists());
		String uiPage = mvc.perform(get("/api/docs")).andExpect(status().is3xxRedirection())
				.andReturn().getResponse().getRedirectedUrl();
		mvc.perform(get(uiPage)).andExpect(status().isOk());
	}

	@Test
	void otherEndpointsNeedLogin() throws Exception {
		mvc.perform(get("/api/projects"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

}
