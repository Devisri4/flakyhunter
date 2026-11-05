package dev.flakyhunter.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import dev.flakyhunter.parsing.ParsedTest;
import dev.flakyhunter.parsing.ParsedTest.Outcome;

/**
 * A fictional "Acme Shop" test suite with realistic behaviour. Everything is derived from a fixed seed and the
 * run's time slot, so the same slot always produces the same results (restarts and top-ups stay consistent).
 */
final class DemoCatalog {

	enum Kind {
		/** almost always green */
		STABLE,
		/** fails 12–35% of the time (milder ones end up SUSPECT); usually passes when re-run */
		FLAKY,
		/** green, but broken for a few runs every couple of weeks (a regression that gets fixed) */
		BROKEN_EPISODES,
		/** stable but takes seconds */
		SLOW,
		/** only exists in the newest runs */
		NEW,
		/** disabled */
		SKIPPED
	}

	record DemoTest(int index, String suite, String className, String name, Kind kind, double failProbability,
			long baseDurationMs, int period, int brokenLength, int offset, int introducedAfter, int failureTemplate) {
	}

	private static final long SEED = 20261105L;

	private static final String[] AREAS = { "cart", "checkout", "search", "catalog", "accounts", "orders", "payments",
			"shipping", "inventory", "reviews" };
	private static final String[] CLASS_SUFFIXES = { "ServiceTest", "ControllerTest", "RepositoryTest", "IT", "UiTest" };
	private static final String[] VERBS = { "creates", "updates", "deletes", "lists", "finds", "validates", "rejects",
			"calculates", "applies", "exports", "imports", "sorts", "filters", "paginates", "retries", "caches",
			"notifies", "refunds", "ships", "renders" };
	private static final String[] OBJECTS = { "Item", "Order", "Total", "Coupon", "Address", "Invoice", "Review",
			"Stock", "Session", "Token", "Page", "Report", "Discount", "Payment", "Label" };
	private static final String[] VARIANTS = { "", "WhenEmpty", "ForGuest", "OnTimeout", "InBulk", "WithCache" };

	private final List<DemoTest> tests;

	DemoCatalog(int size) {
		Random random = new Random(SEED);
		List<DemoTest> list = new ArrayList<>(size);
		int perClass = 6;
		for (int i = 0; i < size; i++) {
			int classIndex = i / perClass;
			String area = AREAS[classIndex % AREAS.length];
			String suffix = CLASS_SUFFIXES[(classIndex / AREAS.length) % CLASS_SUFFIXES.length];
			String className = capitalize(area) + suffix;
			String suite = "com.acme.shop." + area + "." + className;
			String name = VERBS[(i * 7 + classIndex) % VERBS.length] + OBJECTS[(i * 3 + classIndex * 5) % OBJECTS.length]
					+ VARIANTS[(i / VERBS.length + classIndex) % VARIANTS.length];
			boolean uiOrIntegration = suffix.equals("IT") || suffix.equals("UiTest");

			double roll = random.nextDouble();
			double flakyShare = uiOrIntegration ? 0.16 : 0.04; // end-to-end tests are flakier
			Kind kind;
			if (roll < flakyShare) {
				kind = Kind.FLAKY;
			}
			else if (roll < flakyShare + 0.03) {
				kind = Kind.BROKEN_EPISODES;
			}
			else if (roll < flakyShare + 0.07) {
				kind = Kind.SLOW;
			}
			else if (roll < flakyShare + 0.10) {
				kind = Kind.NEW;
			}
			else if (roll < flakyShare + 0.11) {
				kind = Kind.SKIPPED;
			}
			else {
				kind = Kind.STABLE;
			}
			long base = 15 + (long) (Math.pow(random.nextDouble(), 2) * 500);
			if (uiOrIntegration) {
				base *= 6;
			}
			if (kind == Kind.SLOW) {
				base = 3_000 + random.nextInt(12_000);
			}
			int period = 40 + random.nextInt(80);
			list.add(new DemoTest(i, suite, className, uniqueName(list, suite, name), kind,
					0.12 + random.nextDouble() * 0.23, base, period, 3 + random.nextInt(9), random.nextInt(period),
					random.nextInt(16), random.nextInt(4)));
		}
		this.tests = List.copyOf(list);
	}

	List<DemoTest> tests() {
		return tests;
	}

