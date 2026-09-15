package a3.launcher

import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class LauncherArchitectureTest {
    @Test
    fun L1_gradle_does_not_depend_on_prediction_or_mcp() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("project(\":renderers:android-compose\")"))
        assertTrue(gradle.contains("project(\":intent-model\")"))
        assertTrue(gradle.contains("project(\":core:runtime\")"))
        assertFalse(gradle.contains(":prediction"))
        assertFalse(gradle.contains(":adapters:mcp"))
        assertTrue(gradle.contains("com.android.application"))
    }

    @Test
    fun L5_main_does_not_write_world() {
        val hits = File("src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> file.readLines().asSequence().map { line -> file to line } }
            .filter { (_, line) ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//")) return@filter false
                line.contains("BeliefWriter" + ".apply") ||
                    line.contains("mint" + "Accepted" + "Observation")
            }
            .toList()
        assertTrue(hits.isEmpty(), "write tokens in launcher main: $hits")
    }

    @Test
    fun L1_main_has_no_material_widgets_or_network() {
        val forbidden = listOf("Button(", "Card(", "Dialog(", "AlertDialog(", "Snackbar(")
        val sources = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            for (token in forbidden) {
                assertFalse(text.contains(token), "${file.path} $token")
            }
            assertFalse(text.contains("androidx.compose.material"), file.path)
            for (line in text.lineSequence()) {
                if (line.contains("TextField(") && !line.contains("BasicTextField(")) {
                    throw AssertionError("${file.path} TextField: $line")
                }
            }
            for (line in text.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("import") || trimmed.startsWith("//")) continue
                for (token in listOf("http", "socket", "gemini", "cloud")) {
                    assertFalse(line.contains(token), "${file.path} $token")
                }
            }
        }
    }

    @Test
    fun L10_main_has_no_confirm_loading_literals() {
        val forbidden = listOf("Confirm", "Conferma", "loading")
        val sources = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            for (token in forbidden) {
                assertFalse(text.contains(token), "${file.path} $token")
            }
        }
    }

    @Test
    fun F3_catalog_paints_node_text_on_item_and_action() {
        // Invariant: item/action paint BoundCopy; Theme.actionMin sizing; no hardcoded 48/160.dp.
        // Source shape moved at b088efa (GlassSurface) and 0110efe (BoundCopy pressed) —
        // assertions track the invariant, not the pre-glass Box literal.
        val catalog = File(
            "../renderers/android-compose/src/main/kotlin/a3/renderers/android/compose/ComposeCatalog.kt"
        ).readText()
        assertTrue(catalog.contains("BoundCopy(node, pressed)"))
        assertTrue(catalog.contains("if (node.text.isNotEmpty())"))
        assertTrue(catalog.contains("BasicText(") && catalog.contains("text = node.text"))
        assertTrue(Regex("\"item\"\\s*->\\s*GlassSurface").containsMatchIn(catalog))
        assertTrue(Regex("\"action\"\\s*->\\s*Box").containsMatchIn(catalog))
        assertTrue(catalog.contains("BoundCopy(node, pressed)") && catalog.contains("\"item\""))
        assertTrue(catalog.contains("BoundCopy(node, pressed)") && catalog.contains("\"action\""))
        assertTrue(catalog.contains("Theme.actionMin"))
        assertFalse(catalog.contains("48.dp"))
        assertFalse(catalog.contains("160.dp"))
    }

    @Test
    fun G2_launcher_main_does_not_invent_spec_role_or_meaning() {
        val spec = Regex("""(?:data\s+)?class\s+\w+Spec\b""")
        val allowedMeanings = setOf("timetable", "departure", "passenger", "price", "confirm")
        val meaningCall = Regex("""PresentationAtom\(\s*"([^"]+)"""")
        val sources = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            assertFalse(spec.containsMatchIn(text), file.path)
            for (match in meaningCall.findAll(text)) {
                assertTrue(match.groupValues[1] in allowedMeanings, "${file.path} ${match.groupValues[1]}")
            }
            assertFalse(text.contains("\"station\""), file.path)
        }
    }
}
