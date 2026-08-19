package vn.edu.school.schedule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "vn.edu.school.schedule", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule sharedMustNotDependOnFeatures = noClasses()
            .that().resideInAPackage("..shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..auth..", "..user..", "..organization..", "..academic..", "..weeklyplan..",
                    "..task..", "..notification..", "..reminder..", "..conversation..", "..audit..", "..export..");
}
