package dev.flakyhunter.notification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dev.flakyhunter.common.ApiException;
import dev.flakyhunter.common.Tokens;
import dev.flakyhunter.project.ProjectAccess;
import dev.flakyhunter.project.Role;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owners manage a project's webhooks. The signing secret is shown once, when the webhook is created. */
@Service
public class WebhookService {

	public enum Kind {
		SLACK, DISCORD, GENERIC
	}

	public record WebhookView(UUID id, String kind, String url, boolean enabled, OffsetDateTime createdAt,
			String lastStatus, OffsetDateTime lastDeliveryAt) {
	}

	public record CreatedWebhook(WebhookView webhook, String secret) {
	}

	private final JdbcClient db;
	private final ProjectAccess access;
	private final Notifier notifier;

	WebhookService(JdbcClient db, ProjectAccess access, Notifier notifier) {
		this.db = db;
		this.access = access;
		this.notifier = notifier;
	}

	public List<WebhookView> list(String slug) {
		UUID projectId = access.require(slug, Role.OWNER).project().getId();
		return db.sql("""
				select id, kind, url, enabled, created_at, last_status, last_delivery_at
				from webhooks where project_id = :p order by created_at""")
				.param("p", projectId).query(WebhookView.class).list();
	}

	@Transactional
	public CreatedWebhook create(String slug, Kind kind, String url) {
		UUID projectId = access.require(slug, Role.OWNER).project().getId();
		WebhookUrls.requirePublicHttps(url);
		long count = db.sql("select count(*) from webhooks where project_id = :p").param("p", projectId)
				.query(Long.class).single();
		if (count >= 10) {
			throw ApiException.conflict("A project can have at most 10 webhooks");
		}
		UUID id = UUID.randomUUID();
		String secret = "whsec_" + Tokens.randomSecret();
		db.sql("insert into webhooks (id, project_id, kind, url, secret) values (:id, :p, :kind, :url, :secret)")
				.param("id", id).param("p", projectId).param("kind", kind.name()).param("url", url.strip())
				.param("secret", secret).update();
		return new CreatedWebhook(find(projectId, id), secret);
	}

	@Transactional
	public WebhookView setEnabled(String slug, UUID id, boolean enabled) {
		UUID projectId = access.require(slug, Role.OWNER).project().getId();
		db.sql("update webhooks set enabled = :e where id = :id and project_id = :p")
				.param("e", enabled).param("id", id).param("p", projectId).update();
		return find(projectId, id);
	}

	@Transactional
	public void delete(String slug, UUID id) {
		UUID projectId = access.require(slug, Role.OWNER).project().getId();
		int deleted = db.sql("delete from webhooks where id = :id and project_id = :p")
				.param("id", id).param("p", projectId).update();
		if (deleted == 0) {
			throw ApiException.notFound("Webhook not found");
		}
	}

	/** Sends a sample message now and returns the delivery result ("200 OK", "error: ..."). */
	public WebhookView sendTest(String slug, UUID id) {
		UUID projectId = access.require(slug, Role.OWNER).project().getId();
		find(projectId, id);
		notifier.sendTest(id);
		return find(projectId, id);
	}

	private WebhookView find(UUID projectId, UUID id) {
		return db.sql("""
				select id, kind, url, enabled, created_at, last_status, last_delivery_at
				from webhooks where id = :id and project_id = :p""")
				.param("id", id).param("p", projectId).query(WebhookView.class).optional()
				.orElseThrow(() -> ApiException.notFound("Webhook not found"));
	}

}
