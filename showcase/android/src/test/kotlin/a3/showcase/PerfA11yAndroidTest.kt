package a3.showcase

import a3.overlay.A3UiProfile
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class PerfA11yAndroidTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<CaptureActivity>()

    @Test
    fun PF_007_marks_readable_under_every_profile() {
        var profile by mutableStateOf(A3UiProfile.HIGH)
        composeRule.setContent {
            Box(Modifier.size(411.dp, 891.dp)) {
                ShowcaseA11yHost(fixtures = ShowcaseA11y.lawful(), profile = profile)
            }
        }
        for (next in A3UiProfile.entries) {
            composeRule.runOnIdle { profile = next }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("profile-pill").assertIsDisplayed()
            composeRule.onNodeWithTag("stale-warning").assertIsDisplayed()
            composeRule.onNodeWithTag("contradicted-badge").assertIsDisplayed()
            composeRule.onNodeWithTag("pending-label").assertIsDisplayed()
            composeRule.onNodeWithTag("fact-baseline").assertIsDisplayed()
            composeRule.onNodeWithTag("a11y-cta-hotel").assertIsDisplayed()
        }
        println("PASS PF-007 android AX marks under every profile")
    }

    @Test
    fun PF_001_and_PF_006_override_visible() {
        composeRule.setContent {
            Box(Modifier.size(411.dp, 891.dp)) {
                ShowcaseA11yHost(
                    fixtures = ShowcaseA11y.lawful(),
                    profile = A3UiProfile.MID
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile-pill").assertIsDisplayed()
        composeRule.onNodeWithTag("stale-warning").assertIsDisplayed()
        println("PASS PF-001 PF-006 showcase override visible")
    }

    @Test
    fun PF_001_showcase_settings_override_in_source() {
        val src = File("src/main/kotlin/a3/showcase/ShowcaseApp.kt").readText()
        kotlin.test.assertTrue(src.contains("profile-pill"))
        kotlin.test.assertTrue(src.contains("profile-auto"))
        kotlin.test.assertTrue(src.contains("A3UiProfile.entries"))
        kotlin.test.assertTrue(src.contains("onProfile"))
        println("PASS PF-001 showcase settings chips")
    }
}
