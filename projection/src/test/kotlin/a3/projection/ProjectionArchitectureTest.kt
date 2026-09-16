// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.projection

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P11bis — bytecode + Gradle barrier.
 * :projection may depend on :core:world-api and :prediction only.
 */
@AnalyzeClasses(
    packages = ["a3.projection"],
    importOptions = [ImportOption.DoNotIncludeTests::class]
)
class ProjectionArchitectureTest {
    @ArchTest
    val P11bis_projection_does_not_depend_on_world_or_runtime: ArchRule =
        noClasses()
            .that().resideInAPackage("a3.projection..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("a3.core.world", "a3.core.runtime..", "a3.a3ui..", "a3.renderers..")

    @Test
    fun P11bis_gradle_module_does_not_depend_on_world_or_runtime() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("project(\":core:world-api\")"))
        assertTrue(gradle.contains("project(\":prediction\")"))
        assertFalse(Regex("""project\(":core:world"\)""").containsMatchIn(gradle))
        assertFalse(gradle.contains("project(\":core:runtime\")"))
        assertFalse(gradle.contains("project(\":a3ui\")"))
        assertFalse(gradle.contains("project(\":renderers\")"))
    }
}
