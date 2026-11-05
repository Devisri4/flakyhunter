package dev.flakyhunter.common;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The logged-in user, taken from the access-token JWT (subject = user id). */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static UUID id() {
		return optionalId().orElseThrow(() -> ApiException.unauthorized("Login required"));
	}

	/** Empty for anonymous visitors (e.g. someone looking at the public demo). */
	public static Optional<UUID> optionalId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof JwtAuthenticationToken jwt) {
			return Optional.of(UUID.fromString(jwt.getName()));
		}
		return Optional.empty();
	}

}
