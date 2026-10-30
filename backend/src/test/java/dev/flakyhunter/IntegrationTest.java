package dev.flakyhunter;

import java.util.Arrays;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for tests that need the real app and a real Postgres (Testcontainers).
 * Needs Docker: skipped on the laptop, runs in GitHub Actions.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "flakyhunter.auth.jwt-secret=test-only-secret-that-is-long-enough-for-hs256")
@AutoConfigureMockMvc
public abstract class IntegrationTest {

	protected static final String PASSWORD = "correct-horse-battery";

	@Autowired
	protected MockMvc mvc;

	protected static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	/** Signs up a new user and returns their login cookies. */
	protected Cookie[] signup(String email) throws Exception {
		MvcResult result = mvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s","displayName":"Test User"}""".formatted(email, PASSWORD)))
				.andExpect(status().isCreated())
				.andReturn();
		return result.getResponse().getCookies();
	}

	protected static Cookie findCookie(Cookie[] cookies, String name) {
		return Arrays.stream(cookies).filter(c -> c.getName().equals(name)).findFirst()
				.orElseThrow(() -> new AssertionError("missing cookie " + name));
	}

}
