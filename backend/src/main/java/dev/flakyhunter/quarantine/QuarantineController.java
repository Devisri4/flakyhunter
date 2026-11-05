package dev.flakyhunter.quarantine;

import java.util.List;

import dev.flakyhunter.project.ApiKeyPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class QuarantineController {

	private final QuarantineService quarantine;

	QuarantineController(QuarantineService quarantine) {
		this.quarantine = quarantine;
	}

	record QuarantineRequest(@NotBlank @Size(max = 500) String reason, Integer days) {
	}

	/** Browser: quarantine a test (member role). */
	@PostMapping("/api/projects/{slug}/tests/{id:\\d+}/quarantine")
	@ResponseStatus(HttpStatus.CREATED)
	QuarantineService.QuarantinedTest quarantine(@PathVariable String slug, @PathVariable long id,
			@Valid @RequestBody QuarantineRequest body) {
		return quarantine.quarantine(slug, id, body.reason(), body.days());
	}

	/** Browser: lift a quarantine (member role). */
	@DeleteMapping("/api/projects/{slug}/tests/{id:\\d+}/quarantine")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void lift(@PathVariable String slug, @PathVariable long id) {
		quarantine.lift(slug, id);
	}

	/** Browser: the active list (viewer role), e.g. for the setup page's Playwright pattern. */
	@GetMapping("/api/projects/{slug}/quarantine")
	List<QuarantineService.QuarantinedTest> list(@PathVariable String slug) {
		return quarantine.activeForViewer(slug);
	}

	/** CI (API key): which tests to skip in this project. */
	@GetMapping("/api/v1/quarantine")
	List<QuarantineService.QuarantinedTest> forCi() {
		return quarantine.active(ApiKeyPrincipal.current().projectId());
	}

}
