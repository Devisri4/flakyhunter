package dev.flakyhunter.common;

import dev.flakyhunter.auth.AuthCookies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	/** Auth endpoints that must work while logged out (or with an expired access cookie). */
	private static final String[] PUBLIC_AUTH = {
			"/api/auth/csrf", "/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth
				// Health and info are public: the website and the keep-alive ping read them without logging in.
				.requestMatchers("/api/actuator/health/**", "/api/actuator/info").permitAll()
				.requestMatchers(PUBLIC_AUTH).permitAll()
				.anyRequest().authenticated())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// SPA mode: XSRF-TOKEN cookie readable by JS, echoed back in the X-XSRF-TOKEN header.
				// CI endpoints use API keys, not cookies, so they don't need CSRF protection.
				.csrf(csrf -> csrf.spa().ignoringRequestMatchers("/api/v1/**"))
				// The access JWT arrives in an HttpOnly cookie instead of an Authorization header.
				.oauth2ResourceServer(o -> o
						.bearerTokenResolver(cookieTokenResolver())
						.authenticationEntryPoint(problemEntryPoint())
						.jwt(Customizer.withDefaults()))
				// Not logged in => 401 (not Spring's default 403), so the SPA knows to refresh or log in.
				.exceptionHandling(ex -> ex.authenticationEntryPoint(problemEntryPoint()));
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

	private static AuthenticationEntryPoint problemEntryPoint() {
		return (request, response, e) -> {
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
			response.setContentType("application/problem+json");
			response.getWriter().write(
					"{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Login required\"}");
		};
	}

}
