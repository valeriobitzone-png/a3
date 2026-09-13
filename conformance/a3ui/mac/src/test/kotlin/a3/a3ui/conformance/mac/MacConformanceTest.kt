package a3.a3ui.conformance.mac

import a3.a3ui.conformance.A3UiFixtures
import a3.a3ui.conformance.CtaLaw
import a3.a3ui.conformance.ConformancePaths
import a3.a3ui.conformance.SurfaceFixture
import a3.a3ui.conformance.ui.assertLawfulScene
import a3.a3ui.conformance.ui.exists
import a3.a3ui.conformance.ui.host
import a3.a3ui.conformance.ui.observation
import a3.a3ui.conformance.ui.stateDescription
import a3.a3ui.conformance.ui.writeTree
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import java.io.File
import javax.imageio.ImageIO
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacConformanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val shots = ConformancePaths.macShots()

    private fun capture(name: String, reduced: Boolean, blurOff: Boolean, fixtures: List<SurfaceFixture>) {
        composeRule.host(reduced = reduced, blurOff = blurOff, fixtures = fixtures)
        val png = File(shots, "$name.png")
        png.parentFile?.mkdirs()
        val image = composeRule.onNodeWithTag("conformance-host").captureToImage().toAwtImage()
        ImageIO.write(image, "png", png)
        writeTree(
            File(shots, "$name.tree.json"),
            composeRule.onNodeWithTag("conformance-host").fetchSemanticsNode()
        )
        assertTrue(png.length() > 800, "empty $name.png")
    }

    @Test
    fun AC_010_mac_contradicted_cta_disabled_reason_visible() {
        capture("CONTRADICTED", false, false, listOf(A3UiFixtures.calendarContradicted))
        composeRule.onNodeWithTag("cta-calendar").assertIsNotEnabled()
        composeRule.onNodeWithText(CtaLaw.FORBID_REASON).assertIsDisplayed()
        println("PASS AC-010 mac CONTRADICTED")
    }

    @Test
    fun AC_012_mac_stale_warning_visible() {
        capture("STALE", false, false, listOf(A3UiFixtures.hotelStale))
        composeRule.onNodeWithTag("cta-hotel").assertIsEnabled()
        composeRule.onNodeWithText(CtaLaw.STALE_WARNING).assertIsDisplayed()
        println("PASS AC-012 mac STALE")
    }

    @Test
    fun AC_014_mac_pending_reserved_slot_no_spinner() {
        capture("PENDING", false, false, listOf(A3UiFixtures.trainPending))
        composeRule.onNodeWithTag("cta-train").assertIsNotEnabled()
        composeRule.onNodeWithTag("pending-slot").assertIsDisplayed()
        composeRule.onNodeWithText(CtaLaw.PENDING_LABEL).assertIsDisplayed()
        assertFalse(composeRule.exists("pending-spinner"))
        println("PASS AC-014 mac PENDING")
    }

    @Test
    fun AC_016_mac_fact_cta_enabled_baseline() {
        capture("FACT", false, false, listOf(A3UiFixtures.flightFact))
        composeRule.onNodeWithTag("cta-flight").assertIsEnabled()
        composeRule.onNodeWithTag("fact-baseline").assertIsDisplayed()
        println("PASS AC-016 mac FACT")
    }

    @Test
    fun AC_018_mac_reduced_motion_keeps_marks() {
        capture("reduced-motion", true, false, A3UiFixtures.lawful())
        composeRule.assertLawfulScene()
        composeRule.onNodeWithTag("motion-zero").assertIsDisplayed()
        CtaLaw.judgeRender(composeRule.observation(reduced = true))
        println("PASS AC-018 mac reduced motion")
    }

    @Test
    fun AC_020_mac_blur_off_marks_readable() {
        capture("blur-off", false, true, A3UiFixtures.lawful())
        composeRule.onNodeWithTag("scene-blur-off").assertIsDisplayed()
        composeRule.assertLawfulScene()
        CtaLaw.judgeRender(composeRule.observation(blurOff = true))
        println("PASS AC-020 mac blur off")
    }

    @Test
    fun AC_022_mac_voiceover_state_description() {
        composeRule.host()
        for (fixture in A3UiFixtures.lawful()) {
            val spoken = composeRule.onNodeWithTag("cta-${fixture.id}").stateDescription() ?: ""
            assertTrue(spoken.contains(fixture.mark), spoken)
            when (fixture.mark) {
                "CONTRADICTED" -> {
                    assertTrue(spoken.contains("contradicted: resolve conflict first"), spoken)
                    assertTrue(spoken.contains("forbidden"), spoken)
                }
                "PENDING", "UNKNOWN" -> assertTrue(spoken.contains("forbidden"), spoken)
                "STALE" -> assertTrue(spoken.contains("warning"), spoken)
                "FACT" -> assertTrue(spoken.contains("permitted"), spoken)
            }
        }
        println("PASS AC-022 mac VoiceOver stateDescription")
    }
}
