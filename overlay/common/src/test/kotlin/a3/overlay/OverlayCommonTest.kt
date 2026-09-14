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
import kotlin.test.assertFalse
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
        val frozen = diff("core/", "broker/", "renderers/android-core/", "launcher/")
        assertTrue(frozen.isBlank(), frozen)
        val status = ProcessBuilder("git", "status", "--porcelain").directory(root)
            .redirectErrorStream(true).start()
        val porcelain = status.inputStream.bufferedReader().readText()
        assertEquals(0, status.waitFor())
        val allowed = listOf(
            "overlay/",
            "showcase/",
            "docs/",
            "renderers/android-compose/",
            "renderers/mac-compose/",
            "settings.gradle.kts",
            "REVIEW_REAL_OVERLAY.md",
            "review-assets/overlay/",
            "REVIEW_OVERLAY_LIFECYCLE.md",
            "review-assets/overlay-lifecycle/",
            "REVIEW_A3UI_PERF.md",
            "review-assets/"
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

    @Test
    fun OL_001_starts_collapsed() {
        val s = OverlayLifecycle.start(0)
        assertEquals(OverlayPhase.COLLAPSED, s.phase)
        assertEquals(OverlayActionMark.UNKNOWN, s.mark)
        assertEquals(null, s.expandedAt)
        assertEquals(OverlayContract.DEFAULT_PHASE, s.phase.name)
        val json = OverlayNativeJson.session(OverlayFlight.present(), false, "Chrome")
        assertTrue(json.contains("\"phase\":\"COLLAPSED\""), json)
        assertTrue(json.contains("\"mark\":\"UNKNOWN\""), json)
    }

    @Test
    fun OL_002_passthrough_collapsed_reaches_under_app() {
        val pill = OverlayRect(1000, 20, 1240, 80)
        val log = OverlayTapLog()
        log.tap(OverlayPhase.COLLAPSED, 100, 400, pill)
        log.tap(OverlayPhase.COLLAPSED, 1100, 40, pill)
        log.tap(OverlayPhase.EXPANDED, 100, 400, pill)
        assertEquals(listOf(100 to 400), log.underApp)
        assertEquals(listOf(1100 to 40, 100 to 400), log.overlay)
        assertTrue(OverlayLifecycle.reachesUnder(OverlayPhase.COLLAPSED, 50, 50, pill))
        assertFalse(OverlayLifecycle.reachesUnder(OverlayPhase.COLLAPSED, 1100, 40, pill))
        assertFalse(OverlayWindowLaw.containerTouchable(OverlayPhase.COLLAPSED))
        assertTrue(OverlayWindowLaw.pillTouchable())
        assertTrue(OverlayWindowLaw.passThroughOutsidePill(OverlayPhase.COLLAPSED))
        assertFalse(OverlayWindowLaw.passThroughOutsidePill(OverlayPhase.EXPANDED))
    }

    @Test
    fun OL_003_dispatch_collapses_within_budget() {
        val t0 = 1_000L
        val expanded = OverlayLifecycle.expand(OverlayLifecycle.start(t0), t0 + 1)
        assertEquals(OverlayPhase.EXPANDED, expanded.phase)
        val dispatched = OverlayLifecycle.dispatch(expanded, t0 + 2)
        assertEquals(OverlayPhase.COLLAPSED, dispatched.phase)
        assertEquals(OverlayActionMark.PENDING, dispatched.mark)
        assertEquals(OverlayCollapseReason.DISPATCH, dispatched.reason)
        assertEquals(t0 + 2, dispatched.collapsedAt)
        assertTrue((dispatched.collapsedAt!! - (t0 + 2)) <= OverlayLifecycle.DISPATCH_BUDGET_MS)
    }

    @Test
    fun OL_004_pill_carries_mark_not_long_text() {
        assertEquals("…", OverlayLifecycle.pillText(OverlayActionMark.PENDING))
        assertEquals("?", OverlayLifecycle.pillText(OverlayActionMark.UNKNOWN))
        assertEquals("✓", OverlayLifecycle.pillText(OverlayActionMark.DONE))
        assertFalse(OverlayLifecycle.isLongPillText(OverlayLifecycle.pillText(OverlayActionMark.PENDING)))
        assertFalse(OverlayLifecycle.isLongPillText(OverlayLifecycle.pillText(OverlayActionMark.DONE)))
        assertTrue(OverlayLifecycle.isLongPillText("A3 attivo"))
        assertTrue(OverlayLifecycle.pillText(OverlayActionMark.PENDING).length <= OverlayLifecycle.PILL_MAX_CHARS)
    }

    @Test
    fun OL_005_under_focus_collapses_when_expanded() {
        val expanded = OverlayLifecycle.expand(OverlayLifecycle.start(0), 5)
        val collapsed = OverlayLifecycle.underFocus(expanded, 6)
        assertEquals(OverlayPhase.COLLAPSED, collapsed.phase)
        assertEquals(OverlayCollapseReason.UNDER_FOCUS, collapsed.reason)
        val idle = OverlayLifecycle.underFocus(OverlayLifecycle.start(0), 1)
        assertEquals(OverlayPhase.COLLAPSED, idle.phase)
        assertEquals(null, idle.reason)
    }

    @Test
    fun OL_006_idle_timeout_collapses() {
        val expanded = OverlayLifecycle.expand(OverlayLifecycle.start(0), 0)
        assertEquals(OverlayPhase.EXPANDED, OverlayLifecycle.tick(expanded, 19_999).phase)
        val timed = OverlayLifecycle.tick(expanded, 20_000)
        assertEquals(OverlayPhase.COLLAPSED, timed.phase)
        assertEquals(OverlayCollapseReason.TIMEOUT, timed.reason)
        val poked = OverlayLifecycle.interact(expanded, 15_000)
        assertEquals(OverlayPhase.EXPANDED, OverlayLifecycle.tick(poked, 20_000).phase)
        assertEquals(OverlayPhase.COLLAPSED, OverlayLifecycle.tick(poked, 35_000).phase)
        assertEquals(20_000L, OverlayLifecycle.DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun OL_007_expanded_is_interactive() {
        val s = OverlayLifecycle.expand(OverlayLifecycle.start(0), 1)
        assertEquals(OverlayPhase.EXPANDED, s.phase)
        assertTrue(OverlayWindowLaw.containerTouchable(OverlayPhase.EXPANDED))
        val pill = OverlayRect(1000, 20, 1200, 80)
        val surface = OverlayRect(40, 200, 700, 520)
        assertEquals(
            OverlayTapTarget.SURFACE,
            OverlayLifecycle.tapTarget(OverlayPhase.EXPANDED, 100, 400, pill, surface)
        )
        assertEquals(
            OverlayTapTarget.PILL,
            OverlayLifecycle.tapTarget(OverlayPhase.EXPANDED, 1100, 40, pill, surface)
        )
        assertEquals(
            OverlayTapTarget.UNDER_APP,
            OverlayLifecycle.tapTarget(OverlayPhase.EXPANDED, 10, 10, pill, surface)
        )
    }

    @Test
    fun OL_008_reduced_motion_is_instant_marks_remain() {
        assertEquals(0L, OverlayLifecycle.transitionMs(true))
        assertEquals(OverlayLifecycle.SPRING_MS, OverlayLifecycle.transitionMs(false))
        val pending = OverlayLifecycle.dispatch(OverlayLifecycle.expand(OverlayLifecycle.start(0), 1), 2)
        val done = OverlayLifecycle.terminal(pending, 3, true)
        assertEquals(OverlayPhase.COLLAPSED, done.phase)
        assertEquals(OverlayActionMark.DONE, done.mark)
        val unknown = OverlayLifecycle.terminal(pending, 3, false)
        assertEquals(OverlayActionMark.UNKNOWN, unknown.mark)
        assertEquals(OverlayPhase.COLLAPSED, unknown.phase)
    }

    @Test
    fun OL_009_ov_regression_still_present() {
        OV_001_android_permission_is_explicit()
        OV_002_backdrop_honest_when_projection_denied()
        OV_007_flight_decomposes_and_opens_browser()
        OV_008_freeze_core_broker_renderers_launcher()
    }

    @Test
    fun OL_010_freeze_includes_agent() {
        fun diff(vararg paths: String): String {
            val proc = ProcessBuilder("git", "diff", "--stat", "--", *paths)
                .directory(root)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            assertEquals(0, proc.waitFor())
            return out
        }
        val frozen = diff("core/", "broker/", "renderers/android-core/", "launcher/", "agent/")
        assertTrue(frozen.isBlank(), frozen)
    }

    @Test
    fun OL_screenshots_are_real_png() {
        val dir = File(root, "review-assets/overlay-lifecycle")
        val needed = listOf(
            "ol-001-android-collapsed-chrome.png",
            "ol-002-android-passthrough.png",
            "ol-003-android-collapse-after-choose.png",
            "ol-007-android-expanded-chrome.png",
            "ol-001-mac-collapsed-safari.png",
            "ol-003-mac-collapse-after-choose.png",
            "ol-007-mac-expanded-safari.png"
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
