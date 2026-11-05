package dev.flakyhunter.junit5;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The project's quarantined tests, keyed by fingerprint = SHA-256 of {@code suite + "::" + name} — the same
 * identity FlakyHunter uses for uploaded JUnit reports (suite = class name, name = method name; Gradle reports
 * add "()" and display names may be used, so all three are tried).
 */
final class QuarantineList {

	record Entry(String fingerprint, String reason, String expiresAt) {
	}

	private static final Logger log = Logger.getLogger(QuarantineList.class.getName());
	private static final Duration TIMEOUT = Duration.ofSeconds(5);
	/** One object of the JSON array; the API's fields are flat strings, so a small regex is enough (no JSON library). */
	private static final Pattern OBJECT = Pattern.compile("\\{[^{}]*}");
	private static final Pattern FIELD = Pattern.compile("\"(fingerprint|reason|expiresAt)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

	static final QuarantineList EMPTY = new QuarantineList(Map.of());

	private final Map<String, Entry> byFingerprint;

	QuarantineList(Map<String, Entry> byFingerprint) {
		this.byFingerprint = byFingerprint;
	}

	int size() {
		return byFingerprint.size();
	}

	Optional<Entry> find(String className, String methodName, String displayName) {
		for (String name : new String[] { methodName, methodName + "()", displayName }) {
			Entry e = byFingerprint.get(fingerprint(className, name));
			if (e != null) {
				return Optional.of(e);
			}
		}
		return Optional.empty();
	}

	static QuarantineList fetch(String baseUrl, String apiKey) {
		if (apiKey == null) {
			log.info("FlakyHunter: FLAKYHUNTER_API_KEY not set, quarantine not applied");
			return EMPTY;
		}
		try {
			HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
			HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + "/api/v1/quarantine"))
					.timeout(TIMEOUT)
					.header("X-Api-Key", apiKey)
					.header("Accept", "application/json")
					.GET()
					.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				log.warning("FlakyHunter: quarantine list unavailable (HTTP " + response.statusCode() + "), running all tests");
				return EMPTY;
			}
			QuarantineList list = parse(response.body());
			log.info("FlakyHunter: " + list.size() + " quarantined test(s) will be skipped");
			return list;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return EMPTY;
		}
		catch (Exception e) {
			log.warning("FlakyHunter: could not load the quarantine list (" + e + "), running all tests");
			return EMPTY;
		}
	}

	static QuarantineList parse(String json) {
		Map<String, Entry> entries = new HashMap<>();
		Matcher object = OBJECT.matcher(json);
		while (object.find()) {
			Map<String, String> fields = new HashMap<>();
			Matcher field = FIELD.matcher(object.group());
			while (field.find()) {
				fields.put(field.group(1), unescape(field.group(2)));
			}
			String fp = fields.get("fingerprint");
			if (fp != null) {
				entries.put(fp, new Entry(fp, fields.get("reason"), fields.get("expiresAt")));
			}
		}
		return new QuarantineList(entries);
	}

	static String fingerprint(String suite, String name) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest((suite.strip() + "::" + name.strip()).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String unescape(String s) {
		return s.replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\");
	}

}
