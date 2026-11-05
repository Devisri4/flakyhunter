package dev.flakyhunter.parsing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.flakyhunter.parsing.ParsedTest.Outcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaywrightJsonParserTest {

	private final PlaywrightJsonParser parser = new PlaywrightJsonParser();

	@Test
	void readsAttemptsSuitesAndProjects() throws IOException {
		Map<String, ParsedTest> tests = parse(fixture());
		assertThat(tests).containsOnlyKeys("adds an item [chromium]", "adds an item [firefox]",
				"applies a coupon [chromium]", "finds a product [chromium]", "exports results [chromium]");

		ParsedTest green = tests.get("adds an item [chromium]");
		assertThat(green.suite()).isEqualTo("checkout.spec.ts › Cart"); // file + describe block
		assertThat(green.file()).isEqualTo("checkout.spec.ts");
		assertThat(green.outcome()).isEqualTo(Outcome.PASSED);
		assertThat(green.durationMs()).isEqualTo(812);

		ParsedTest flaky = tests.get("adds an item [firefox]");
		assertThat(flaky.outcome()).isEqualTo(Outcome.PASSED_ON_RETRY);
		assertThat(flaky.attempts()).isEqualTo(2);
		assertThat(flaky.failedAttempts()).isEqualTo(1);
		assertThat(flaky.durationMs()).isEqualTo(5912);
		// colour codes removed, first line only, type taken from the message
		assertThat(flaky.message()).isEqualTo("TimeoutError: locator.click: Timeout 5000ms exceeded.");
		assertThat(flaky.failureType()).isEqualTo("TimeoutError");
		assertThat(flaky.stack()).contains("checkout.spec.ts:14:22");

		ParsedTest broken = tests.get("applies a coupon [chromium]");
		assertThat(broken.outcome()).isEqualTo(Outcome.FAILED);
		assertThat(broken.attempts()).isEqualTo(2);
		assertThat(broken.failedAttempts()).isEqualTo(2);
		assertThat(broken.message()).isEqualTo("Error: expect(received).toBe(expected)");
		assertThat(broken.failureType()).isEqualTo("Error");

		ParsedTest timeout = tests.get("finds a product [chromium]");
		assertThat(timeout.suite()).isEqualTo("search.spec.ts");
		assertThat(timeout.outcome()).isEqualTo(Outcome.FAILED);
		assertThat(timeout.failureType()).isEqualTo("TimeoutError");

		assertThat(tests.get("exports results [chromium]").outcome()).isEqualTo(Outcome.SKIPPED);
	}

	@Test
	void withOneProjectTheNameHasNoSuffix() {
		String json = """
				{"config":{"projects":[{"name":"chromium"}]},"suites":[{"title":"a.spec.ts","file":"a.spec.ts",
				 "specs":[{"title":"works","tests":[{"projectName":"chromium","results":[{"status":"passed","duration":5}]}]}]}]}""";
		assertThat(parse(json)).containsOnlyKeys("works");
	}

	@Test
	void brokenJsonIsAClearError() {
		assertThatThrownBy(() -> parse("{\"suites\": [ {\"title\": "))
				.isInstanceOf(ReportParseException.class)
				.hasMessageStartingWith("Malformed JSON");
		assertThatThrownBy(() -> parse("{\"hello\": 1}"))
				.isInstanceOf(ReportParseException.class)
				.hasMessageContaining("Not a Playwright JSON report");
	}

	@Test
	void autoDetectionPicksTheParserByTheFirstCharacter() throws IOException {
		assertThat(ReportParsers.detect("  \n{\"suites\":[]}".getBytes())).isEqualTo(ReportParsers.Format.PLAYWRIGHT);
		assertThat(ReportParsers.detect("<?xml version='1.0'?><testsuite/>".getBytes())).isEqualTo(ReportParsers.Format.JUNIT);
		byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '{' };
		assertThat(ReportParsers.detect(bom)).isEqualTo(ReportParsers.Format.PLAYWRIGHT);

		ReportParsers parsers = new ReportParsers(new JUnitXmlParser(), parser);
		byte[] junit = "<testsuite name='s'><testcase classname='c' name='t'/></testsuite>".getBytes();
		List<ParsedTest> both = parsers.parse(ReportParsers.Format.AUTO, List.of(junit, fixture().getBytes()));
		assertThat(both).hasSize(6); // 1 JUnit + 5 Playwright tests in one run
	}

	private Map<String, ParsedTest> parse(String json) {
		InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
		return parser.parse(List.of(in)).stream().collect(Collectors.toMap(ParsedTest::name, Function.identity()));
	}

	private static String fixture() throws IOException {
		try (InputStream in = PlaywrightJsonParserTest.class.getResourceAsStream("/reports/playwright.json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
