package dev.flakyhunter.parsing;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import dev.flakyhunter.parsing.ParsedTest.Outcome;
import org.springframework.stereotype.Component;

/**
 * Streaming (StAX) parser for JUnit XML: Maven Surefire (incl. rerun/flaky elements), Gradle and pytest.
 * DTDs and external entities are disabled, so XXE attacks are impossible.
 */
@Component
public class JUnitXmlParser {

	public static final int MAX_TESTS = 50_000;

	private final XMLInputFactory factory;

	public JUnitXmlParser() {
		factory = XMLInputFactory.newFactory();
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		factory.setProperty(XMLInputFactory.IS_COALESCING, true);
	}

	/** Parses several report files of one run; tests reported more than once are merged. */
	public List<ParsedTest> parse(List<InputStream> files) {
		Map<String, ParsedTest> byKey = new LinkedHashMap<>();
		for (InputStream file : files) {
			parseOne(file, byKey);
		}
		return List.copyOf(byKey.values());
	}

	private void parseOne(InputStream in, Map<String, ParsedTest> byKey) {
		XMLStreamReader xml = null;
		try {
			xml = factory.createXMLStreamReader(in);
			Deque<String> suites = new ArrayDeque<>();
			boolean sawRoot = false;
			while (xml.hasNext()) {
				int event = xml.next();
				if (event == XMLStreamConstants.DTD) {
					throw new ReportParseException("Reports with a DOCTYPE are not accepted");
				}
				if (event == XMLStreamConstants.START_ELEMENT) {
					String element = xml.getLocalName();
					if (!sawRoot) {
						sawRoot = true;
						if (!element.equals("testsuites") && !element.equals("testsuite")) {
							throw new ReportParseException(
									"Not a JUnit XML report: root element is <" + element + ">");
						}
					}
					switch (element) {
						case "testsuite" -> suites.push(attr(xml, "name", ""));
						case "testcase" -> add(byKey, readTestCase(xml, suites.isEmpty() ? "" : suites.peek()));
						default -> {
							// <properties>, <system-out>, ... are not needed
						}
					}
				}
				else if (event == XMLStreamConstants.END_ELEMENT && xml.getLocalName().equals("testsuite")) {
					suites.pop();
				}
			}
			if (!sawRoot) {
				throw new ReportParseException("The report file is empty");
			}
		}
		catch (XMLStreamException e) {
			throw new ReportParseException("Malformed XML: " + e.getMessage(), e);
		}
		finally {
			close(xml);
		}
	}

	private static void add(Map<String, ParsedTest> byKey, ParsedTest test) {
		String key = test.suite() + "::" + test.name();
		ParsedTest existing = byKey.get(key);
		if (existing == null && byKey.size() >= MAX_TESTS) {
			throw new ReportParseException("A run may contain at most " + MAX_TESTS + " test cases");
		}
		byKey.put(key, existing == null ? test : existing.mergeWith(test));
	}

	/** Reads one {@code <testcase>} up to its end tag. The reader is positioned on its start tag. */
	private ParsedTest readTestCase(XMLStreamReader xml, String enclosingSuite) throws XMLStreamException {
		String name = attr(xml, "name", "").strip();
		String classname = attr(xml, "classname", "").strip();
		String suite = classname.isEmpty() ? enclosingSuite.strip() : classname;
		String file = attr(xml, "file", null);
		long durationMs = seconds(attr(xml, "time", null));
		if (name.isEmpty()) {
			throw new ReportParseException("A <testcase> has no name");
		}

		boolean skipped = false;
		boolean failed = false;
		int reruns = 0;       // Surefire <rerunFailure>/<rerunError>: failed again on rerun
		int flakyRuns = 0;    // Surefire <flakyFailure>/<flakyError>: failed, then passed on rerun
		String type = null;
		String message = null;
		String stack = null;

		int depth = 1;
		while (depth > 0) {
			int event = xml.next();
			if (event == XMLStreamConstants.START_ELEMENT) {
				depth++;
				String element = xml.getLocalName();
				switch (element) {
					case "skipped" -> skipped = true;
					case "failure", "error", "rerunFailure", "rerunError", "flakyFailure", "flakyError" -> {
						if (element.startsWith("rerun")) {
							reruns++;
						}
						else if (element.startsWith("flaky")) {
							flakyRuns++;
						}
						else {
							failed = true;
						}
						if (message == null) {
							type = attr(xml, "type", null);
							message = attr(xml, "message", null);
							stack = readNestedText(xml);
							depth--; // readNestedText consumed the end tag
						}
					}
					default -> {
					}
				}
			}
			else if (event == XMLStreamConstants.END_ELEMENT) {
				depth--;
			}
		}

		if (message == null && stack != null) {
			message = stack.lines().findFirst().orElse(null);
		}
		if (failed) {
			int attempts = 1 + reruns;
			return new ParsedTest(suite, name, file, Outcome.FAILED, attempts, attempts, durationMs, type, message, stack);
		}
		if (flakyRuns > 0) {
			return new ParsedTest(suite, name, file, Outcome.PASSED_ON_RETRY, 1 + flakyRuns, flakyRuns, durationMs, type,
					message, stack);
		}
		if (skipped) {
			return new ParsedTest(suite, name, file, Outcome.SKIPPED, 1, 0, durationMs, null, null, null);
		}
		return new ParsedTest(suite, name, file, Outcome.PASSED, 1, 0, durationMs, null, null, null);
	}

	/**
	 * Text of the current element (e.g. a stack trace), including nested {@code <stackTrace>} (Surefire's
	 * flaky/rerun elements) but not captured output; stops after the element's end tag.
	 */
	private static String readNestedText(XMLStreamReader xml) throws XMLStreamException {
		StringBuilder text = new StringBuilder();
		int depth = 1;
		int outputDepth = 0; // > 0 while inside <system-out>/<system-err>
		while (depth > 0) {
			int event = xml.next();
			switch (event) {
				case XMLStreamConstants.START_ELEMENT -> {
					depth++;
					String element = xml.getLocalName();
					if (outputDepth > 0 || element.equals("system-out") || element.equals("system-err")) {
						outputDepth++;
					}
				}
				case XMLStreamConstants.END_ELEMENT -> {
					depth--;
					if (outputDepth > 0) {
						outputDepth--;
					}
				}
				case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
					if (outputDepth == 0 && text.length() <= ParsedTest.MAX_STACK) {
						text.append(xml.getText());
					}
				}
				default -> {
				}
			}
		}
		return text.isEmpty() ? null : text.toString();
	}

	private static String attr(XMLStreamReader xml, String name, String fallback) {
		String value = xml.getAttributeValue(null, name);
		return value == null ? fallback : value;
	}

	/** JUnit "time" is seconds as a decimal, sometimes with a thousands separator ("1,234.5"). */
	static long seconds(String time) {
		if (time == null || time.isBlank()) {
			return 0;
		}
		try {
			return new BigDecimal(time.strip().replace(",", "")).movePointRight(3).max(BigDecimal.ZERO).longValue();
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}

	private static void close(XMLStreamReader xml) {
		if (xml != null) {
			try {
				xml.close();
			}
			catch (XMLStreamException ignored) {
				// nothing useful to do
			}
		}
	}

}
