package dev.flakyhunter.junit5;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class FlakyHunterExtensionTest {

	private HttpServer server;
	private final AtomicInteger status = new AtomicInteger(200);
	private final AtomicReference<String> body = new AtomicReference<>("[]");
	private final AtomicReference<String> seenKey = new AtomicReference<>();
	private final AtomicInteger calls = new AtomicInteger();

	@BeforeEach
	void startFakeFlakyHunter() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/v1/quarantine", exchange -> {
			calls.incrementAndGet();
			seenKey.set(exchange.getRequestHeaders().getFirst("X-Api-Key"));
			byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status.get(), bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
		System.setProperty("FLAKYHUNTER_URL", "http://127.0.0.1:" + server.getAddress().getPort());
		System.setProperty("FLAKYHUNTER_API_KEY", "fh_test");
		FlakyHunterExtension.reset();
	}

	@AfterEach
	void stop() {
		server.stop(0);
		System.clearProperty("FLAKYHUNTER_URL");
		System.clearProperty("FLAKYHUNTER_API_KEY");
		FlakyHunterExtension.reset();
	}

	@Test
	void quarantinedTestsAreSkippedWithTheReason() {
		String fp = QuarantineList.fingerprint(QuarantineTarget.class.getName(), "flakyOne");
		body.set("[{\"testId\":1,\"fingerprint\":\"" + fp + "\",\"suite\":\"x\",\"name\":\"flakyOne\","
				+ "\"reason\":\"times out on CI\",\"expiresAt\":\"2026-11-19T10:00:00Z\"}]");

		run().assertStatistics(s -> s.started(1).succeeded(1).skipped(1));
		assertEquals("fh_test", seenKey.get());
		assertEquals(1, calls.get(), "the list is fetched once, not per test");
	}

	@Test
	void ifFlakyHunterIsDownEverythingRuns() {
		status.set(503);
		run().assertStatistics(s -> s.started(2).succeeded(2).skipped(0));
	}

	@Test
	void withoutAnApiKeyNothingIsAsked() {
		System.clearProperty("FLAKYHUNTER_API_KEY");
		FlakyHunterExtension.reset();
		run().assertStatistics(s -> s.started(2).skipped(0));
		assertEquals(0, calls.get());
	}

	@Test
	void parsesTheListWithoutAJsonLibrary() {
		QuarantineList list = QuarantineList.parse("""
				[{"fingerprint":"abc","reason":"a \\"quoted\\" reason","expiresAt":"2026-12-01T00:00:00Z"},
				 {"fingerprint":"def","reason":null}]""");
		assertEquals(2, list.size());
		String fp = QuarantineList.fingerprint("com.acme.CartTest", "addsItem()");
		assertTrue(new QuarantineList(java.util.Map.of(fp, new QuarantineList.Entry(fp, null, null)))
				.find("com.acme.CartTest", "addsItem", "addsItem()").isPresent(), "Gradle-style names match too");
	}

	private Events run() {
		return EngineTestKit.engine("junit-jupiter")
				.selectors(selectClass(QuarantineTarget.class))
				.execute()
				.testEvents();
	}

}
