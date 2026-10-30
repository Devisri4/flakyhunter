package dev.flakyhunter.project;

import java.util.UUID;

import dev.flakyhunter.common.ApiException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/** A CI pipeline authenticated with an API key: it may act on exactly one project. */
public record ApiKeyPrincipal(UUID keyId, UUID projectId) {

	public static ApiKeyPrincipal current() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof Token token) {
			return token.principal;
		}
		throw ApiException.unauthorized("An API key is required (X-Api-Key header)");
	}

	/** The Spring Security authentication object for a valid API key. */
	public static final class Token extends AbstractAuthenticationToken {

		private final ApiKeyPrincipal principal;

		public Token(ApiKeyPrincipal principal) {
			super(AuthorityUtils.createAuthorityList("ROLE_CI"));
			this.principal = principal;
			setAuthenticated(true);
		}

		@Override
		public Object getCredentials() {
			return null;
		}

		@Override
		public ApiKeyPrincipal getPrincipal() {
			return principal;
		}

	}

}