	/**
	 * Results of one CI run.
	 *
	 * @param slot      the run's time slot (runs are numbered by time, e.g. one slot per 6 hours)
	 * @param firstSlot slot of the demo's first run (NEW tests appear in the last few runs before "now" at creation)
	 * @param newSince  slot from which NEW tests exist
	 */
	List<ParsedTest> run(long slot, long newSince) {
		List<ParsedTest> results = new ArrayList<>(tests.size());
		for (DemoTest t : tests) {
			if (t.kind() == Kind.NEW && slot < newSince + t.introducedAfter() % 8) {
				continue; // not written yet
			}
			Random r = new Random(mix(t.index(), slot));
			double roll = r.nextDouble();
			long duration = Math.max(1, (long) (t.baseDurationMs() * (0.75 + r.nextDouble() * 0.5)));
			results.add(switch (t.kind()) {
				case SKIPPED -> new ParsedTest(t.suite(), t.name(), null, Outcome.SKIPPED, 1, 0, 0, null, null, null);
				case FLAKY -> roll < t.failProbability()
						? failure(t, slot, r, r.nextDouble() < 0.65, duration)
						: pass(t, duration);
				case BROKEN_EPISODES -> (slot + t.offset()) % t.period() < t.brokenLength()
						? brokenFailure(t, slot, duration)
						: pass(t, duration);
				default -> roll < 0.002 ? failure(t, slot, r, r.nextBoolean(), duration) : pass(t, duration);
			});
		}
		return results;
	}

	private static ParsedTest pass(DemoTest t, long duration) {
		return new ParsedTest(t.suite(), t.name(), null, Outcome.PASSED, 1, 0, duration, null, null, null);
	}

	/** A flaky failure; {@code passedOnRetry} = the rerun went green (Surefire's flakyFailure). */
	private static ParsedTest failure(DemoTest t, long slot, Random r, boolean passedOnRetry, long duration) {
		String type;
		String message = switch (t.failureTemplate()) {
			case 0 -> {
				type = "org.openqa.selenium.TimeoutException";
				yield "Timeout %dms exceeded waiting for \"#%s-%d\"".formatted(2000 + r.nextInt(8) * 500,
						t.name().toLowerCase().substring(0, Math.min(8, t.name().length())), r.nextInt(500));
			}
			case 1 -> {
				type = "java.net.ConnectException";
				yield "Connection refused: localhost:%d".formatted(5432 + r.nextInt(40));
			}
			case 2 -> {
				type = "org.opentest4j.AssertionFailedError";
				int expected = 10 + r.nextInt(90);
				yield "expected: <%d> but was: <%d>".formatted(expected, expected - 1 - r.nextInt(3));
			}
			default -> {
				type = "org.springframework.dao.CannotAcquireLockException";
				yield "Deadlock detected while updating order %d".formatted(10_000 + r.nextInt(90_000));
			}
		};
		String stack = stack(t, type, message, 20 + (int) (slot % 40));
		long withTimeout = t.failureTemplate() == 0 ? duration + 2_000 + r.nextInt(3_000) : duration;
		return passedOnRetry
				? new ParsedTest(t.suite(), t.name(), null, Outcome.PASSED_ON_RETRY, 2, 1, withTimeout + duration, type,
						message, stack)
				: new ParsedTest(t.suite(), t.name(), null, Outcome.FAILED, 2, 2, withTimeout * 2, type, message, stack);
	}

	/** A real regression: same failure every run while the episode lasts. */
	private static ParsedTest brokenFailure(DemoTest t, long slot, long duration) {
		String type = "java.lang.AssertionError";
		String message = "expected status <201> but was <500> for order %d".formatted(1_000 + slot % 997);
		return new ParsedTest(t.suite(), t.name(), null, Outcome.FAILED, 2, 2, duration * 2, type, message,
				stack(t, type, message, 57));
	}

	private static String stack(DemoTest t, String type, String message, int line) {
		String method = t.name().replaceAll("[^A-Za-z0-9]", "");
		return type + ": " + message + "\n"
				+ "\tat com.acme.shop.support.Waits.until(Waits.java:" + (12 + line % 5) + ")\n"
				+ "\tat " + t.suite() + "." + method + "(" + t.className() + ".java:" + line + ")\n"
				+ "\tat java.base/java.lang.reflect.Method.invoke(Method.java:580)\n"
				+ "\tat org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:767)";
	}

	/** Commit of a run: a fake 40-hex SHA; every 5th run re-runs the previous commit (a "retry the build"). */
	static String commitFor(long slot) {
		long base = slot % 5 == 0 ? slot - 1 : slot;
		long a = mix(-1, base);
		long b = mix(-2, base);
		return String.format("%016x%016x%08x", a, b, (int) (a ^ b));
	}

	static String branchFor(long slot) {
		return slot % 7 == 3 ? "feature/checkout-redesign" : "main";
	}

	private static long mix(long testIndex, long slot) {
		long h = SEED ^ (testIndex * 0x9E3779B97F4A7C15L) ^ (slot * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 33;
		h *= 0xff51afd7ed558ccdL;
		h ^= h >>> 33;
		return h;
	}

	private static String uniqueName(List<DemoTest> existing, String suite, String name) {
		String candidate = name;
		int n = 2;
		while (true) {
			String c = candidate;
			if (existing.stream().noneMatch(t -> t.suite().equals(suite) && t.name().equals(c))) {
				return candidate;
			}
			candidate = name + n++;
		}
	}

	private static String capitalize(String s) {
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

}
