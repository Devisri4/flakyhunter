package dev.flakyhunter.auth;

import java.util.Map;
import java.util.UUID;

import dev.flakyhunter.common.ApiException;
import dev.flakyhunter.common.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

	private final AuthService auth;
	private final AuthCookies cookies;
	private final UserRepository users;

	AuthController(AuthService auth, AuthCookies cookies, UserRepository users) {
		this.auth = auth;
		this.cookies = cookies;
		this.users = users;
	}

	record SignupRequest(
			@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String password,
			@NotBlank @Size(max = 60) String displayName) {
	}

	record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	record UserResponse(UUID id, String email, String displayName) {

		static UserResponse of(User user) {
			return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
		}

	}

	/** The SPA calls this first: reading the token makes Spring Security set the XSRF-TOKEN cookie. */
	@GetMapping("/csrf")
	Map<String, String> csrf(CsrfToken token) {
		return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
	}

	@PostMapping("/signup")
	ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest body) {
		return withSession(HttpStatus.CREATED, auth.signup(body.email(), body.password(), body.displayName()));
	}

	@PostMapping("/login")
	ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest body) {
		return withSession(HttpStatus.OK, auth.login(body.email(), body.password()));
	}

	@PostMapping("/refresh")
	ResponseEntity<UserResponse> refresh(HttpServletRequest request) {
		try {
			return withSession(HttpStatus.OK, auth.refresh(AuthCookies.read(request, AuthCookies.REFRESH)));
		}
		catch (ApiException e) {
			// a dead refresh token is useless to the browser: clear both cookies with the 401
			return ResponseEntity.status(e.status())
					.header(HttpHeaders.SET_COOKIE, cookies.clearAccess().toString())
					.header(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString())
					.build();
		}
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(HttpServletRequest request) {
		auth.logout(AuthCookies.read(request, AuthCookies.REFRESH));
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, cookies.clearAccess().toString())
				.header(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString())
				.build();
	}

	@GetMapping("/me")
	UserResponse me() {
		return users.findById(CurrentUser.id())
				.map(UserResponse::of)
				.orElseThrow(() -> ApiException.unauthorized("Login required"));
	}

	private ResponseEntity<UserResponse> withSession(HttpStatus status, AuthService.Session session) {
		return ResponseEntity.status(status)
				.header(HttpHeaders.SET_COOKIE, cookies.access(session.accessToken()).toString())
				.header(HttpHeaders.SET_COOKIE, cookies.refresh(session.refreshToken()).toString())
				.body(UserResponse.of(session.user()));
	}

}
