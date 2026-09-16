// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.a3ui.conformance.android

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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class AndroidConformanceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val shots = ConformancePaths.androidShots()

    private fun capture(name: String, reduced: Boolean, blurOff: Boolean, fixtures: List<SurfaceFixture>) {
        composeRule.host(reduced = reduced, blurOff = blurOff, fixtures = fixtures)
        val png = File(shots, "$name.png")
        png.parentFile?.mkdirs()
        val view = composeRule.activity.findViewById<View>(android.R.id.content)
        val width = if (view.width > 0) view.width else 411
        val height = if (view.height > 0) view.height else 891
        if (view.width <= 0 || view.height <= 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        FileOutputStream(png).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        writeTree(
            File(shots, "$name.tree.json"),
            composeRule.onNodeWithTag("conformance-host").fetchSemanticsNode()
        )
        assertTrue(png.length() > 800, "empty $name.png")
        assertTrue(bitmapHasInk(bitmap), "blank $name.png")
    }

    private fun bitmapHasInk(bitmap: Bitmap): Boolean {
        val step = 19
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val c = bitmap.getPixel(x, y)
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                if (r < 250 || g < 250 || b < 250) return true
                x += step
            }
            y += step
        }
        return false
    }

    @Test
    fun AC_009_android_contradicted_cta_disabled_reason_visible() {
        capture("CONTRADICTED", false, false, listOf(A3UiFixtures.calendarContradicted))
        composeRule.onNodeWithTag("cta-calendar").assertIsNotEnabled()
        composeRule.onNodeWithText(CtaLaw.FORBID_REASON).assertIsDisplayed()
        val spoken = composeRule.onNodeWithTag("cta-calendar").stateDescription() ?: ""
        assertTrue(spoken.contains("CONTRADICTED"), spoken)
        assertTrue(spoken.contains("confirm forbidden"), spoken)
        println("PASS AC-009 android CONTRADICTED")
    }

    @Test
    fun AC_011_android_stale_warning_visible() {
        capture("STALE", false, false, listOf(A3UiFixtures.hotelStale))
        composeRule.onNodeWithTag("cta-hotel").assertIsEnabled()
        composeRule.onNodeWithText(CtaLaw.STALE_WARNING).assertIsDisplayed()
        composeRule.onNodeWithText("€89").assertIsDisplayed()
        println("PASS AC-011 android STALE")
    }

    @Test
    fun AC_013_android_pending_reserved_slot_no_spinner() {
        capture("PENDING", false, false, listOf(A3UiFixtures.trainPending))
        composeRule.onNodeWithTag("cta-train").assertIsNotEnabled()
        composeRule.onNodeWithTag("pending-slot").assertIsDisplayed()
        composeRule.onNodeWithText(CtaLaw.PENDING_LABEL).assertIsDisplayed()
        assertFalse(composeRule.exists("pending-spinner"))
        println("PASS AC-013 android PENDING")
    }

    @Test
    fun AC_015_android_fact_cta_enabled_baseline() {
        capture("FACT", false, false, listOf(A3UiFixtures.flightFact))
        composeRule.onNodeWithTag("cta-flight").assertIsEnabled()
        composeRule.onNodeWithTag("fact-baseline").assertIsDisplayed()
        assertFalse(composeRule.exists("stale-warning"))
        assertFalse(composeRule.exists("contradicted-badge"))
        println("PASS AC-015 android FACT")
    }

    @Test
    fun AC_017_android_reduced_motion_keeps_marks() {
        capture("reduced-motion", true, false, A3UiFixtures.lawful())
        composeRule.assertLawfulScene()
        composeRule.onNodeWithTag("motion-zero").assertIsDisplayed()
        CtaLaw.judgeRender(composeRule.observation(reduced = true))
        println("PASS AC-017 android reduced motion")
    }

    @Test
    fun AC_019_android_blur_off_marks_readable() {
        capture("blur-off", false, true, A3UiFixtures.lawful())
        composeRule.onNodeWithTag("scene-blur-off").assertIsDisplayed()
        composeRule.assertLawfulScene()
        CtaLaw.judgeRender(composeRule.observation(blurOff = true))
        println("PASS AC-019 android blur off")
    }

    @Test
    fun AC_021_android_talkback_state_description() {
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
        println("PASS AC-021 android TalkBack stateDescription")
    }
}
