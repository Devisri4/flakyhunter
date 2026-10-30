package dev.flakyhunter.project;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "projects")
public class Project {

	@Id
	private UUID id;

	private String slug;

	private String name;

	@Column(name = "default_branch")
	private String defaultBranch;

	@Column(name = "is_public_demo")
	private boolean publicDemo;

	@Column(name = "created_at")
	private Instant createdAt;

	protected Project() {
	}

	public Project(String slug, String name, String defaultBranch) {
		this.id = UUID.randomUUID();
		this.slug = slug;
		this.name = name;
		this.defaultBranch = defaultBranch;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getSlug() {
		return slug;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDefaultBranch() {
		return defaultBranch;
	}

	public void setDefaultBranch(String defaultBranch) {
		this.defaultBranch = defaultBranch;
	}

	public boolean isPublicDemo() {
		return publicDemo;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
