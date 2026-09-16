// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.showcase.mac

import a3.overlay.A3UiProfile
import a3.showcase.ShowcaseA11y
import a3.showcase.ShowcaseA11yHost
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class PerfA11yMacTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun PF_007_mac_marks_under_every_profile() {
        var profile by mutableStateOf(A3UiProfile.HIGH)
        composeRule.setContent {
            Box(Modifier.size(720.dp, 900.dp)) {
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
        }
        println("PASS PF-007 mac AX marks under every profile")
    }

    @Test
    fun PF_001_mac_override_visible() {
        composeRule.setContent {
            Box(Modifier.size(720.dp, 900.dp)) {
                ShowcaseA11yHost(
                    fixtures = ShowcaseA11y.lawful(),
                    profile = A3UiProfile.BLUR_OFF
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile-pill").assertIsDisplayed()
        println("PASS PF-001 mac override visible")
    }
}
