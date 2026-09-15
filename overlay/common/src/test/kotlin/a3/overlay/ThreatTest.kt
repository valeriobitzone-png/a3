package a3.overlay

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreatTest {
    private val root = File("../..")

    @Test
    fun TH_001_agent_pipeline_has_no_capture_sources() {
        assertFalse(OverlayCaptureLaw.agentMayReadPixels())
        assertEquals(OverlayCapturePurpose.VISIBLE_BLUR, OverlayCaptureLaw.purpose)
        assertFalse(OverlayCaptureLaw.PERSIST_ALLOWED)
        assertFalse(OverlayCaptureLaw.OFF_DEVICE_ALLOWED)
        val session = OverlayFlight.present()
        assertTrue(session.cards.isNotEmpty())
        assertTrue(session.planDigest.startsWith("overlay-flight"))
        val agentMain = File(root, "agent/src/main")
        assertTrue(agentMain.isDirectory, agentMain.path)
        val hits = ArrayList<String>()
        agentMain.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            for (needle in OverlayCaptureLaw.FORBIDDEN_AGENT_CAPTURE) {
                if (text.contains(needle)) hits += "${file.relativeTo(root)}:$needle"
            }
        }
        assertTrue(hits.isEmpty(), hits.joinToString())
    }

    @Test
    fun TH_004_no_public_mark_spoof_api() {
        assertFalse(OverlayMarkLaw.PUBLIC_SPOOF_API)
        assertFalse(OverlayMarkLaw.mayForgeFromThirdParty())
        assertEquals("overlay-only", OverlayMarkLaw.RENDERER)
        assertEquals("?", OverlayLifecycle.pillText(OverlayActionMark.UNKNOWN))
        val overlayKt = File(root, "overlay").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.path.contains("/test/") }
            .toList()
        val spoof = overlayKt.filter { file ->
            val text = file.readText()
            text.contains("fun drawMark") ||
                text.contains("fun spoofMark") ||
                text.contains("fun forgeMark")
        }
        assertTrue(spoof.isEmpty(), spoof.joinToString { it.path })
        val manifest = File(root, "overlay/android/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:name=\".OverlayService\""))
        assertTrue(manifest.contains("android:exported=\"false\""))
        val chrome = File(root, "overlay/android/src/main/kotlin/a3/overlay/OverlayChrome.kt").readText()
        assertTrue(chrome.contains("OverlayLifecycle.pillText"))
    }

    @Test
    fun TH_005_sensitive_surfaces_collapse_or_hide() {
        assertTrue(OverlaySensitiveLaw.SHOULD_COLLAPSE_OR_HIDE)
        assertEquals(OverlayPhase.COLLAPSED, OverlaySensitiveLaw.defaultPhase(OverlaySurfaceKind.SENSITIVE))
        assertEquals(
            OverlayPhase.COLLAPSED,
            OverlaySensitiveLaw.effectivePhase(OverlaySurfaceKind.SENSITIVE, OverlayPhase.EXPANDED)
        )
        assertEquals(
            OverlayPhase.EXPANDED,
            OverlaySensitiveLaw.effectivePhase(OverlaySurfaceKind.ROUTINE, OverlayPhase.EXPANDED)
        )
        assertFalse(OverlaySensitiveLaw.mayShowExpanded(OverlaySurfaceKind.SENSITIVE))
        val hidden = OverlayLifecycle.hideSensitive(OverlayLifecycle.expand(OverlayLifecycle.start(0), 1), 2)
        assertEquals(OverlayPhase.COLLAPSED, hidden.phase)
        assertEquals(OverlayCollapseReason.SENSITIVE, hidden.reason)
        val android = File(root, "overlay/android/src/main/kotlin/a3/overlay/OverlayService.kt").readText()
        assertTrue(android.contains("EXTRA_SENSITIVE"))
        val swift = File(root, "overlay/mac/native/OverlayMain.swift").readText()
        assertTrue(swift.contains("--sensitive"))
        val threat = File(root, "docs/THREAT_MODEL.md").readText()
        assertTrue(threat.contains("collapse") || threat.contains("hidden"))
        assertTrue(threat.contains("SENSITIVE") || threat.contains("sensibil"))
    }

    @Test
    fun TH_006_permission_table_enabled_and_deny() {
        val rows = OverlayPermissionTable.rows()
        assertEquals(3, rows.size)
        assertEquals(OverlayPolicy.ANDROID_OVERLAY_PERMISSION, OverlayPermissionTable.systemAlertWindow.permission)
        assertEquals(OverlayPolicy.OVERLAY_DENIED, OverlayPermissionTable.systemAlertWindow.onDeny)
        assertEquals(OverlayPolicy.BLUR_UNAVAILABLE, OverlayPermissionTable.mediaProjection.onDeny)
        assertEquals(OverlayPolicy.ACCESSIBILITY_OPTIONAL, OverlayPermissionTable.accessibility.onDeny)
        val deniedOverlay = OverlayPolicy.android(canDrawOverlays = false, mediaProjectionGranted = false)
        assertEquals(OverlayAvailability.DISABLED_NO_OVERLAY_PERMISSION, deniedOverlay.availability)
        assertEquals(OverlayPolicy.OVERLAY_DENIED, deniedOverlay.message)
        val deniedBlur = OverlayPolicy.android(true, false)
        assertEquals(OverlayAvailability.ENABLED, deniedBlur.availability)
        assertEquals(BackdropMode.UNAVAILABLE, deniedBlur.backdrop)
        assertEquals(OverlayPolicy.BLUR_UNAVAILABLE, deniedBlur.message)
        val mac = OverlayPolicy.mac(false)
        assertEquals(OverlayAvailability.ENABLED, mac.availability)
        assertTrue(mac.message.contains(OverlayPolicy.ACCESSIBILITY_OPTIONAL))
        val docs = File(root, "docs/THREAT_MODEL.md").readText()
        assertTrue(docs.contains("SYSTEM_ALERT_WINDOW"))
        assertTrue(docs.contains("MediaProjection"))
        assertTrue(docs.contains("Accessibility"))
    }

    @Test
    fun TH_007_overlay_logs_admit_no_keys_or_tokens() {
        assertTrue(OverlayLogRedact.admits("epistemic overlay"))
        assertTrue(OverlayLogRedact.admits("blur unavailable"))
        assertFalse(OverlayLogRedact.admits("api_key=secret-value"))
        assertFalse(OverlayLogRedact.admits("Authorization: Bearer abc"))
        assertFalse(OverlayLogRedact.admits("sk_live_example"))
        assertFalse(OverlayLogRedact.admits("a3_token=abc"))
        val logCall = Regex("""(Log\.|println\(|NSLog|os_log|fputs)\(""")
        val hits = ArrayList<String>()
        File(root, "overlay").walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "swift") }
            .filter { !it.path.contains("/test/") && !it.path.contains("/src/test/") }
            .forEach { file ->
                file.readLines().forEachIndexed { i, line ->
                    if (logCall.containsMatchIn(line) && !OverlayLogRedact.admits(line)) {
                        hits += "${file.relativeTo(root)}:${i + 1}:$line"
                    }
                }
            }
        assertTrue(hits.isEmpty(), hits.joinToString("\n"))
    }

    @Test
    fun TH_009_freeze_empty_outside_overlay_and_docs() {
        fun diff(vararg paths: String): String {
            val proc = ProcessBuilder("git", "diff", "--stat", "--", *paths)
                .directory(root)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            assertEquals(0, proc.waitFor())
            return out
        }
        // review-assets/ is rewritten by visual/agent tests in the same suite; exclude like sibling freezes.
        val frozen = diff(".", ":!overlay", ":!docs", ":!review-assets", ":!CHANGELOG.md")
        assertTrue(frozen.isBlank(), frozen)
        val status = ProcessBuilder("git", "status", "--porcelain")
            .directory(root)
            .redirectErrorStream(true)
            .start()
        val porcelain = status.inputStream.bufferedReader().readText()
        assertEquals(0, status.waitFor())
        val allowed = listOf("overlay/", "docs/", "review-assets/", "CHANGELOG.md")
        val ignore = listOf(".kotlin/", ".DS_Store")
        for (line in porcelain.lineSequence().filter { it.isNotBlank() }) {
            val path = line.drop(3).trim().removePrefix("?? ").let {
                if (it.contains(" -> ")) it.substringAfter(" -> ") else it
            }
            if (ignore.any { path.startsWith(it) }) continue
            assertTrue(allowed.any { path == it || path.startsWith(it) }, "unexpected path $line")
        }
    }
}
