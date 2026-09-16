// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.capability

import a3.core.model.Capability
import a3.core.model.Claim
import a3.core.model.Observation
import a3.core.world.BeliefState
import a3.core.world.integrate
import java.time.Instant
import java.util.TreeMap

object Transition {
    fun canApply(state: BeliefState, cap: Capability, now: Instant): Boolean =
        cap.preconditions.all { required ->
            matchingLive(state, required, now) != null
        }

    /**
     * Simulated transition for planning. Does not write committed BeliefWriter.
     * Confidence = min(confidence of matching precondition facts) × reliability.
     * Empty preconditions use 1.0 as the min.
     */
    fun apply(state: BeliefState, cap: Capability, now: Instant): BeliefState {
        require(canApply(state, cap, now)) { "Capability preconditions are not satisfied" }
        val confidence = propagatedConfidence(state, cap, now)
        val observation = Observation(
            id = "sim_${cap.id}_v${state.version + 1}",
            executionRef = "planner-simulation",
            t = now,
            facts = cap.effects.sortedBy { it.k }.map { effect ->
                effect.copy(confidence = confidence, observedAt = now)
            }
        )
        return state.integrate(observation)
    }

    fun propagatedConfidence(state: BeliefState, cap: Capability, now: Instant): Double {
        val minPre = if (cap.preconditions.isEmpty()) {
            1.0
        } else {
            cap.preconditions.minOf { required ->
                matchingLive(state, required, now)?.confidence
                    ?: throw IllegalStateException("precondition ${required.k} not validAt($now)")
            }
        }
        return (minPre * cap.reliability).coerceIn(0.0, 1.0)
    }

    /**
     * canApply matching: k=v present in current(now), which is exactly validAt(now).
     * Confidence is not a canApply gate; it only feeds [propagatedConfidence].
     */
    private fun matchingLive(state: BeliefState, required: Claim, now: Instant): Claim? {
        val matches = state.current(now).filter { actual ->
            actual.k == required.k && actual.v == required.v
        }
        if (matches.isEmpty()) return null
        return matches.minWith(compareBy<Claim> { it.k }.thenBy { it.id })
    }

    fun orderedCapabilities(capabilities: Map<String, Capability>): List<Capability> =
        TreeMap(capabilities).values.toList()
}
