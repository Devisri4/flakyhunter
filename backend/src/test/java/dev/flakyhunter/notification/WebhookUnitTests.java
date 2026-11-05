package dev.flakyhunter.notification;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.util.List;

import dev.flakyhunter.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests: URL safety (SSRF), signing and message formats. */
class WebhookUnitTests {

	@ParameterizedTest
	@ValueSource(strings = { "127.0.0.1", "10.1.2.3", "172.16.0.9", "192.168.1.1", "169.254.169.254", "0.0.0.0",
			"100.64.0.1", "::1", "fd00::1", "fe80::1" })
	void internalAddressesAreRefused(String ip) throws Exception {
		assertThat(WebhookUrls.isInternal(InetAddress.getByName(ip))).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "8.8.8.8", "1.1.1.1", "2606:4700:4700::1111" })
	void publicAddressesAreAllowed(String ip) throws Exception {
		assertThat(WebhookUrls.isInternal(InetAddress.getByName(ip))).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://hooks.example.com/x", "ftp://example.com", "https://user:pw@example.com/x",
			"https://127.0.0.1/hook", "https://localhost/hook", "not a url" })
	void unsafeUrlsAreRejected(String url) {
		assertThatThrownBy(() -> WebhookUrls.requirePublicHttps(url)).isInstanceOf(ApiException.class);
	}

	@Test
	void signatureIsHmacSha256Hex() {
		// RFC 4231 test case 2
		assertThat(Notifier.hmac("Jefe", "what do ya want for nothing?"))
				.isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
	}

	@Test
	void messagesFitEachService() {
		Notifier notifier = new Notifier(null, "https://flakyhunter.vercel.app/");
		var project = new Notifier.ProjectInfo("acme", "Acme");
		var tests = List.of(new Notifier.FlakyTest(7, "com.acme.CartTest", "addsItem", new BigDecimal("0.420")));

		String slack = notifier.message("SLACK", project, 99, tests, 3);
		assertThat(slack).startsWith("{\"text\":").contains("3 tests became flaky in Acme")
				.contains("https://flakyhunter.vercel.app/p/acme/tests/7").contains("and 2 more");
		assertThat(notifier.message("DISCORD", project, 99, tests, 1)).startsWith("{\"content\":");
		assertThat(notifier.message("GENERIC", project, 99, tests, 1))
				.contains("\"event\":\"tests.became_flaky\"")
				.contains("\"runUrl\":\"https://flakyhunter.vercel.app/p/acme/runs/99\"");
	}

}
