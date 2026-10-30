package dev.flakyhunter.auth;

import dev.flakyhunter.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowTests extends IntegrationTest {

	@Test
	void signupSetsHttpOnlyCookiesAndMeReturnsTheUser() throws Exception {
		String email = uniqueEmail();
		Cookie[] cookies = signup(email.toUpperCase());

		Cookie access = findCookie(cookies, AuthCookies.ACCESS);
		Cookie refresh = findCookie(cookies, AuthCookies.REFRESH);
		assertThat(access.isHttpOnly()).isTrue();
		assertThat(refresh.isHttpOnly()).isTrue();
		assertThat(refresh.getPath()).isEqualTo("/api/auth");

		mvc.perform(get("/api/auth/me").cookie(access))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email)) // stored lower-case
				.andExpect(jsonPath("$.displayName").value("Test User"));
	}

	@Test
	void meWithoutLoginIs401() throws Exception {
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void duplicateEmailIs409ProblemDetail() throws Exception {
		String email = uniqueEmail();
		signup(email);
		mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s","displayName":"Again"}""".formatted(email, PASSWORD)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail").value("An account with this email already exists"));
	}

	@Test
	void shortPasswordIs400() throws Exception {
		mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"short","displayName":"X"}""".formatted(uniqueEmail())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("password: must be 8 to 72 characters"));
	}

	@Test
	void wrongPasswordAndUnknownEmailGiveTheSameMessage() throws Exception {
		String email = uniqueEmail();
		signup(email);
		login(email, "wrong-password-123").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value(AuthService.INVALID_LOGIN));
		login(uniqueEmail(), PASSWORD).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value(AuthService.INVALID_LOGIN));
	}

	@Test
	void loginWorksWithTheRightPassword() throws Exception {
		String email = uniqueEmail();
		signup(email);
		login(email, PASSWORD).andExpect(status().isOk())
				.andExpect(cookie().exists(AuthCookies.ACCESS))
				.andExpect(cookie().exists(AuthCookies.REFRESH));
	}

	@Test
	void stateChangingRequestsNeedTheCsrfToken() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"a@b.c\",\"password\":\"whatever1\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void refreshRotatesTheTokenAndReuseRevokesTheWholeFamily() throws Exception {
		Cookie first = findCookie(signup(uniqueEmail()), AuthCookies.REFRESH);

		MvcResult rotated = refresh(first).andExpect(status().isOk()).andReturn();
		Cookie second = rotated.getResponse().getCookie(AuthCookies.REFRESH);
		assertThat(second).isNotNull();
		assertThat(second.getValue()).isNotEqualTo(first.getValue());

		// the old token is replayed (e.g. stolen) => rejected, and the family is revoked
		refresh(first).andExpect(status().isUnauthorized());
		refresh(second).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesTheRefreshToken() throws Exception {
		Cookie refresh = findCookie(signup(uniqueEmail()), AuthCookies.REFRESH);
		mvc.perform(post("/api/auth/logout").with(csrf()).cookie(refresh))
				.andExpect(status().isNoContent())
				.andExpect(cookie().maxAge(AuthCookies.ACCESS, 0));
		refresh(refresh).andExpect(status().isUnauthorized());
	}

	@Test
	void csrfEndpointHandsOutAToken() throws Exception {
		mvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());
	}

	private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
		return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}""".formatted(email, password)));
	}

	private org.springframework.test.web.servlet.ResultActions refresh(Cookie refreshCookie) throws Exception {
		return mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(refreshCookie));
	}

}
