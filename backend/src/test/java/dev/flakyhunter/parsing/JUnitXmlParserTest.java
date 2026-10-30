package dev.flakyhunter.parsing;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.flakyhunter.parsing.ParsedTest.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JUnitXmlParserTest {

	private final JUnitXmlParser parser = new JUnitXmlParser();

	@Test
	void surefireBasic() {
		Map<String, ParsedTest> tests = parse("surefire-basic.xml");
		assertThat(tests).hasSize(4);

		ParsedTest pass = tests.get("addsItem");
		assertThat(pass.suite()).isEqualTo("com.acme.shop.CartTest");
		assertThat(pass.outcome()).isEqualTo(Outcome.PASSED);
		assertThat(pass.durationMs()).isEqualTo(12);
		assertThat(pass.message()).isNull();

		ParsedTest failure = tests.get("computesTotal");
		assertThat(failure.outcome()).isEqualTo(Outcome.FAILED);
		assertThat(failure.attempts()).isEqualTo(1);
		assertThat(failure.failureType()).isEqualTo("org.opentest4j.AssertionFailedError");
		assertThat(failure.message()).isEqualTo("expected: <42> but was: <41>");
		assertThat(failure.stack()).contains("CartTest.java:31").doesNotContain("computing total");

		assertThat(tests.get("appliesCoupon").outcome()).isEqualTo(Outcome.SKIPPED);
		assertThat(tests.get("checksOut").outcome()).isEqualTo(Outcome.FAILED);
		assertThat(tests.get("checksOut").failureType()).isEqualTo("java.net.ConnectException");
	}

	@Test
	void surefireRerunsAreCountedAsAttempts() {
		Map<String, ParsedTest> tests = parse("surefire-reruns.xml");

		ParsedTest flaky = tests.get("paysWithCard");
		assertThat(flaky.outcome()).isEqualTo(Outcome.PASSED_ON_RETRY);
		assertThat(flaky.attempts()).isEqualTo(2);
		assertThat(flaky.failedAttempts()).isEqualTo(1);
		assertThat(flaky.message()).isEqualTo("Timeout 5000ms exceeded");
		assertThat(flaky.stack()).contains("CheckoutIT.java:40").doesNotContain("attempt 1");

		ParsedTest broken = tests.get("refunds");
		assertThat(broken.outcome()).isEqualTo(Outcome.FAILED);
		assertThat(broken.attempts()).isEqualTo(3);
		assertThat(broken.failedAttempts()).isEqualTo(3);

		assertThat(tests.get("showsReceipt").outcome()).isEqualTo(Outcome.PASSED);
	}

	@Test
	void gradleReport() {
		Map<String, ParsedTest> tests = parse("gradle.xml");
		assertThat(tests).containsOnlyKeys("listsOrders()", "[1] status=PAID", "createsOrder()");
		assertThat(tests.get("createsOrder()").outcome()).isEqualTo(Outcome.FAILED);
		assertThat(tests.get("createsOrder()").message()).contains("expected:<201> but was:<500>");
	}

	@Test
	void pytestReport() {
		Map<String, ParsedTest> tests = parse("pytest.xml");
		assertThat(tests.get("test_add_item").suite()).isEqualTo("tests.test_cart");
		assertThat(tests.get("test_add_item").file()).isEqualTo("tests/test_cart.py");
		assertThat(tests.get("test_total[EUR]").outcome()).isEqualTo(Outcome.FAILED);
		assertThat(tests.get("test_total[EUR]").message()).isEqualTo("assert 41 == 42");
		assertThat(tests.get("test_coupon").outcome()).isEqualTo(Outcome.SKIPPED);
	}

	@Test
	void sameTestTwiceInOneRunIsMergedIntoARetry() {
		Map<String, ParsedTest> tests = parse("nested-suites.xml");
		assertThat(tests).hasSize(1);
		ParsedTest merged = tests.get("shows the form");
		assertThat(merged.suite()).isEqualTo("login page"); // no classname: the innermost suite is used
		assertThat(merged.outcome()).isEqualTo(Outcome.PASSED_ON_RETRY);
		assertThat(merged.attempts()).isEqualTo(2);
		assertThat(merged.failedAttempts()).isEqualTo(1);
		assertThat(merged.durationMs()).isEqualTo(1200);
	}

	@Test
	void severalFilesFormOneRun() {
		List<ParsedTest> tests = parser.parse(List.of(resource("surefire-basic.xml"), resource("gradle.xml")));
		assertThat(tests).hasSize(7);
	}

	@Test
	void malformedXmlIsAClearError() {
		assertThatThrownBy(() -> parse("malformed.xml"))
				.isInstanceOf(ReportParseException.class)
				.hasMessageStartingWith("Malformed XML");
	}

	@Test
	void notAJUnitReport() {
		assertThatThrownBy(() -> parse("not-junit.xml"))
				.isInstanceOf(ReportParseException.class)
				.hasMessageContaining("root element is <html>");
	}

	@Test
	void xxeIsRejected() {
		assertThatThrownBy(() -> parse("xxe.xml")).isInstanceOf(ReportParseException.class);
	}

	@Test
	void emptyFile() {
		assertThatThrownBy(() -> parser.parse(List.of(new ByteArrayInputStream(new byte[0]))))
				.isInstanceOf(ReportParseException.class);
	}

	@Test
	void longMessagesAreTruncated() {
		String xml = "<testsuite name='s'><testcase name='t' classname='c'><failure message='%s'>%s</failure></testcase></testsuite>"
				.formatted("m".repeat(5000), "s".repeat(20_000));
		ParsedTest t = parser.parse(List.of(stream(xml))).getFirst();
		assertThat(t.message()).hasSize(ParsedTest.MAX_MESSAGE);
		assertThat(t.stack()).hasSize(ParsedTest.MAX_STACK);
	}

	@Test
	void tooManyTestCases() {
		StringBuilder xml = new StringBuilder("<testsuite name='big'>");
		for (int i = 0; i <= JUnitXmlParser.MAX_TESTS; i++) {
			xml.append("<testcase classname='c' name='t").append(i).append("'/>");
		}
		xml.append("</testsuite>");
		assertThatThrownBy(() -> parser.parse(List.of(stream(xml.toString()))))
				.isInstanceOf(ReportParseException.class)
				.hasMessageContaining("at most");
	}

	@ParameterizedTest
	@CsvSource({ "0.012, 12", "1.5, 1500", "'1,234.5', 1234500", "'', 0", "abc, 0", "-1, 0" })
	void seconds(String time, long expectedMs) {
		assertThat(JUnitXmlParser.seconds(time)).isEqualTo(expectedMs);
	}

	private Map<String, ParsedTest> parse(String fixture) {
		return parser.parse(List.of(resource(fixture))).stream()
				.collect(Collectors.toMap(ParsedTest::name, Function.identity()));
	}

	private static InputStream resource(String name) {
		InputStream in = JUnitXmlParserTest.class.getResourceAsStream("/reports/" + name);
		assertThat(in).as("fixture " + name).isNotNull();
		return in;
	}

	private static InputStream stream(String xml) {
		return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
	}

}
