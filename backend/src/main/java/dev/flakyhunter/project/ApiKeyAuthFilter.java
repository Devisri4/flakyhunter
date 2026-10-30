package dev.flakyhunter.project;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates CI requests by the {@code X-Api-Key} header (or {@code Authorization: Bearer fh_...}).
 * Created by the security config for the /api/v1 chain only, so it is deliberately not a @Component.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

	private final ApiKeyService apiKeys;

	public ApiKeyAuthFilter(ApiKeyService apiKeys) {
		this.apiKeys = apiKeys;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String key = request.getHeader("X-Api-Key");
		String authorization = request.getHeader("Authorization");
		if (key == null && authorization != null && authorization.startsWith("Bearer " + ApiKeyService.KEY_PREFIX)) {
			key = authorization.substring("Bearer ".length());
		}
		if (key != null) {
			apiKeys.authenticate(key.strip())
					.ifPresent(p -> SecurityContextHolder.getContext().setAuthentication(new ApiKeyPrincipal.Token(p)));
		}
		chain.doFilter(request, response); // no valid key => the entry point answers 401
	}

}
