package dev.flakyhunter.project;

import dev.flakyhunter.common.Tokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit tests: no Spring, no database, so they also run on the laptop. */
class ProjectUnitTests {

	@ParameterizedTest
	@CsvSource({
			"Acme Shop, acme-shop",
			"  My  Cool--Project!! , my-cool-project",
			"Café Crème, cafe-creme",
			"API v2.0, api-v2-0" })
	void slugFromName(String name, String expected) {
		assertThat(Slugs.fromName(name)).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "acme-shop", "a1-b2-c3", "x23456789012345678901234567890123456789y" })
	void validSlugs(String slug) {
		assertThat(Slugs.isValid(slug)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "ab", "-abc", "abc-", "Acme", "a b c", "a--b", "x234567890123456789012345678901234567890y" })
	void invalidSlugs(String slug) {
		assertThat(Slugs.isValid(slug)).isFalse();
	}

	@Test
	void longNamesAreCutTo40Characters() {
		assertThat(Slugs.fromName("a".repeat(39) + " bbbb")).hasSize(39).isEqualTo("a".repeat(39));
	}

	@Test
	void rolesAreOrdered() {
		assertThat(Role.OWNER.atLeast(Role.MEMBER)).isTrue();
		assertThat(Role.MEMBER.atLeast(Role.MEMBER)).isTrue();
		assertThat(Role.VIEWER.atLeast(Role.MEMBER)).isFalse();
	}

	@Test
	void apiKeysHaveThePrefixAndAreUnique() {
		String a = ApiKeyService.newKey();
		String b = ApiKeyService.newKey();
		assertThat(a).startsWith("fh_").hasSize(46).isNotEqualTo(b);
	}

	@Test
	void sha256IsHexOfTheStandardDigest() {
		assertThat(Tokens.sha256Hex("abc"))
				.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

}
