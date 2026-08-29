package a3.a3ui

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@AnalyzeClasses(
    packages = ["a3.a3ui"],
    importOptions = [ImportOption.DoNotIncludeTests::class]
)
class A3UIArchitectureTest {
    @ArchTest
    val P11ter_a3ui_does_not_depend_on_world_runtime_renderers_adapters: ArchRule =
        noClasses()
            .that().resideInAPackage("a3.a3ui..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "a3.core.world",
                "a3.core.runtime..",
                "a3.renderers..",
                "a3.adapters.."
            )

    @ArchTest
    val P42_a3ui_does_not_depend_on_prediction_domain_candidate: ArchRule =
        noClasses()
            .that().resideInAPackage("a3.a3ui..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("a3." + "prediction" + ".model." + "ProjectionCandidate")

    @Test
    fun P11ter_gradle_module_depends_only_on_projection_and_world_api() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("project(\":projection\")"))
        assertTrue(gradle.contains("project(\":core:world-api\")"))
        assertFalse(gradle.contains("project(\":prediction\")"))
        assertFalse(Regex("""project\(":core:world"\)""").containsMatchIn(gradle))
        assertFalse(gradle.contains("project(\":core:runtime\")"))
        assertFalse(gradle.contains("project(\":renderers\")"))
        assertFalse(gradle.contains("project(\":adapters\")"))
    }
}
