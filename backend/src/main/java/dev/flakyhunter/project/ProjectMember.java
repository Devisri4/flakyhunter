package dev.flakyhunter.project;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_members")
public class ProjectMember {

	@Embeddable
	public record Key(@Column(name = "project_id") UUID projectId, @Column(name = "user_id") UUID userId)
			implements Serializable {
	}

	@EmbeddedId
	private Key id;

	@Enumerated(EnumType.STRING)
	private Role role;

	@Column(name = "created_at")
	private Instant createdAt;

	protected ProjectMember() {
	}

	public ProjectMember(UUID projectId, UUID userId, Role role) {
		this.id = new Key(projectId, userId);
		this.role = role;
		this.createdAt = Instant.now();
	}

	public Key getId() {
		return id;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

}
