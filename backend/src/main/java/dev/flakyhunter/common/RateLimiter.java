package dev.flakyhunter.common;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * In-memory token buckets (Bucket4j), one per key such as "login:1.2.3.4:a@b.c". Buckets not used for
 * 15 minutes are dropped, so memory stays bounded. Fine for one instance; several would need a shared store.
 */
@Component
public class RateLimiter {

	@ConfigurationProperties("flakyhunter.rate-limits")
	public record Limits(
			@DefaultValue("10") int loginPerMinute,
			@DefaultValue("20") int signupPerHour,
			@DefaultValue("30") int ingestPerMinute) {
	}

	/** 429 Too Many Requests, with the number of seconds to wait for the Retry-After header. */
	public static class RateLimitedException extends ApiException {

		private final long retryAfterSeconds;

		RateLimitedException(long retryAfterSeconds) {
			super(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please try again in " + retryAfterSeconds + " s");
			this.retryAfterSeconds = retryAfterSeconds;
		}

		public long retryAfterSeconds() {
			return retryAfterSeconds;
		}

	}

	private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
			.expireAfterAccess(Duration.ofMinutes(15))
			.maximumSize(100_000)
			.build();

	private final Limits limits;

	RateLimiter(Limits limits) {
		this.limits = limits;
	}

	public Limits limits() {
		return limits;
	}

	/** Takes one token from the bucket for {@code key}, or throws {@link RateLimitedException}. */
	public void check(String key, int capacity, Duration period) {
		Bucket bucket = buckets.get(key, k -> Bucket.builder()
				.addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build())
				.build());
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (!probe.isConsumed()) {
			long seconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
			throw new RateLimitedException(seconds);
		}
	}

}
