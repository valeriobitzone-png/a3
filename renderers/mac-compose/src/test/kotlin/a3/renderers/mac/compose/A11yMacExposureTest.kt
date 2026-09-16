// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import a3.renderers.android.core.model.RenderedNode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class A11yMacExposureTest {
    private fun node(description: String, role: String = "action", text: String = "conferma") =
        RenderedNode(
            id = "n1",
            role = role,
            text = text,
            stateDescription = description
        )

    @Test
    fun voiceover_contradicted_is_not_dimmed_button() {
        val spoken = MacExposure.contentDescription(node("status contradicted"))
        MacExposure.requireNotSensoryGloss(spoken)
        assertTrue(spoken.contains("contradicted"), spoken)
        assertTrue(spoken.contains("conferma disabilitata"), spoken)
        assertTrue(spoken.contains("resolve conflict first"), spoken)
        assertFalse(spoken.lowercase().contains("dimmed"), spoken)
    }

    @Test
    fun voiceover_stale_includes_age_warning() {
        val spoken = MacExposure.contentDescription(node("freshness stale", text = "Hotel Milano"))
        assertTrue(spoken.contains("stale 2 ore"), spoken)
        assertTrue(spoken.contains("warning"), spoken)
    }

    @Test
    fun voiceover_pending_reserved_slot() {
        val spoken = MacExposure.contentDescription(node("action pending", text = "Treno"))
        assertTrue(spoken.contains("in verifica"), spoken)
        assertTrue(spoken.contains("slot riservato"), spoken)
    }

    @Test
    fun fact_action_has_state_description() {
        val node = node("", text = "Volo")
        val described = MacExposure.markStateDescription(node)
        assertTrue(described.contains("FACT"), described)
        val spoken = MacExposure.contentDescription(node)
        assertFalse(spoken.contains("contradicted"))
    }
}
