package dev.flakyhunter.common;

import dev.flakyhunter.auth.AuthCookies;
import dev.flakyhunter.project.ApiKeyAuthFilter;
import dev.flakyhunter.project.ApiKeyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {

	/** Auth endpoints that must work while logged out (or with an expired access cookie). */
	private static final String[] PUBLIC_AUTH = {
			"/api/auth/csrf", "/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout" };

	/** CI endpoints: API key only (no cookies, so no CSRF), checked before the browser chain below. */
	@Bean
	@Order(1)
	SecurityFilterChain ciFilterChain(HttpSecurity http, ApiKeyService apiKeys) throws Exception {
		http.securityMatcher("/api/v1/**")
				.authorizeHttpRequests(auth -> auth.anyRequest().hasRole("CI"))
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.disable())
				.addFilterBefore(new ApiKeyAuthFilter(apiKeys), AnonymousAuthenticationFilter.class)
				.exceptionHandling(ex -> ex.authenticationEntryPoint(problemEntryPoint("A valid API key is required "
						+ "in the X-Api-Key header")));
		return http.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth
				// Health and info are public: the website and the keep-alive ping read them without logging in.
				.requestMatchers("/api/actuator/health/**", "/api/actuator/info").permitAll()
				.requestMatchers(PUBLIC_AUTH).permitAll()
				.anyRequest().authenticated())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// SPA mode: XSRF-TOKEN cookie readable by JS, echoed back in the X-XSRF-TOKEN header.
				// CI endpoints use API keys, not cookies, so they don't need CSRF protection.
				.csrf(csrf -> csrf.spa())
				// The access JWT arrives in an HttpOnly cookie instead of an Authorization header.
				.oauth2ResourceServer(o -> o
						.bearerTokenResolver(cookieTokenResolver())
						.authenticationEntryPoint(problemEntryPoint("Login required"))
						.jwt(Customizer.withDefaults()))
				// Not logged in => 401 (not Spring's default 403), so the SPA knows to refresh or log in.
				.exceptionHandling(ex -> ex.authenticationEntryPoint(problemEntryPoint("Login required")));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	private static BearerTokenResolver cookieTokenResolver() {
		return request -> {
			String path = request.getRequestURI();
			for (String publicPath : PUBLIC_AUTH) {
				if (path.equals(publicPath)) {
					return null; // ignore a stale access cookie here, or refresh could never succeed
				}
			}
			return AuthCookies.read(request, AuthCookies.ACCESS);
		};
	}

	private static AuthenticationEntryPoint problemEntryPoint(String detail) {
		String body = "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"" + detail + "\"}";
		return (request, response, e) -> {
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
			response.setContentType("application/problem+json");
			response.getWriter().write(body);
		};
	}

}
