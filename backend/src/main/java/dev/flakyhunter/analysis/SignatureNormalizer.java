package dev.flakyhunter.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.flakyhunter.common.Tokens;
import org.springframework.stereotype.Component;

/**
 * Groups failures by root cause: exception type + normalised first message line + the top 3 stack frames
 * from the project's own code (framework and JDK frames skipped, line numbers dropped), hashed with SHA-256.
 *
 * <p>{@code TimeoutError: locator.click: Timeout 5000ms exceeded waiting for "#submit-42"} and the same with
 * 3000ms / "#submit-7" both become {@code locator.click: Timeout <dur> exceeded waiting for <str>}.
 */
@Component
public class SignatureNormalizer {

	public record Signature(String hash, String exceptionType, String normalizedMessage, List<String> frames) {
	}

	private static final int MAX_FRAMES = 3;
	private static final int MAX_MESSAGE = 500;

	private static final Pattern UUID = Pattern
			.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
	private static final Pattern HEX = Pattern.compile("\\b0x[0-9a-fA-F]+\\b|\\b(?=[0-9a-fA-F]*\\d)(?=[0-9a-fA-F]*[a-fA-F])[0-9a-fA-F]{8,}\\b");
	private static final Pattern QUOTED = Pattern.compile("\"[^\"]*\"|'[^']*'|`[^`]*`");
	private static final Pattern DURATION = Pattern
			.compile("\\b\\d+(?:\\.\\d+)?\\s?(?:ms|millis|milliseconds|s|sec|secs|seconds|m|min|mins|minutes)\\b");
	/** Any remaining digits, also inside words ("order42"): ids and counts must not split a group. */
	private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
	private static final Pattern SPACES = Pattern.compile("\\s+");

	/** "java.lang.IllegalStateException: boom" or "AssertionError: x" at the start of a line. */
	private static final Pattern TYPE_PREFIX = Pattern.compile("^\\s*([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*(?:Error|Exception|Failure|Throwable))\\s*:?\\s*(.*)$");
	/** A JVM stack frame: "at com.acme.Cart.total(Cart.java:31)". */
	private static final Pattern FRAME = Pattern.compile("^\\s*at\\s+([\\w$.<>/]+)\\(");

	private static final List<String> FRAMEWORK_PREFIXES = List.of("java.", "javax.", "jdk.", "sun.", "com.sun.",
			"jakarta.", "kotlin.", "scala.", "org.junit.", "junit.", "org.opentest4j.", "org.assertj.", "org.hamcrest.",
			"org.mockito.", "org.testng.", "org.springframework.", "org.apache.", "org.gradle.", "worker.org.gradle.",
			"org.openqa.", "io.netty.", "reactor.", "net.bytebuddy.", "org.awaitility.", "io.restassured.",
			"groovy.", "org.codehaus.groovy.");

	public Signature normalize(String failureType, String message, String stack) {
		String firstLine = firstNonBlankLine(message != null ? message : stack);
		String type = failureType == null || failureType.isBlank() ? null : failureType.strip();
		String text = firstLine == null ? "" : firstLine;

		// "SomeException: the message" => take the type from the text when the report didn't name it,
		// and drop it from the message so "type" and "message" don't repeat each other
		Matcher prefix = TYPE_PREFIX.matcher(text);
		if (prefix.matches()) {
			if (type == null) {
				type = prefix.group(1);
			}
			if (type.equals(prefix.group(1)) || type.endsWith("." + prefix.group(1))) {
				text = prefix.group(2);
			}
		}
		if (type == null) {
			type = "Failure";
		}

		String normalized = normalizeText(text);
		List<String> frames = ownFrames(stack);
		String hash = Tokens.sha256Hex(type + "\n" + normalized + "\n" + String.join("\n", frames));
		return new Signature(hash, type, normalized.isEmpty() ? "(no message)" : normalized, frames);
	}

	static String normalizeText(String text) {
		String s = UUID.matcher(text).replaceAll("<id>");
		s = HEX.matcher(s).replaceAll("<id>");
		s = QUOTED.matcher(s).replaceAll("<str>");
		s = DURATION.matcher(s).replaceAll("<dur>");
		s = NUMBER.matcher(s).replaceAll("<n>");
		s = SPACES.matcher(s).replaceAll(" ").strip();
		return s.length() <= MAX_MESSAGE ? s : s.substring(0, MAX_MESSAGE);
	}

	/** The first frames from the project's own code, without line numbers (so edits don't split groups). */
	static List<String> ownFrames(String stack) {
		List<String> frames = new ArrayList<>();
		if (stack == null) {
			return frames;
		}
		for (String line : stack.split("\\R")) {
			Matcher m = FRAME.matcher(line);
			if (m.find()) {
				String method = m.group(1);
				if (FRAMEWORK_PREFIXES.stream().noneMatch(method::startsWith)) {
					frames.add(method);
					if (frames.size() == MAX_FRAMES) {
						break;
					}
				}
			}
		}
		return frames;
	}

	private static String firstNonBlankLine(String text) {
		if (text == null) {
			return null;
		}
		return text.lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse(null);
	}

}
