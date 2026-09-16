// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core

import a3.core.model.*
import a3.core.model.Claim
import a3.core.model.Observation
import a3.core.schema.ModelValidator
import a3.core.serialize.CanonicalJson
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaValidationTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    @Test
    fun every_model_validates_against_its_schema() {
        val fact = Claim("ticket.owned", true, 0.9, "train", t, t.plusSeconds(60))
        ModelValidator.goal(Goal("g", "i", listOf(fact), priority = 0.5))
        ModelValidator.intent(Intent("int", "buy ticket", "text", "explicit", 0.8, "g"))
        ModelValidator.plan(
            Plan(
                "p", "g",
                listOf(PlanStep(1, "train.commit", TrustTier.IRREVERSIBLE, false)),
                ExpectedOutcome("g", listOf(fact))
            )
        )
        ModelValidator.state(
            WorldStateSnapshot(
                version = 1,
                t = t,
                facts = listOf(fact),
                transitions = listOf(StateTransition(0, 1, "ev_1"))
            )
        )
        ModelValidator.trust(TrustGrant("tg", "train.commit", TrustTier.COMMIT, true, ttl = 300))
        ModelValidator.outcome(Outcome("o", "g", listOf(fact), 0.9, "achieved"))
        ModelValidator.observation(Observation("obs", "exec", t, listOf(fact)))
        ModelValidator.event(
            Event(
                id = "ev_accepted_1",
                t = t,
                source = "observation",
                type = "observation.accepted",
                stateVersion = 1,
                payload = Observation("obs", "exec", t, listOf(fact)),
                integrateMode = "SUPERSEDE_KEYS"
            )
        )
        ModelValidator.capability(
            Capability("c", "c", emptyList(), effects = listOf(fact), reliability = 1.0)
        )
        ModelValidator.prediction(Prediction("pr", "ctx", listOf(mapOf("k" to "v"))))
        ModelValidator.projection(Projection("pj", 1, "phone", "ui_1", listOf("confirm")))

        val json = CanonicalJson.of(Goal("g", "i", listOf(fact)))
        assertTrue(json.contains("\"desired_state\""))
        assertEquals(json, CanonicalJson.of(Goal("g", "i", listOf(fact))))
    }
}
