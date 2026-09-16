// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.prediction

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SPLIT-LINE — §9 public subtree must not depend on extension modules
 * (compile or test configurations).
 */
class SplitLineTest {
    private val root = File("..")

    private val publicModules = listOf(
        "core/envelope",
        "core/truth",
        "core/temporal",
        "core/confidence",
        "core/admission",
        "core/world-api",
        "core/world",
        "core/runtime",
        "core/action",
        "core/json",
        "core/t12",
        "conformance"
    )

    private val extensionPrefixes = listOf(
        ":prediction",
        ":projection",
        ":intent-model",
        ":agent",
        ":a3ui",
        ":renderers",
        ":launcher",
        ":overlay",
        ":adapters",
        ":showcase",
        ":broker"
    )

    private val projectDep = Regex(
        """(api|implementation|compileOnly|runtimeOnly|testImplementation|testCompileOnly)\(project\("([^"]+)"\)\)"""
    )

    @Test
    fun SL_001_runtime_has_no_prediction_arc() {
        val gradle = File(root, "core/runtime/build.gradle.kts").readText()
        assertFalse(gradle.contains("project(\":prediction\")"), gradle)
        assertFalse(File(root, "core/runtime/src/test/kotlin/a3/core/prediction").exists())
        assertTrue(File(root, "prediction/src/test/kotlin/a3/prediction/PredictionWorldGateTest.kt").isFile)
    }

    @Test
    fun SL_002_zero_public_to_extension_arcs() {
        val violations = ArrayList<String>()
        for (mod in publicModules) {
            val file = File(root, "$mod/build.gradle.kts")
            if (!file.isFile) continue
            for (match in projectDep.findAll(file.readText())) {
                val cfg = match.groupValues[1]
                val dep = match.groupValues[2]
                if (extensionPrefixes.any { dep == it || dep.startsWith("$it:") }) {
                    violations += "$mod [$cfg] → $dep"
                }
            }
        }
        assertTrue(violations.isEmpty(), "public→extension arcs:\n" + violations.joinToString("\n"))
    }

    @Test
    fun SL_003_split_line_doc_present() {
        val doc = File(root, "docs/SPLIT_LINE.md").readText()
        assertTrue(doc.contains("SPEC_A3-EP") || doc.contains("§9") || doc.contains("section 9"))
        assertTrue(doc.contains("envelope"))
        assertTrue(doc.contains("prediction"))
        assertTrue(doc.contains("conformance"))
        assertTrue(doc.contains(":core:runtime") || doc.contains("core:runtime"))
        assertTrue(doc.contains("dentro") || doc.contains("Pubblico") || doc.contains("public"))
    }

    @Test
    fun SL_005_freeze_empty_outside_runtime_prediction_docs() {
        fun diff(vararg paths: String): String {
            val proc = ProcessBuilder("git", "diff", "--stat", "--", *paths)
                .directory(root)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            check(proc.waitFor() == 0)
            return out
        }
        // review-assets/ is rewritten by visual/agent tests in the same suite; exclude like sibling freezes.
        val frozen = diff(".", ":!core/runtime", ":!prediction", ":!docs", ":!review-assets", ":!CHANGELOG.md")
        assertTrue(frozen.isBlank(), frozen)
        val status = ProcessBuilder("git", "status", "--porcelain")
            .directory(root)
            .redirectErrorStream(true)
            .start()
        val porcelain = status.inputStream.bufferedReader().readText()
        check(status.waitFor() == 0)
        val allowed = listOf("core/runtime/", "prediction/", "docs/", "review-assets/", "CHANGELOG.md")
        val ignore = listOf(".kotlin/", ".DS_Store")
        for (line in porcelain.lineSequence().filter { it.isNotBlank() }) {
            val path = line.drop(3).trim().removePrefix("?? ").let {
                if (it.contains(" -> ")) it.substringAfter(" -> ") else it
            }
            if (ignore.any { path.startsWith(it) }) continue
            assertTrue(
                allowed.any { path == it || path.startsWith(it) },
                "unexpected path $line"
            )
        }
    }
}
