package a3.overlay

import a3.agent.admit.AgentAdmission
import a3.agent.model.ActivityKind
import a3.agent.plan.PlanDecomposer
import a3.agent.surface.AgentSurfaces
import a3.agent.travel.TravelCatalog
import a3.core.admission.SourceId
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OverlayCommonTest {
    private val root = File("../..")

    @Test
    fun OV_001_android_permission_is_explicit() {
        val denied = OverlayPolicy.android(canDrawOverlays = false, mediaProjectionGranted = false)
        assertEquals(OverlayAvailability.DISABLED_NO_OVERLAY_PERMISSION, denied.availability)
        assertTrue(denied.message.contains(OverlayPolicy.ANDROID_OVERLAY_PERMISSION))
        val enabled = OverlayPolicy.android(true, false)
        assertEquals(OverlayAvailability.ENABLED, enabled.availability)
        assertEquals(OverlayContract.ANDROID_WINDOW_TYPE, "TYPE_APPLICATION_OVERLAY")
    }

    @Test
    fun OV_002_backdrop_honest_when_projection_denied() {
        val blur = OverlayPolicy.android(true, true)
        assertEquals(BackdropMode.REAL_BLUR, blur.backdrop)
        val clear = OverlayPolicy.android(true, false)
        assertEquals(BackdropMode.UNAVAILABLE, clear.backdrop)
        assertEquals(OverlayPolicy.BLUR_UNAVAILABLE, clear.message)
        val px = IntArray(9) { 0xFF112233.toInt() }
        px[4] = 0xFFFF0000.toInt()
        val out = OverlayBlur.blur(px, 3, 3, 1)
        assertTrue(out[4] != px[4] || out[0] != 0xFF112233.toInt())
    }

    @Test
    fun OV_007_flight_decomposes_and_opens_browser() {
        val plan = PlanDecomposer.decompose(OverlayFlight.INTENT)
        assertTrue(plan.activities.contains(ActivityKind.TRANSPORT), plan.activities.toString())
        val at = Instant.parse("2026-09-12T08:00:00Z")
        val admitted = mutableSetOf<Pair<SourceId, String>>()
        val titles = TravelCatalog.query(ActivityKind.TRANSPORT, at, AgentAdmission.policy(), admitted)
            .mapIndexed { i, view -> AgentSurfaces.optionFromAdmission(ActivityKind.TRANSPORT, view, at, i).title }
        val session = OverlayFlight.present()
        assertEquals(titles, session.cards.map { it.title })
        val opened = ArrayList<String>()
        val url = OverlayFlight.choose(session, OverlayFlight.air(session).id) {
            opened += it
            true
        }
        assertEquals(OverlayFlight.AIR_URL, url)
        assertEquals(listOf(OverlayFlight.AIR_URL), opened)
        assertTrue(session.cards.none { it.url.contains("a3.launcher") })
    }

    @Test
    fun OV_008_freeze_core_broker_renderers_launcher() {
        fun diff(vararg paths: String): String {
            val proc = ProcessBuilder("git", "diff", "--stat", "--", *paths)
                .directory(root)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            assertEquals(0, proc.waitFor())
            return out
        }
        val frozen = diff("core/", "broker/", "renderers/", "launcher/")
        assertTrue(frozen.isBlank(), frozen)
        val status = ProcessBuilder("git", "status", "--porcelain").directory(root)
            .redirectErrorStream(true).start()
        val porcelain = status.inputStream.bufferedReader().readText()
        assertEquals(0, status.waitFor())
        val allowed = listOf(
            "overlay/",
            "showcase/",
            "settings.gradle.kts",
            "REVIEW_REAL_OVERLAY.md",
            "review-assets/overlay/"
        )
        val ignore = listOf(".kotlin/", ".DS_Store")
        for (line in porcelain.lineSequence().filter { it.isNotBlank() }) {
            val path = line.drop(3).trim().removePrefix("?? ").let {
                if (it.contains(" -> ")) it.substringAfter(" -> ") else it
            }
            if (ignore.any { path.startsWith(it) }) continue
            assertTrue(allowed.any { path == it || path.startsWith(it) }, "unexpected path $line")
        }
        val settings = File(root, "settings.gradle.kts").readText()
        assertTrue(settings.contains(":overlay:common"))
        assertTrue(settings.contains(":overlay:android"))
        assertTrue(settings.contains(":overlay:mac"))
        val classes = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("a3.overlay")
        noClasses()
            .that().resideInAPackage("a3.overlay..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("a3.launcher..", "a3.broker..")
            .check(classes)
    }

    @Test
    fun OV_screenshots_are_real_png_over_apps() {
        val dir = File(root, "review-assets/overlay")
        val needed = listOf(
            "ov-001-android-permission-denied.png",
            "ov-002-android-blur-unavailable.png",
            "ov-002-android-real-blur.png",
            "ov-003-android-chrome.png",
            "ov-003-android-whatsapp.png",
            "ov-005-mac-safari-blur.png",
            "ov-006-mac-mail.png",
            "ov-007-android-flight-over-chrome.png",
            "ov-007-android-chrome-opened.png"
        )
        for (name in needed) {
            val f = File(dir, name)
            assertTrue(f.exists() && f.length() > 80_000, name)
            val bytes = f.readBytes()
            assertEquals(0x89.toByte(), bytes[0], name)
            assertEquals('P'.code.toByte(), bytes[1], name)
            assertEquals('N'.code.toByte(), bytes[2], name)
            assertEquals('G'.code.toByte(), bytes[3], name)
        }
    }
}
