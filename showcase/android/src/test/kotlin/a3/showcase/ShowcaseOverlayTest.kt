package a3.showcase

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ShowcaseOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun SV4_001_overlay_level_does_not_host_flight_surfaces() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ShowcaseApp(
                formFactor = "phone",
                initialLevel = ShowcaseLevel.OVERLAY,
                staticChrome = true
            )
        }
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.onNodeWithTag("showcase-overlay-host").assertIsDisplayed()
        composeRule.onNodeWithTag("showcase-overlay-note").assertIsDisplayed()
        composeRule.onNodeWithTag("showcase-overlay-intent").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("scene-train").fetchSemanticsNodes().size
        )
        assertTrue(ShowcaseOverlay.NOTE.contains("not this launcher"))
        assertEquals("trova volo Roma-Milano domani", ShowcaseOverlay.INTENT)
        composeRule.onNodeWithTag("nav-overlay").assertIsDisplayed()
    }
}
