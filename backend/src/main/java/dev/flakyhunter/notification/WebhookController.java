package dev.flakyhunter.notification;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
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

/** Webhook management (owner only). */
@RestController
@RequestMapping("/api/projects/{slug}/webhooks")
class WebhookController {

	private final WebhookService webhooks;

	WebhookController(WebhookService webhooks) {
		this.webhooks = webhooks;
	}

	record CreateWebhook(@NotNull WebhookService.Kind kind, @NotBlank @Size(max = 500) String url) {
	}

	record UpdateWebhook(boolean enabled) {
	}

	@GetMapping
	List<WebhookService.WebhookView> list(@PathVariable String slug) {
		return webhooks.list(slug);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	WebhookService.CreatedWebhook create(@PathVariable String slug, @Valid @RequestBody CreateWebhook body) {
		return webhooks.create(slug, body.kind(), body.url());
	}

	@PatchMapping("/{id}")
	WebhookService.WebhookView update(@PathVariable String slug, @PathVariable UUID id,
			@RequestBody UpdateWebhook body) {
		return webhooks.setEnabled(slug, id, body.enabled());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable String slug, @PathVariable UUID id) {
		webhooks.delete(slug, id);
	}

	@PostMapping("/{id}/test")
	WebhookService.WebhookView test(@PathVariable String slug, @PathVariable UUID id) {
		return webhooks.sendTest(slug, id);
	}

}
