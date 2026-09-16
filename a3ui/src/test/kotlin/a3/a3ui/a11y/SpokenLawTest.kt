// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.a3ui.a11y

import a3.a3ui.model.EpistemicAction
import a3.a3ui.model.EpistemicAxis
import a3.a3ui.model.EpistemicFreshness
import a3.a3ui.model.EpistemicStatus
import a3.a3ui.model.EpistemicSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpokenLawTest {
    @Test
    fun every_mark_has_non_sensory_state_description() {
        for (mark in SpokenLaw.marks()) {
            val described = SpokenLaw.stateDescription(mark)
            assertTrue(described.startsWith(mark.wire()), described)
            SpokenLaw.requireNotSensoryGloss(described)
            assertFalse(described.contains("amber") || described.contains("blur"))
        }
    }

    @Test
    fun canonical_hotel_matches_spec_example() {
        val spoken = SpokenLaw.canonicalHotelReading("Hotel", "prezzo 89 euro")
        assertTrue(spoken.contains("stale 2 ore"))
        assertTrue(spoken.contains("prezzo 89 euro"))
        assertTrue(spoken.contains("conferma disabilitata"))
        assertTrue(spoken.contains("ragione: contradicted resolve conflict first"))
        assertFalse(spoken.contains("dimmed"))
    }

    @Test
    fun contradicted_reads_type_reason_forbidden_action() {
        val spoken = SpokenLaw.reading(
            SpokenRequest(oggetto = "Calendario", mark = MarkKind.CONTRADICTED)
        )
        assertTrue(spoken.contains("contradicted"), spoken)
        assertTrue(spoken.contains("conferma disabilitata"), spoken)
        assertTrue(spoken.contains("ragione: contradicted resolve conflict first"), spoken)
        assertFalse(spoken.contains("dimmed"))
    }

    @Test
    fun stale_reads_age_and_warning() {
        val spoken = SpokenLaw.reading(
            SpokenRequest(
                oggetto = "Hotel Milano",
                mark = MarkKind.STALE,
                priceSpoken = "prezzo 89 euro"
            )
        )
        assertTrue(spoken.contains("stale 2 ore"), spoken)
        assertTrue(spoken.contains("prezzo 89 euro"), spoken)
        assertTrue(spoken.contains("warning: stale 2 ore"), spoken)
        assertTrue(spoken.contains("conferma abilitata"), spoken)
    }

    @Test
    fun pending_reads_reserved_slot() {
        val spoken = SpokenLaw.reading(SpokenRequest(oggetto = "Treno", mark = MarkKind.PENDING))
        assertTrue(spoken.contains("in verifica, slot riservato"), spoken)
        assertTrue(spoken.contains("conferma disabilitata"), spoken)
    }

    @Test
    fun fact_is_baseline_without_extra_mark() {
        val spoken = SpokenLaw.reading(SpokenRequest(oggetto = "Volo", mark = MarkKind.FACT))
        assertEquals("Volo, conferma abilitata", spoken)
        assertFalse(spoken.contains("stale"))
        assertFalse(spoken.contains("contradicted"))
        assertFalse(spoken.contains("in verifica"))
        assertTrue(SpokenLaw.stateDescription(MarkKind.FACT).contains("FACT"))
    }

    @Test
    fun held_and_unknown_forbid_confirm() {
        val held = SpokenLaw.reading(SpokenRequest(oggetto = "Hotel", mark = MarkKind.HELD))
        val unknown = SpokenLaw.reading(SpokenRequest(oggetto = "Hotel", mark = MarkKind.UNKNOWN))
        assertTrue(held.contains("held") && held.contains("conferma disabilitata"), held)
        assertTrue(unknown.contains("uncertain") && unknown.contains("conferma disabilitata"), unknown)
    }

    @Test
    fun sensory_gloss_is_rejected() {
        assertFailsWith<IllegalStateException> {
            SpokenLaw.requireNotSensoryGloss("conferma, bottone dimmed")
        }
    }

    @Test
    fun axis_maps_to_mark_kind() {
        assertEquals(MarkKind.CONTRADICTED, SpokenLaw.of(EpistemicAxis(status = EpistemicStatus.CONTRADICTED)))
        assertEquals(MarkKind.HELD, SpokenLaw.of(EpistemicAxis(status = EpistemicStatus.HELD)))
        assertEquals(MarkKind.PENDING, SpokenLaw.of(EpistemicAxis(action = EpistemicAction.PENDING)))
        assertEquals(MarkKind.UNKNOWN, SpokenLaw.of(EpistemicAxis(support = EpistemicSupport.UNKNOWN)))
        assertEquals(MarkKind.STALE, SpokenLaw.of(EpistemicAxis(freshness = EpistemicFreshness.STALE)))
        assertEquals(MarkKind.FACT, SpokenLaw.of(EpistemicAxis()))
    }

    @Test
    fun touch_targets_meet_platform_minima() {
        assertTrue(SpokenLaw.ANDROID_MIN_DP >= 48)
        assertTrue(SpokenLaw.MAC_MIN_PT >= 44)
    }
}
