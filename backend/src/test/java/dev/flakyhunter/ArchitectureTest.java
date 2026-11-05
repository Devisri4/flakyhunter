package dev.flakyhunter;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** The module rules of ADR-0001, checked on every build (plain unit test: no Spring, no database). */
class ArchitectureTest {

	private static final JavaClasses CLASSES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("dev.flakyhunter");

	@Test
	void controllersNeverTouchRepositoriesOrSqlDirectly() {
		noClasses().that().areAnnotatedWith(RestController.class)
				.should().dependOnClassesThat().areAssignableTo(org.springframework.data.repository.Repository.class)
				.orShould().dependOnClassesThat().areAnnotatedWith(Repository.class)
				.orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient")
				.orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
				.check(CLASSES);
	}

	@Test
	void theParsersKnowNothingAboutTheRestOfTheApp() {
		// parsing turns bytes into ParsedTest; it must not depend on storage, web or other features
		noClasses().that().resideInAPackage("..parsing..")
				.should().dependOnClassesThat().resideInAnyPackage("dev.flakyhunter.catalog..",
						"dev.flakyhunter.ingestion..", "dev.flakyhunter.project..", "dev.flakyhunter.analysis..",
						"org.springframework.jdbc..", "org.springframework.web..")
				.check(CLASSES);
	}

	@Test
	void theScoringModelIsPure() {
		classes().that().haveSimpleNameStartingWith("FlakinessScorer")
				.or().haveSimpleNameStartingWith("SignatureNormalizer")
				.should().onlyDependOnClassesThat().resideOutsideOfPackages("org.springframework.jdbc..",
						"org.springframework.web..", "jakarta.persistence..")
				.check(CLASSES);
	}

	@Test
	void repositoriesStayInsideTheirModule() {
		// JPA repositories are package-private: other modules go through the module's services
		classes().that().areAssignableTo(org.springframework.data.repository.Repository.class)
				.and().resideInAPackage("dev.flakyhunter.project..")
				.should().notBePublic()
				.check(CLASSES);
	}

}
