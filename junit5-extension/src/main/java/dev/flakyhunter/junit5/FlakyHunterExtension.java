package dev.flakyhunter.junit5;

import java.lang.reflect.Method;
import java.util.Optional;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Skips tests that are quarantined in FlakyHunter.
 *
 * <pre>
 * &#64;ExtendWith(FlakyHunterExtension.class)
 * class CheckoutTest { ... }
 * </pre>
 *
 * or for every test class: add {@code junit.jupiter.extensions.autodetection.enabled=true} to
 * {@code src/test/resources/junit-platform.properties} (the extension registers itself via ServiceLoader).
 *
 * <p>Configuration (system property or environment variable):
 * <ul>
 * <li>{@code FLAKYHUNTER_API_KEY} — the project's API key; without it the extension does nothing</li>
 * <li>{@code FLAKYHUNTER_URL} — defaults to {@code https://flakyhunter.vercel.app}</li>
 * </ul>
 * The quarantine list is fetched once per test JVM. If FlakyHunter can't be reached, every test runs (fail open):
 * a monitoring tool must never be the reason a build breaks.
 */
public class FlakyHunterExtension implements ExecutionCondition {

	static final String DEFAULT_URL = "https://flakyhunter.vercel.app";

	private static volatile QuarantineList cached;

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		Optional<Method> method = context.getTestMethod();
		Optional<Class<?>> testClass = context.getTestClass();
		if (method.isEmpty() || testClass.isEmpty()) {
			return ConditionEvaluationResult.enabled("FlakyHunter checks individual tests");
		}
		QuarantineList list = quarantineList();
		Optional<QuarantineList.Entry> hit = list.find(testClass.get().getName(), method.get().getName(),
				context.getDisplayName());
		return hit.map(e -> ConditionEvaluationResult.disabled("Quarantined in FlakyHunter"
				+ (e.reason() == null ? "" : ": " + e.reason())
				+ (e.expiresAt() == null ? "" : " (until " + e.expiresAt() + ")")))
				.orElseGet(() -> ConditionEvaluationResult.enabled("Not quarantined in FlakyHunter"));
	}

	private static QuarantineList quarantineList() {
		QuarantineList list = cached;
		if (list == null) {
			synchronized (FlakyHunterExtension.class) {
				if (cached == null) {
					cached = QuarantineList.fetch(setting("FLAKYHUNTER_URL", DEFAULT_URL), setting("FLAKYHUNTER_API_KEY", null));
				}
				list = cached;
			}
		}
		return list;
	}

	/** For tests: forget the cached list. */
	static void reset() {
		cached = null;
	}

	private static String setting(String name, String fallback) {
		String value = System.getProperty(name);
		if (value == null || value.isBlank()) {
			value = System.getenv(name);
		}
		return value == null || value.isBlank() ? fallback : value.strip();
	}

}
