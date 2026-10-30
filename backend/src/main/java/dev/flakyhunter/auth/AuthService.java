package dev.flakyhunter.auth;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import dev.flakyhunter.common.ApiException;
import dev.flakyhunter.common.Tokens;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	/** Same message for "no such user" and "wrong password", so emails can't be probed. */
	static final String INVALID_LOGIN = "Invalid email or password";

	private final UserRepository users;
	private final RefreshTokenRepository refreshTokens;
	private final PasswordEncoder passwordEncoder;
	private final JwtEncoder jwtEncoder;
	private final AuthProperties props;
	private final String dummyHash;

	AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
			JwtEncoder jwtEncoder, AuthProperties props) {
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
		this.props = props;
		this.dummyHash = passwordEncoder.encode("timing-equaliser");
	}

	/** A logged-in session: a fresh access JWT and a fresh refresh token. */
	public record Session(User user, String accessToken, String refreshToken, UUID refreshTokenId) {
	}

	@Transactional
	public Session signup(String email, String password, String displayName) {
		String normalised = normaliseEmail(email);
		if (users.existsByEmail(normalised)) {
			throw ApiException.conflict("An account with this email already exists");
		}
		User user = users.save(new User(normalised, passwordEncoder.encode(password), displayName.strip()));
		return startSession(user, UUID.randomUUID());
	}

	@Transactional
	public Session login(String email, String password) {
		User user = users.findByEmail(normaliseEmail(email)).orElse(null);
		if (user == null) {
			passwordEncoder.matches(password, dummyHash); // take as long as a real check
			throw ApiException.unauthorized(INVALID_LOGIN);
		}
		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw ApiException.unauthorized(INVALID_LOGIN);
		}
		return startSession(user, UUID.randomUUID());
	}

	/**
	 * Rotates the refresh token: the presented one is revoked and replaced. Presenting an
	 * already-revoked token means it was stolen or replayed, so the whole family is revoked.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public Session refresh(String presentedToken) {
		if (presentedToken == null) {
			throw ApiException.unauthorized("Login required");
		}
		Instant now = Instant.now();
		RefreshToken current = refreshTokens.findByTokenHash(Tokens.sha256Hex(presentedToken))
				.orElseThrow(() -> ApiException.unauthorized("Login required"));
		if (current.isRevoked()) {
			refreshTokens.revokeFamily(current.getFamilyId(), now);
			throw ApiException.unauthorized("Session ended, please log in again");
		}
		if (current.isExpired(now)) {
			throw ApiException.unauthorized("Session expired, please log in again");
		}
		User user = users.findById(current.getUserId())
				.orElseThrow(() -> ApiException.unauthorized("Login required"));
		Session next = startSession(user, current.getFamilyId());
		current.revoke(now, next.refreshTokenId());
		return next;
	}

	@Transactional
	public void logout(String presentedToken) {
		if (presentedToken == null) {
			return;
		}
		refreshTokens.findByTokenHash(Tokens.sha256Hex(presentedToken))
				.ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), Instant.now()));
	}

	private Session startSession(User user, UUID familyId) {
		Instant now = Instant.now();
		String refresh = Tokens.randomSecret();
		RefreshToken row = refreshTokens.save(new RefreshToken(user.getId(), familyId, Tokens.sha256Hex(refresh),
				now.plus(props.refreshTokenTtl())));
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("flakyhunter")
				.subject(user.getId().toString())
				.issuedAt(now)
				.expiresAt(now.plus(props.accessTokenTtl()))
				.claim("email", user.getEmail())
				.build();
		String access = jwtEncoder
				.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		return new Session(user, access, refresh, row.getId());
	}

	static String normaliseEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
