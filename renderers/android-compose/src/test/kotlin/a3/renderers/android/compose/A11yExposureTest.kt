package a3.renderers.android.compose

import a3.a3ui.a11y.SpokenLaw
import a3.a3ui.model.EpistemicAction
import a3.a3ui.model.EpistemicAxis
import a3.a3ui.model.EpistemicFreshness
import a3.a3ui.model.EpistemicStatus
import a3.a3ui.model.EpistemicSupport
import a3.renderers.android.core.model.RenderedNode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class A11yExposureTest {
    private fun node(axis: EpistemicAxis, role: String = "action", text: String = "conferma") =
        RenderedNode(
            id = "n1",
            role = role,
            text = text,
            axis = axis,
            stateDescription = axis.stateDescription()
        )

    @Test
    fun talkback_contradicted_is_not_dimmed_button() {
        val spoken = Exposure.contentDescription(
            node(EpistemicAxis(status = EpistemicStatus.CONTRADICTED))
        )
        SpokenLaw.requireNotSensoryGloss(spoken)
        assertTrue(spoken.contains("contradicted"), spoken)
        assertTrue(spoken.contains("conferma disabilitata"), spoken)
        assertTrue(spoken.contains("resolve conflict first"), spoken)
        assertFalse(spoken.lowercase().contains("dimmed"), spoken)
    }

    @Test
    fun talkback_stale_includes_age_warning() {
        val spoken = Exposure.contentDescription(
            node(EpistemicAxis(freshness = EpistemicFreshness.STALE), text = "Hotel Milano")
        )
        assertTrue(spoken.contains("stale 2 ore"), spoken)
        assertTrue(spoken.contains("warning"), spoken)
    }

    @Test
    fun talkback_pending_reserved_slot() {
        val spoken = Exposure.contentDescription(
            node(EpistemicAxis(action = EpistemicAction.PENDING), text = "Treno")
        )
        assertTrue(spoken.contains("in verifica"), spoken)
        assertTrue(spoken.contains("slot riservato"), spoken)
    }

    @Test
    fun fact_action_has_state_description() {
        val node = node(EpistemicAxis(), text = "Volo")
        val described = Exposure.markStateDescription(node)
        assertTrue(described.contains("FACT"), described)
        val spoken = Exposure.contentDescription(node)
        assertFalse(spoken.contains("contradicted"))
        SpokenLaw.requireNotSensoryGloss(spoken)
    }

    @Test
    fun unknown_and_held_forbid_confirm() {
        val unknown = Exposure.contentDescription(node(EpistemicAxis(support = EpistemicSupport.UNKNOWN)))
        val held = Exposure.contentDescription(node(EpistemicAxis(status = EpistemicStatus.HELD)))
        assertTrue(unknown.contains("conferma disabilitata"), unknown)
        assertTrue(held.contains("conferma disabilitata"), held)
    }
}
