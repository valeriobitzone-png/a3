// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import a3.overlay.A3UiProfile
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import java.io.File
import java.time.Instant

/**
 * On-screen Mac catalog harvest. Compose Desktop + Skia Metal + [Modifier.blur]
 * replica (not OverlayCompositor, not SOFTWARE tests).
 */
fun main(args: Array<String>) {
    val profile = A3UiProfile.parse(
        argValue(args, "--profile") ?: System.getProperty("a3.harvest.profile")
    ) ?: A3UiProfile.HIGH
    val frames = (
        argValue(args, "--frames") ?: System.getProperty("a3.harvest.frames")
        )?.toIntOrNull() ?: 320
    val log = argValue(args, "--log") ?: System.getProperty("a3.harvest.log")
        ?: error("MacFrameHarvest requires --log=")
    val api = System.getProperty("skiko.renderApi").orEmpty()
    check(!api.equals("SOFTWARE", ignoreCase = true)) {
        "catalog harvest MUST NOT use skiko SOFTWARE"
    }
    val samples = ArrayList<Double>(frames)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "a3 catalog harvest ${profile.wire()}",
            state = WindowState(width = 1280.dp, height = 800.dp)
        ) {
            A3UiProfileProvider(profile) {
                GlassSceneHost(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(28.dp)) {
                        BasicText(
                            "${profile.wire()} · auto",
                            style = TextStyle(color = Color.White, fontSize = 14.sp)
                        )
                        repeat(8) { i ->
                            MacGlassSurface(
                                Modifier
                                    .padding(top = 12.dp)
                                    .fillMaxWidth()
                            ) {
                                BasicText(
                                    "catalog card $i  Roma–Milano",
                                    modifier = Modifier.padding(20.dp),
                                    style = TextStyle(color = Color.White, fontSize = 18.sp)
                                )
                            }
                        }
                    }
                }
            }
            LaunchedEffect(Unit) {
                var last = 0L
                while (samples.size < frames) {
                    withFrameNanos { now ->
                        if (last != 0L) {
                            samples += (now - last) / 1_000_000.0
                        }
                        last = now
                    }
                }
                writeDump(log, profile, samples, api)
                exitApplication()
            }
        }
    }
}

private fun argValue(args: Array<String>, flag: String): String? {
    val idx = args.indexOf(flag)
    if (idx >= 0 && idx + 1 < args.size) return args[idx + 1]
    return args.firstOrNull { it.startsWith("$flag=") }?.substringAfter("=")
}

private fun writeDump(path: String, profile: A3UiProfile, samples: List<Double>, api: String) {
    val sorted = samples.sorted()
    fun pct(p: Double): Double {
        if (sorted.isEmpty()) return Double.NaN
        val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }
    File(path).parentFile?.mkdirs()
    File(path).writeText(
        buildString {
            appendLine("host=${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
            appendLine("date=${Instant.now()}")
            appendLine("method=withFrameNanos on-screen Compose Desktop MacCatalog glass")
            appendLine("scene=catalog")
            appendLine("profile=${profile.wire()}")
            appendLine("skiko.renderApi=${api.ifBlank { "default" }}")
            appendLine("harness=on-screen Window (not skiko SOFTWARE tests)")
            appendLine("frames=${samples.size}")
            appendLine("p50_ms=${pct(0.50)}")
            appendLine("p95_ms=${pct(0.95)}")
            appendLine("p99_ms=${pct(0.99)}")
            appendLine("max_ms=${sorted.lastOrNull() ?: Double.NaN}")
            appendLine("samples_ms=${samples.joinToString(",") { String.format(java.util.Locale.US, "%.4f", it) }}")
        }
    )
}
