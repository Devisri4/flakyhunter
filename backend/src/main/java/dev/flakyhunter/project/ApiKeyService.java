package dev.flakyhunter.project;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.flakyhunter.common.ApiException;
import dev.flakyhunter.common.Tokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyService {

	static final String KEY_PREFIX = "fh_";
	/** How much of the key the UI may show: "fh_" + 8 characters. */
	static final int VISIBLE_LENGTH = KEY_PREFIX.length() + 8;

	private final ApiKeyRepository apiKeys;
	private final ProjectAccess access;

	ApiKeyService(ApiKeyRepository apiKeys, ProjectAccess access) {
		this.apiKeys = apiKeys;
		this.access = access;
	}

	public record ApiKeyView(UUID id, String name, String prefix, Instant createdAt, Instant lastUsedAt,
			Instant revokedAt) {

		static ApiKeyView of(ApiKey k) {
			return new ApiKeyView(k.getId(), k.getName(), k.getPrefix(), k.getCreatedAt(), k.getLastUsedAt(),
					k.getRevokedAt());
		}

	}

	/** Returned only once, right after creation: the full key is never stored or shown again. */
	public record CreatedApiKey(ApiKeyView apiKey, String key) {
	}

	@Transactional(readOnly = true)
	public List<ApiKeyView> list(String slug) {
		UUID projectId = access.require(slug, Role.OWNER).project().getId();
		return apiKeys.findByProjectIdOrderByCreatedAtDesc(projectId).stream().map(ApiKeyView::of).toList();
	}

	@Transactional
	public CreatedApiKey create(String slug, String name) {
		ProjectAccess.Access a = access.require(slug, Role.OWNER);
		String key = newKey();
		ApiKey saved = apiKeys.save(new ApiKey(a.project().getId(), name.strip(), key.substring(0, VISIBLE_LENGTH),
				Tokens.sha256Hex(key), a.userId()));
		return new CreatedApiKey(ApiKeyView.of(saved), key);
	}

	@Transactional
	public void revoke(String slug, UUID keyId) {
		UUID projectId = access.require(slug, Role.OWNER).project().getId();
		ApiKey key = apiKeys.findByIdAndProjectId(keyId, projectId)
				.orElseThrow(() -> ApiException.notFound("API key not found"));
		key.revoke();
		apiKeys.save(key);
	}

	static String newKey() {
		return KEY_PREFIX + Tokens.randomSecret();
	}

}
