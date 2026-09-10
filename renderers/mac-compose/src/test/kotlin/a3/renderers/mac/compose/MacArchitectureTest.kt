package a3.renderers.mac.compose

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacArchitectureTest {
    @Test
    fun MR_006_mac_compose_does_not_depend_on_android_compose_core_a3ui_broker() {
        val classes = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("a3.renderers.mac.compose")
        noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "a3.renderers.android.compose..",
                "a3.core..",
                "a3.a3ui..",
                "a3.broker.."
            )
            .check(classes)
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("org.jetbrains.compose.desktop") || gradle.contains("compose.desktop"))
        assertTrue(gradle.contains("project(\":renderers:android-core\")"))
        val mainImpl = gradle.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("implementation(") }
            .toList()
        assertTrue(mainImpl.any { it.contains(":renderers:android-core") })
        assertFalse(mainImpl.any { it.contains(":a3ui") })
        assertFalse(mainImpl.any { it.contains(":android-compose") })
        assertFalse(mainImpl.any { it.contains(":broker") })
        assertFalse(mainImpl.any { it.contains(":core:") })
        assertFalse(mainImpl.any { it.contains(":launcher") })
        val main = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(main.isNotEmpty())
        for (file in main) {
            val text = file.readText()
            assertFalse(text.contains("a3.renderers.android.compose"), file.path)
            assertFalse(text.contains("import a3.a3ui"), file.path)
            assertFalse(text.contains("import a3.core."), file.path)
            assertFalse(text.contains("import a3.broker"), file.path)
            assertFalse(text.contains("androidx.compose.material"), file.path)
            assertFalse(text.contains("class ") && text.contains("Spec"), file.path)
        }
    }

    @Test
    fun MR_007_frozen_trees_have_empty_diff() {
        val root = File("../..")
        val proc = ProcessBuilder(
            "git",
            "diff",
            "--stat",
            "--",
            "core/",
            "core/json",
            "core/admission",
            "core/action",
            "prediction/",
            "projection/",
            "a3ui/",
            "renderers/android-core/",
            "renderers/android-compose/",
            "adapters/",
            "intent-model/",
            "broker/",
            "launcher/"
        ).directory(root).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.waitFor())
        assertTrue(out.isBlank(), out)
    }
}
