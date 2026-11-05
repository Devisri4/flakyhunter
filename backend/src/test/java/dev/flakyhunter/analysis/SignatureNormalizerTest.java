package dev.flakyhunter.analysis;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import dev.flakyhunter.analysis.SignatureNormalizer.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureNormalizerTest {

	private final SignatureNormalizer normalizer = new SignatureNormalizer();

	@Test
	void theExampleFromThePlanGroupsTogether() {
		Signature a = normalizer.normalize(null,
				"TimeoutError: locator.click: Timeout 5000ms exceeded waiting for \"#submit-42\"", null);
		Signature b = normalizer.normalize(null,
				"TimeoutError: locator.click: Timeout 3000ms exceeded waiting for \"#submit-7\"", null);
		assertThat(a.hash()).isEqualTo(b.hash());
		assertThat(a.exceptionType()).isEqualTo("TimeoutError");
		assertThat(a.normalizedMessage()).isEqualTo("locator.click: Timeout <dur> exceeded waiting for <str>");
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"expected: <42> but was: <41>                      | expected: <<n>> but was: <<n>>",
			"Order 8f14e45f-ceea-467a-9575-6f1f0e5d0c9a missing | Order <id> missing",
			"pointer 0x7ffe12ab is null                         | pointer <id> is null",
			"retried after 1.5 s                                | retried after <dur>",
			"user 'alice' not found                             | user <str> not found",
			"lots   of    space                                 | lots of space",
	})
	void normalizesVariableParts(String input, String expected) {
		assertThat(SignatureNormalizer.normalizeText(input.strip())).isEqualTo(expected);
	}

	@Test
	void typeFromTheReportWinsAndIsNotRepeatedInTheMessage() {
		Signature s = normalizer.normalize("org.opentest4j.AssertionFailedError",
				"org.opentest4j.AssertionFailedError: expected: <1> but was: <2>", null);
		assertThat(s.exceptionType()).isEqualTo("org.opentest4j.AssertionFailedError");
		assertThat(s.normalizedMessage()).isEqualTo("expected: <<n>> but was: <<n>>");
	}

	@Test
	void ownFramesAreKeptWithoutLineNumbersAndFrameworkFramesSkipped() {
		String stack = """
				java.lang.IllegalStateException: boom
					at java.base/java.util.Objects.requireNonNull(Objects.java:259)
					at com.acme.shop.Cart.total(Cart.java:31)
					at com.acme.shop.Cart.checkout(Cart.java:58)
					at org.junit.jupiter.engine.execution.MethodInvocation.proceed(MethodInvocation.java:60)
					at com.acme.shop.CartTest.computesTotal(CartTest.java:12)
					at com.acme.shop.Extra.notIncluded(Extra.java:1)
				""";
		assertThat(SignatureNormalizer.ownFrames(stack))
				.containsExactly("com.acme.shop.Cart.total", "com.acme.shop.Cart.checkout", "com.acme.shop.CartTest.computesTotal");

		Signature before = normalizer.normalize("java.lang.IllegalStateException", "boom", stack);
		Signature afterEditingTheFile = normalizer.normalize("java.lang.IllegalStateException", "boom",
				stack.replace("Cart.java:31", "Cart.java:35"));
		assertThat(afterEditingTheFile.hash()).isEqualTo(before.hash());
	}

	@Test
	void differentRootCausesStayApart() {
		Signature npe = normalizer.normalize("java.lang.NullPointerException", "boom", null);
		Signature ise = normalizer.normalize("java.lang.IllegalStateException", "boom", null);
		Signature otherMessage = normalizer.normalize("java.lang.IllegalStateException", "bang", null);
		assertThat(Set.of(npe.hash(), ise.hash(), otherMessage.hash())).hasSize(3);
	}

	@Test
	void noMessageAtAll() {
		Signature s = normalizer.normalize(null, null, null);
		assertThat(s.exceptionType()).isEqualTo("Failure");
		assertThat(s.normalizedMessage()).isEqualTo("(no message)");
	}

	/** Property-style: whatever ids, numbers, durations or quoted values appear, the group is the same. */
	@Test
	void variablePartsNeverSplitAGroup() {
		Random random = new Random(42);
		Set<String> hashes = new HashSet<>();
		for (int i = 0; i < 500; i++) {
			String message = "Timeout %dms waiting for order %s (attempt %d) on '%s' at 0x%x".formatted(
					random.nextInt(100_000), UUID.randomUUID(), random.nextInt(10), "host-" + random.nextInt(999),
					0x10000000 + random.nextInt(0x0fffffff));
			hashes.add(normalizer.normalize("org.openqa.selenium.TimeoutException", message,
					"\tat com.acme.ui.LoginPage.submit(LoginPage.java:" + random.nextInt(500) + ")").hash());
		}
		assertThat(hashes).hasSize(1);
	}

}
