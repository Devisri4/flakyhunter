package dev.flakyhunter.common;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The logged-in user, taken from the access-token JWT (subject = user id). */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static UUID id() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof JwtAuthenticationToken jwt) {
			return UUID.fromString(jwt.getName());
		}
		throw ApiException.unauthorized("Login required");
	}

}
