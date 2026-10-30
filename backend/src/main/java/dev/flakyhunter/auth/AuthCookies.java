package dev.flakyhunter.auth;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The two login cookies. Both are HttpOnly (JavaScript can't read them) and SameSite=Lax.
 * The refresh cookie is only sent to /api/auth, so it travels as rarely as possible.
 */
@Component
public class AuthCookies {

	public static final String ACCESS = "fh_access";
	public static final String REFRESH = "fh_refresh";
	private static final String ACCESS_PATH = "/api";
	private static final String REFRESH_PATH = "/api/auth";

	private final AuthProperties props;

	AuthCookies(AuthProperties props) {
		this.props = props;
	}

	public ResponseCookie access(String jwt) {
		return cookie(ACCESS, jwt, ACCESS_PATH, props.accessTokenTtl());
	}

	public ResponseCookie refresh(String token) {
		return cookie(REFRESH, token, REFRESH_PATH, props.refreshTokenTtl());
	}

	public ResponseCookie clearAccess() {
		return cookie(ACCESS, "", ACCESS_PATH, Duration.ZERO);
	}

	public ResponseCookie clearRefresh() {
		return cookie(REFRESH, "", REFRESH_PATH, Duration.ZERO);
	}

	public static String read(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie c : cookies) {
			if (c.getName().equals(name) && !c.getValue().isBlank()) {
				return c.getValue();
			}
		}
		return null;
	}

	private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
		return ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(props.secureCookies())
				.sameSite("Lax")
				.path(path)
				.maxAge(maxAge)
				.build();
	}

}
