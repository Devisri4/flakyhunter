package dev.flakyhunter.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("flakyhunter.auth")
public record AuthProperties(
		String jwtSecret,
		@DefaultValue("15m") Duration accessTokenTtl,
		@DefaultValue("7d") Duration refreshTokenTtl,
		@DefaultValue("true") boolean secureCookies) {

	public AuthProperties {
		// HS256 needs at least 256 bits of key material
		if (jwtSecret == null || jwtSecret.length() < 32) {
			throw new IllegalStateException("JWT_SECRET must be set and at least 32 characters long");
		}
	}

}
