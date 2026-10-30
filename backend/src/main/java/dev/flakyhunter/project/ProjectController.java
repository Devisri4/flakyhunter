package dev.flakyhunter.project;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
class ProjectController {

	private final ProjectService projects;
	private final MemberService members;
	private final ApiKeyService apiKeys;

	ProjectController(ProjectService projects, MemberService members, ApiKeyService apiKeys) {
		this.projects = projects;
		this.members = members;
		this.apiKeys = apiKeys;
	}

	record CreateProject(@NotBlank @Size(max = 80) String name, @Size(max = 40) String slug,
			@Size(max = 100) String defaultBranch) {
	}

	record UpdateProject(@Size(max = 80) String name, @Size(max = 100) String defaultBranch) {
	}

	record AddMember(@NotBlank @Email String email, @NotNull Role role) {
	}

	record ChangeRole(@NotNull Role role) {
	}

	record CreateApiKey(@NotBlank @Size(max = 60) String name) {
	}

	// --- projects ---

	@GetMapping
	List<ProjectSummary> list() {
		return projects.myProjects();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ProjectSummary create(@Valid @RequestBody CreateProject body) {
		return projects.create(body.name(), body.slug(), body.defaultBranch());
	}

	@GetMapping("/{slug}")
	ProjectSummary get(@PathVariable String slug) {
		return projects.get(slug);
	}

	@PatchMapping("/{slug}")
	ProjectSummary update(@PathVariable String slug, @Valid @RequestBody UpdateProject body) {
		return projects.update(slug, body.name(), body.defaultBranch());
	}

	@DeleteMapping("/{slug}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable String slug) {
		projects.delete(slug);
	}

	// --- members ---

	@GetMapping("/{slug}/members")
	List<MemberView> members(@PathVariable String slug) {
		return members.list(slug);
	}

	@PostMapping("/{slug}/members")
	@ResponseStatus(HttpStatus.CREATED)
	MemberView addMember(@PathVariable String slug, @Valid @RequestBody AddMember body) {
		return members.add(slug, body.email(), body.role());
	}

	@PatchMapping("/{slug}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changeRole(@PathVariable String slug, @PathVariable UUID userId, @Valid @RequestBody ChangeRole body) {
		members.changeRole(slug, userId, body.role());
	}

	@DeleteMapping("/{slug}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeMember(@PathVariable String slug, @PathVariable UUID userId) {
		members.remove(slug, userId);
	}

	// --- API keys ---

	@GetMapping("/{slug}/api-keys")
	List<ApiKeyService.ApiKeyView> apiKeys(@PathVariable String slug) {
		return apiKeys.list(slug);
	}

	@PostMapping("/{slug}/api-keys")
	@ResponseStatus(HttpStatus.CREATED)
	ApiKeyService.CreatedApiKey createApiKey(@PathVariable String slug, @Valid @RequestBody CreateApiKey body) {
		return apiKeys.create(slug, body.name());
	}

	@DeleteMapping("/{slug}/api-keys/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revokeApiKey(@PathVariable String slug, @PathVariable UUID id) {
		apiKeys.revoke(slug, id);
	}

}
