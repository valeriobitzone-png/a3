// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.compose

import a3.overlay.A3UiProfile
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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ProfileComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun PF_004_blur_off_fallback_and_honest_message() {
        composeRule.setContent {
            A3UiProfileProvider(A3UiProfile.BLUR_OFF) {
                GlassSceneHost {
                    GlassSurface { }
                }
            }
        }
        composeRule.onNodeWithTag("glass-fallback").assertIsDisplayed()
        println("PASS PF-004 compose BLUR_OFF fallback")
    }

    @Test
    fun PF_008_particles_off_keeps_idle_on_trigger() {
        composeRule.setContent {
            A3UiProfileProvider(A3UiProfile.PARTICLES_OFF) {
                ParticleBurst(trigger = true)
            }
        }
        composeRule.onNodeWithTag("particle-idle").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("particle-burst").fetchSemanticsNodes().isEmpty()
        )
        println("PASS PF-008 compose PARTICLES_OFF")
    }

    @Test
    fun PF_008_high_still_bursts() {
        composeRule.setContent {
            A3UiProfileProvider(A3UiProfile.HIGH) {
                ParticleBurst(trigger = true)
            }
        }
        composeRule.onNodeWithTag("particle-burst").assertIsDisplayed()
    }
}
