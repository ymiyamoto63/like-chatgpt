package com.example.chatbackend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.example.chatbackend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule domainMustNotDependOnApplicationOrAdapter = noClasses()
			.that().resideInAPackage("..domain..")
			.should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..");

	@ArchTest
	static final ArchRule applicationMustNotDependOnAdapter = noClasses()
			.that().resideInAPackage("..application..")
			.should().dependOnClassesThat().resideInAPackage("..adapter..");

	@ArchTest
	static final ArchRule applicationServiceMustNotDependOnAdapter = noClasses()
			.that().resideInAPackage("..application.service..")
			.should().dependOnClassesThat().resideInAPackage("..adapter..");

	@ArchTest
	static final ArchRule onlyAdapterAndConfigMayDependOnSpring = noClasses()
			.that().resideInAnyPackage("..domain..", "..application..")
			.should().dependOnClassesThat().resideInAPackage("org.springframework..");

}
