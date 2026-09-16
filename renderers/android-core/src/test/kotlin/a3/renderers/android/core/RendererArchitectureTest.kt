// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.core

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
    packages = ["a3.renderers"],
    importOptions = [ImportOption.DoNotIncludeTests::class]
)
class RendererArchitectureTest {
    @ArchTest
    val P43_renderers_do_not_depend_on_world_runtime_or_prediction: ArchRule =
        noClasses()
            .that().resideInAPackage("a3.renderers..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "a3.core.world",
                "a3.core.runtime..",
                "a3." + "prediction" + ".."
            )

    @ArchTest
    val P45_renderers_do_not_depend_on_accepted_observation_type: ArchRule =
        noClasses()
            .that().resideInAPackage("a3.renderers..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("a3.core.world." + "Accepted" + "Observation")

    @ArchTest
    val R7_android_core_does_not_depend_on_compose_or_material: ArchRule =
        noClasses()
            .that().resideInAPackage("a3.renderers.android.core..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "androidx.compose..",
                "androidx.compose.material..",
                "androidx.compose.material3.."
            )

    @ArchTest
    val A3UI_5_interpreter_does_not_depend_on_command: ArchRule =
        noClasses()
            .that().resideInAPackage("a3.renderers.android.core.interp..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("a3.core.action.Command")

    @Test
    fun P43_gradle_android_core_depends_only_on_a3ui() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("project(\":a3ui\")"))
        assertTrue(gradle.contains("project(\":projection\")"))
        assertFalse(gradle.contains("project(\":core:world\")"))
        assertFalse(gradle.contains("project(\":core:runtime\")"))
        assertFalse(gradle.contains("project(\":prediction\")"))
    }

    @Test
    fun P44_rendered_output_is_terminal_and_not_consumed_by_a3_modules() {
        val a3ui = File("../../a3ui/build.gradle.kts").readText()
        val projection = File("../../projection/build.gradle.kts").readText()
        val prediction = File("../../prediction/build.gradle.kts").readText()
        val runtime = File("../../core/runtime/build.gradle.kts").readText()
        for (text in listOf(a3ui, projection, prediction, runtime)) {
            assertFalse(text.contains("project(\":renderers"))
        }
        val rendererOutput = "a3.renderers.android.core.model.RenderedOutput"
        val a3uiSrc = File("../../a3ui/src/main").walkTopDown().filter { it.extension == "kt" }
        assertTrue(a3uiSrc.none { it.readText().contains(rendererOutput) })
    }

    @Test
    fun P45_no_apply_or_observation_token_on_interpreter() {
        val interp = a3.renderers.android.core.interp.A3UIInterpreter::class.java
        assertTrue(interp.methods.none { it.name == "apply" })
        assertTrue(interp.methods.none { it.name == "commit" })
        val accepted = "Accepted" + "Observation"
        val world = "World" + "State"
        assertTrue(interp.methods.none { m -> m.parameterTypes.any { it.simpleName == accepted } })
        assertTrue(interp.methods.none { m -> m.parameterTypes.any { it.simpleName == world } })
        assertTrue(
            interp.methods.filter { it.name == "interpret" }.all {
                it.returnType == a3.renderers.android.core.model.RenderedOutput::class.java
            }
        )
    }
}
