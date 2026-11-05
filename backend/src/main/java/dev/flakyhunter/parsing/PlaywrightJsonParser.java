package dev.flakyhunter.parsing;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import dev.flakyhunter.parsing.ParsedTest.Outcome;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parser for Playwright's JSON reporter ({@code --reporter=json}). The report nests
 * {@code suites → (suites…) → specs → tests → results[]}; each entry in {@code results} is one attempt, so retries
 * are exact: failed first and passed later ⇒ PASSED_ON_RETRY.
 *
 * <p>Identity: suite = file + describe blocks ("checkout.spec.ts › Cart"), name = spec title, plus
 * " [project]" when the report has several Playwright projects (e.g. chromium/firefox).
 */
@Component
public class PlaywrightJsonParser {

	private static final Pattern ANSI = Pattern.compile("\u001B\\[[;\\d]*m");

	private final ObjectMapper mapper = new ObjectMapper();

	public List<ParsedTest> parse(List<InputStream> files) {
		Map<String, ParsedTest> byKey = new LinkedHashMap<>();
		for (InputStream file : files) {
			JsonNode root;
			try {
				root = mapper.readTree(file);
			}
			catch (JacksonException e) {
				throw new ReportParseException("Malformed JSON: " + e.getOriginalMessage(), e);
			}
			if (root == null || !root.path("suites").isArray()) {
				throw new ReportParseException("Not a Playwright JSON report: no \"suites\" array");
			}
			boolean multiProject = root.path("config").path("projects").size() > 1;
			for (JsonNode suite : root.path("suites").values()) {
				walk(suite, new ArrayList<>(), multiProject, byKey);
			}
		}
		return List.copyOf(byKey.values());
	}

	private void walk(JsonNode suite, List<String> path, boolean multiProject, Map<String, ParsedTest> byKey) {
		List<String> here = new ArrayList<>(path);
		String title = suite.path("title").asString("");
		if (!title.isBlank()) {
			here.add(title.strip());
		}
		for (JsonNode spec : suite.path("specs").values()) {
			for (JsonNode test : spec.path("tests").values()) {
				String name = spec.path("title").asString("").strip();
				String project = test.path("projectName").asString("");
				if (multiProject && !project.isBlank()) {
					name += " [" + project + "]";
				}
				if (name.isEmpty()) {
					throw new ReportParseException("A spec has no title");
				}
				String suiteName = here.isEmpty() ? spec.path("file").asString("") : String.join(" › ", here);
				ParsedTest parsed = toTest(suiteName, name, spec.path("file").asString(null), test);
				String key = suiteName + "::" + name;
				ParsedTest existing = byKey.get(key);
				if (existing == null && byKey.size() >= JUnitXmlParser.MAX_TESTS) {
					throw new ReportParseException("A run may contain at most " + JUnitXmlParser.MAX_TESTS + " test cases");
				}
				byKey.put(key, existing == null ? parsed : existing.mergeWith(parsed));
			}
		}
		for (JsonNode child : suite.path("suites").values()) {
			walk(child, here, multiProject, byKey);
		}
	}

	private static ParsedTest toTest(String suite, String name, String file, JsonNode test) {
		List<JsonNode> results = new ArrayList<>(test.path("results").values());
		if (results.isEmpty() || results.stream().allMatch(r -> r.path("status").asString("").equals("skipped"))) {
			return new ParsedTest(suite, name, file, Outcome.SKIPPED, 1, 0, 0, null, null, null);
		}
		int failed = 0;
		long duration = 0;
		JsonNode firstFailure = null;
		for (JsonNode r : results) {
			duration += Math.max(0, r.path("duration").asLong(0));
			if (failed(r)) {
				failed++;
				if (firstFailure == null) {
					firstFailure = r;
				}
			}
		}
		boolean lastPassed = !failed(results.getLast()) && !results.getLast().path("status").asString("").equals("skipped");
		Outcome outcome = failed == 0 ? Outcome.PASSED : lastPassed ? Outcome.PASSED_ON_RETRY : Outcome.FAILED;

		String type = null;
		String message = null;
		String stack = null;
		if (firstFailure != null) {
			JsonNode error = firstFailure.path("error");
			if (error.isMissingNode() && firstFailure.path("errors").size() > 0) {
				error = firstFailure.path("errors").get(0);
			}
			message = clean(error.path("message").asString(null));
			stack = clean(error.path("stack").asString(null));
			type = firstFailure.path("status").asString("").equals("timedOut") ? "TimeoutError" : errorType(message);
			if (message != null) {
				message = message.lines().findFirst().orElse(message);
			}
		}
		return new ParsedTest(suite, name, file, outcome, results.size(), failed, duration, type, message, stack);
	}

	private static boolean failed(JsonNode result) {
		String status = result.path("status").asString("");
		return status.equals("failed") || status.equals("timedOut") || status.equals("interrupted");
	}

	/** "Error: expect(received)…" → "Error"; Playwright doesn't report the type separately. */
	private static String errorType(String message) {
		if (message == null) {
			return null;
		}
		int colon = message.indexOf(':');
		String head = colon > 0 ? message.substring(0, colon) : "";
		return head.matches("[A-Za-z_$][\\w$.]*(Error|Exception)") ? head : "Error";
	}

	/** Playwright messages carry terminal colour codes. */
	private static String clean(String text) {
		return text == null ? null : ANSI.matcher(text).replaceAll("");
	}

}
