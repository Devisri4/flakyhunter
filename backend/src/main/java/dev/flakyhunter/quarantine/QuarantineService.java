package dev.flakyhunter.quarantine;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dev.flakyhunter.common.ApiException;
import dev.flakyhunter.project.ProjectAccess;
import dev.flakyhunter.project.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quarantine: a member takes a flaky test out of CI for a while, with a reason and an expiry (14 days by default,
 * so quarantine can't become a graveyard). FlakyHunter can't skip tests in someone's CI itself, so it publishes
 * the list ({@code GET /api/v1/quarantine}); the JUnit 5 extension or a Playwright grep pattern uses it.
 */
@Service
public class QuarantineService {

	private static final Logger log = LoggerFactory.getLogger(QuarantineService.class);
	static final int DEFAULT_DAYS = 14;
	static final int MAX_DAYS = 90;

	private final JdbcClient db;
	private final ProjectAccess access;

	QuarantineService(JdbcClient db, ProjectAccess access) {
		this.db = db;
		this.access = access;
	}

	/** What CI needs to skip a test: its fingerprint (SHA-256 of "suite::name") and the readable names. */
	public record QuarantinedTest(long testId, String fingerprint, String suite, String name, String reason,
			OffsetDateTime expiresAt, OffsetDateTime createdAt, String createdBy) {
	}

	@Transactional
	public QuarantinedTest quarantine(String slug, long testId, String reason, Integer days) {
		ProjectAccess.Access a = access.require(slug, Role.MEMBER);
		requireTest(a.project().getId(), testId);
		int length = days == null ? DEFAULT_DAYS : days;
		if (length < 1 || length > MAX_DAYS) {
			throw ApiException.badRequest("days must be between 1 and " + MAX_DAYS);
		}
		try {
			db.sql("""
					insert into quarantines (test_case_id, reason, created_by, expires_at)
					values (:test, :reason, :user, now() + make_interval(days => :days))""")
					.param("test", testId).param("reason", reason.strip()).param("user", a.userId())
					.param("days", length).update();
		}
		catch (DuplicateKeyException e) {
			throw ApiException.conflict("This test is already quarantined");
		}
		return active(a.project().getId()).stream().filter(q -> q.testId() == testId).findFirst().orElseThrow();
	}

	@Transactional
	public void lift(String slug, long testId) {
		ProjectAccess.Access a = access.require(slug, Role.MEMBER);
		requireTest(a.project().getId(), testId);
		int lifted = db.sql("""
				update quarantines set lifted_at = now(), lifted_by = :user, lift_reason = 'manual'
				where test_case_id = :test and lifted_at is null""")
				.param("user", a.userId()).param("test", testId).update();
		if (lifted == 0) {
			throw ApiException.notFound("This test is not quarantined");
		}
	}

	/** Active (not lifted, not expired) quarantines of a project, newest first. */
	public List<QuarantinedTest> active(UUID projectId) {
		return db.sql("""
				select c.id as test_id, c.fingerprint, c.suite, c.name, q.reason, q.expires_at, q.created_at,
				       u.display_name as created_by
				from quarantines q
				join test_cases c on c.id = q.test_case_id
				left join users u on u.id = q.created_by
				where c.project_id = :p and q.lifted_at is null and q.expires_at > now()
				order by q.created_at desc""")
				.param("p", projectId).query(QuarantinedTest.class).list();
	}

	public List<QuarantinedTest> activeForViewer(String slug) {
		return active(access.require(slug, Role.VIEWER).project().getId());
	}

	/** Expired quarantines are lifted (and logged as such) every hour; CI already ignores them on expiry. */
	@Scheduled(fixedDelayString = "1h", initialDelayString = "5m")
	public int liftExpired() {
		int lifted = db.sql("""
				update quarantines set lifted_at = now(), lift_reason = 'expired'
				where lifted_at is null and expires_at <= now()""").update();
		if (lifted > 0) {
			log.info("Lifted {} expired quarantines", lifted);
		}
		return lifted;
	}

	private void requireTest(UUID projectId, long testId) {
		boolean exists = db.sql("select exists(select 1 from test_cases where id = :id and project_id = :p)")
				.param("id", testId).param("p", projectId).query(Boolean.class).single();
		if (!exists) {
			throw ApiException.notFound("Test not found");
		}
	}

}
