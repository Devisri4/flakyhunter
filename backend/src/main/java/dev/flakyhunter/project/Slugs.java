package dev.flakyhunter.project;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Project slugs: 3–40 chars of a–z, 0–9 and inner hyphens, e.g. "acme-shop". */
final class Slugs {

	static final Pattern VALID = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,38})[a-z0-9]$");

	private Slugs() {
	}

	static String fromName(String name) {
		String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String slug = ascii.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-+|-+$", "");
		if (slug.length() > 40) {
			slug = slug.substring(0, 40).replaceAll("-+$", "");
		}
		return slug;
	}

	/** Names users can't take: the public demo lives at /p/demo, the rest could clash with future pages. */
	static final Set<String> RESERVED = Set.of("demo", "new", "admin", "api", "settings", "projects", "login",
			"signup", "setup");

	static boolean isValid(String slug) {
		return slug != null && VALID.matcher(slug).matches() && !slug.contains("--");
	}

	static boolean isReserved(String slug) {
		return RESERVED.contains(slug);
	}

}
