// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core

import a3.core.events.EventLog
import a3.core.model.*
import a3.core.planner.DeterministicPlanner
import a3.core.planner.PlanResult
import a3.core.policy.Policy
import a3.core.runtime.Executor
import a3.core.runtime.Runtime
import a3.core.runtime.WorldStateReplay
import a3.core.schema.ModelValidator
import a3.core.serialize.CanonicalJson
import a3.core.trust.TrustGate
import a3.core.world.BeliefState
import a3.core.world.Event
import a3.core.world.Observation
import a3.core.world.BeliefWriter
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReplayWriterTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    private fun graph() = CapabilityGraph(
        mapOf(
            "calendar.read" to Capability(
                "calendar.read", "calendar.read", emptyList(),
                effects = listOf(Claim("calendar.next", "work@08:30", 1.0, "calendar", t, t.plusSeconds(3600)))
            ),
            "train.search" to Capability(
                "train.search", "train.search",
                preconditions = listOf(Claim("calendar.next", "work@08:30", 0.5, "calendar", t)),
                effects = listOf(Claim("train.selected", true, 0.95, "train", t))
            ),
            "train.commit" to Capability(
                "train.commit", "train.commit",
                preconditions = listOf(Claim("train.selected", true, 0.5, "train", t)),
                effects = listOf(Claim("ticket.owned", true, 0.97, "train", t)),
                reversible = false
            )
        )
    )

    private fun grants() = mapOf(
        "train.commit" to TrustGrant(
            "tg", "train.commit", TrustTier.IRREVERSIBLE, true, t.plusSeconds(300)
        )
    )

    private fun plan() = DeterministicPlanner().plan(
        Goal("g", "i", listOf(Claim("ticket.owned", true, 0.5, "goal", t))),
        BeliefState(),
        graph(),
        t
    ) as PlanResult.Success

    @Test
    fun V1_every_accepted_event_carries_integrate_mode_and_main_does_not_branch_on_rollback_prefix() {
        val world = BeliefWriter()
        Runtime(Policy(), TrustGate()).execute(
            plan().plan,
            world,
            graph().capabilities,
            Executor { cap, now -> Observation("o_${cap.id}", "e", now, cap.effects) },
            t,
            grants()
        )
        val accepted = world.eventLog().all().filter { it.type == "observation.accepted" }
        assertTrue(accepted.isNotEmpty())
        for (event in accepted) {
            val mode = assertNotNull(event.integrateMode)
            assertTrue(mode == "SUPERSEDE_KEYS" || mode == "COMPENSATE")
            ModelValidator.event(event)
        }

        val mainTrees = listOf(File("../world/src/main"), File("../world-api/src/main"), File("src/main"))
        val prefixBranches = mainTrees.flatMap { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file -> file.readLines().map { line -> file to line } }
        }.filter { (_, line) ->
            line.contains("obs_rollback_") &&
                (line.contains("startsWith") || Regex("""\b(if|when)\b""").containsMatchIn(line))
        }
        assertTrue(prefixBranches.isEmpty(), "decision on rollback prefix: $prefixBranches")
    }

    @Test
    fun V2_apply_is_the_only_writer_event_log_does_not_apply_or_integrate() {
        val eventLogSrc = File("../world/src/main/kotlin/a3/core/events/EventLog.kt").readText()
        assertFalse(eventLogSrc.contains("integrate("))
        assertFalse(eventLogSrc.contains("BeliefWriter.apply"))
        assertFalse(eventLogSrc.contains("mintAccepted"))
        assertTrue(eventLogSrc.contains("fun replay()"))
        assertFalse(eventLogSrc.contains("replayState"))

        val replaySrc = File("src/main/kotlin/a3/core/runtime/WorldStateReplay.kt").readText()
        assertTrue(replaySrc.contains("replica.apply("))
        assertTrue(replaySrc.contains("parseIntegrateMode(event.integrateMode)"))
        assertFalse(replaySrc.contains("obs_rollback_"))
        assertFalse(replaySrc.contains("startsWith"))
    }

    @Test
    fun V3_replay_fold_matches_committed_bytes_for_supersede_and_compensate() {
        var calls = 0
        val executor = Executor { cap, now ->
            calls += 1
            val facts = if (calls <= 2) cap.effects else cap.effects.map { it.copy(v = false) }
            Observation("o_${cap.id}", "e", now, facts)
        }
        val world = BeliefWriter()
        val result = Runtime(Policy(), TrustGate()).execute(
            plan().plan, world, graph().capabilities, executor, t, grants()
        )
        assertTrue(result.rolledBack)
        val modes = world.eventLog().all()
            .filter { it.type == "observation.accepted" }
            .map { it.integrateMode }
        assertEquals(listOf("SUPERSEDE_KEYS", "SUPERSEDE_KEYS", "COMPENSATE"), modes)

        val replayed = WorldStateReplay.replay(world.eventLog())
        assertEquals(result.state.version, replayed.version)
        assertContentEquals(
            CanonicalJson.bytesState(result.state),
            CanonicalJson.bytesState(replayed)
        )
        assertFalse(replayed === world.committed)
    }

    @Test
    fun V4_accepted_without_mode_or_unknown_mode_throws_and_does_not_guess() {
        val obs = Observation(
            "o_orphan",
            "e",
            t,
            listOf(Claim("ticket.owned", true, 1.0, "obs", t))
        )
        val missing = EventLog()
        missing.append(
            Event(
                id = "ev_accepted_1",
                t = t,
                source = "observation",
                type = "observation.accepted",
                payload = obs
            )
        )
        assertFailsWith<IllegalArgumentException> {
            WorldStateReplay.replay(missing)
        }

        val unknown = EventLog()
        unknown.append(
            Event(
                id = "ev_accepted_1",
                t = t,
                source = "observation",
                type = "observation.accepted",
                payload = obs,
                integrateMode = "NORMAL"
            )
        )
        val thrown = assertFailsWith<IllegalArgumentException> {
            WorldStateReplay.replay(unknown)
        }
        assertTrue(thrown.message!!.contains("unknown integrate_mode"))
    }
}
