package dev.flakyhunter.project;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "api_keys")
public class ApiKey {

	@Id
	private UUID id;

	@Column(name = "project_id")
	private UUID projectId;

	private String name;

	private String prefix;

	@Column(name = "key_hash")
	private String keyHash;

	@Column(name = "created_by")
	private UUID createdBy;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected ApiKey() {
	}

	public ApiKey(UUID projectId, String name, String prefix, String keyHash, UUID createdBy) {
		this.id = UUID.randomUUID();
		this.projectId = projectId;
		this.name = name;
		this.prefix = prefix;
		this.keyHash = keyHash;
		this.createdBy = createdBy;
		this.createdAt = Instant.now();
	}

	public void revoke() {
		if (revokedAt == null) {
			revokedAt = Instant.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getName() {
		return name;
	}

	public String getPrefix() {
		return prefix;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastUsedAt() {
		return lastUsedAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

}
