package dev.flakyhunter.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	private UUID id;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "family_id")
	private UUID familyId;

	@Column(name = "token_hash")
	private String tokenHash;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "replaced_by")
	private UUID replacedBy;

	@Column(name = "created_at")
	private Instant createdAt;

	protected RefreshToken() {
	}

	public RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant expiresAt) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.familyId = familyId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = Instant.now();
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	public void revoke(Instant now, UUID replacedBy) {
		this.revokedAt = now;
		this.replacedBy = replacedBy;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getFamilyId() {
		return familyId;
	}

}
