// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import a3.overlay.A3UiProfile
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class ProfileMacComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun PF_004_mac_blur_off_fallback() {
        composeRule.setContent {
            A3UiProfileProvider(A3UiProfile.BLUR_OFF) {
                GlassSceneHost {
                    MacGlassSurface { }
                }
            }
        }
        composeRule.onNodeWithTag("glass-fallback").assertIsDisplayed()
        println("PASS PF-004 mac BLUR_OFF fallback")
    }

    @Test
    fun PF_008_mac_particles_off() {
        composeRule.setContent {
            A3UiProfileProvider(A3UiProfile.PARTICLES_OFF) {
                ParticleBurst(trigger = true)
            }
        }
        composeRule.onNodeWithTag("particle-idle").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("particle-burst").fetchSemanticsNodes().isEmpty())
        println("PASS PF-008 mac PARTICLES_OFF")
    }
}
