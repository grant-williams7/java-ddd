package com.example.marketplace;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Enforces the layer table in docs/reference/architecture.md. Modulith checks
 * the dependencies between layers; the ArchUnit rules check which libraries
 * the two inner layers may touch, which Modulith doesn't look at.
 */
class ModuleStructureTest {

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example.marketplace");

    @Test
    void layersDependOnlyOnWhatTheArchitectureTableAllows() {
        ApplicationModules.of(MarketplaceApplication.class).verify();
    }

    @Test
    void domainUsesOnlyTheJdkAndTheUuidGenerator() {
        classes().that().resideInAPackage("com.example.marketplace.domain..")
                .and().doNotHaveSimpleName("package-info")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java..",
                        "com.fasterxml.uuid..",
                        "com.example.marketplace.domain..")
                .check(MAIN_CLASSES);
    }

    @Test
    void applicationUsesOnlyTheJdkLoggingAndTheDomain() {
        classes().that().resideInAPackage("com.example.marketplace.application..")
                .and().doNotHaveSimpleName("package-info")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java..",
                        "org.slf4j..",
                        "com.example.marketplace.domain..",
                        "com.example.marketplace.application..")
                .check(MAIN_CLASSES);
    }
}
