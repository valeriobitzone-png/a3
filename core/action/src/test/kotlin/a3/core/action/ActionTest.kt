package a3.core.action

import a3.core.admission.CLAIM_ARRAY_SCHEMA
import a3.core.admission.ConfidenceProfile
import a3.core.admission.ObservationCandidate
import a3.core.admission.SourceId
import a3.core.admission.SourceType
import a3.core.world.BeliefState
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ActionTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    private fun auth(
        planDigest: String = "plan-1",
        beliefRevisionHash: String = "rev-1",
        expiresAt: Instant = t.plusSeconds(60)
    ) = Authorization(
        authorizationId = "auth-1",
        planDigest = planDigest,
        beliefRevisionHash = beliefRevisionHash,
        principal = "p",
        scopes = listOf("execute"),
        resource = "r",
        maximumImpact = "low",
        expiresAt = expiresAt,
        idempotencyKey = "idem-auth",
        policyId = "pol",
        policyVersion = "1",
        policyDigest = "digest"
    )

    private fun command(
        planDigest: String = "plan-1",
        idempotencyKey: String = "idem-cmd"
    ) = Command(
        commandId = "cmd-1",
        planDigest = planDigest,
        authorizationId = "auth-1",
        idempotencyKey = idempotencyKey,
        commandDigest = "cmd-digest"
    )

    private fun authorized(beliefRevisionHash: String = "rev-1"): ActionState {
        var s = ActionState("act-1")
        s = transition(s, ActionEvent.PlanProposed(t, "intent-1"))
        s = transition(s, ActionEvent.PlanValidated(t, "plan-1"))
        s = transition(s, ActionEvent.AuthorizationGranted(t, "auth-1", auth(beliefRevisionHash = beliefRevisionHash)))
        return s
    }

    private fun dispatched(): ActionState {
        var s = authorized()
        s = transition(s, ActionEvent.CommandCreated(t, "cmd-digest", command(), "rev-1"))
        s = transition(s, ActionEvent.CommandDispatched(t, "disp-1", "rev-1"))
        return s
    }

    private fun completed(): ActionState =
        transition(dispatched(), ActionEvent.ExecutorCompleted(t, "rcpt-1"))

    @Test
    fun ACT_001_action_state_is_separate_from_belief() {
        val before = ActionState("act-1")
        val next = transition(before, ActionEvent.PlanProposed(t, "intent-1"))
        assertEquals(PlanPhase.PROPOSED, next.planPhase)
        assertNotEquals(before.events, next.events)
        assertTrue(BeliefState::class.java.declaredFields.none { it.type == ActionState::class.java })
        assertTrue(ActionState::class.java.declaredFields.none { it.type.simpleName == "BeliefState" })
        val belief = BeliefState()
        val version = belief.version
        transition(before, ActionEvent.PlanProposed(t, "intent-1"))
        assertEquals(version, belief.version)
        assertFails { rewriteActionHistory() }
    }

    @Test
    fun ACT_002_receipt_does_not_write_belief() {
        val receipt = ExecutionReceipt("rcpt-1", "cmd-1", "disp-1", true, t)
        val next = transition(dispatched(), ActionEvent.ExecutorCompleted(receipt.at, receipt.receiptId))
        assertEquals(ActionPhase.COMPLETED, next.actionPhase)
        val belief = BeliefState()
        assertEquals(0, belief.version)
        assertTrue(belief.facts.isEmpty())
        assertFails { receiptCannotClaimDomain(receipt) }
    }

    @Test
    fun ACT_003_completed_is_not_observed() {
        val next = completed()
        assertEquals(ActionPhase.COMPLETED, next.actionPhase)
        assertNotEquals(ActionPhase.OBSERVED, next.actionPhase)
        assertEquals(null, next.linkedAcceptedObservationId)
        assertFails { completedIsNotObserved() }
        assertFails {
            transition(next.copy(actionPhase = ActionPhase.OBSERVED), ActionEvent.ExecutorCompleted(t, "rcpt-1"))
        }
    }

    @Test
    fun ACT_004_accepted_links_observed_candidate_does_not() {
        val observed = transition(completed(), ActionEvent.ObservationLinked(t, "accepted-1"))
        assertEquals(ActionPhase.OBSERVED, observed.actionPhase)
        assertEquals("accepted-1", observed.linkedAcceptedObservationId)
        val stillCompleted = completed()
        assertNotEquals(ActionPhase.OBSERVED, stillCompleted.actionPhase)
        assertFails {
            transition(stillCompleted, ActionEvent.ObservationLinked(t, ""))
        }
        ObservationCandidate(
            source = SourceId("calendar"),
            id = "cand-1",
            occurredAt = t,
            observedAt = t,
            ingestedAt = t,
            data = emptyList<Any>(),
            dataschema = CLAIM_ARRAY_SCHEMA,
            confidenceProfile = ConfidenceProfile(SourceType.DIRECT, "")
        )
        assertFails { candidateCannotLinkObserved() }
        assertNotEquals(ActionPhase.OBSERVED, stillCompleted.actionPhase)
    }

    @Test
    fun ACT_005_timeout_produces_unknown_not_failed() {
        val unknown = transition(dispatched(), ActionEvent.TimeoutObserved(t, "to-1"))
        assertEquals(ActionPhase.UNKNOWN, unknown.actionPhase)
        assertNotEquals(ActionPhase.FAILED, unknown.actionPhase)
        assertFails { timeoutIsNotFailed() }
        assertFails {
            transition(dispatched(), ActionEvent.ExecutorFailed(t, "timeout without evidence"))
        }
    }

    @Test
    fun ACT_006_revoke_in_flight_is_unknown_success_needs_observation() {
        val revoked = transition(dispatched(), ActionEvent.AuthorizationDenied(t, "revoked"))
        assertEquals(ActionPhase.UNKNOWN, revoked.actionPhase)
        val done = completed()
        assertEquals(ActionPhase.COMPLETED, done.actionPhase)
        assertNotEquals(ActionPhase.OBSERVED, done.actionPhase)
        assertFails { domainSuccessWithoutObservation() }
    }

    @Test
    fun ACT_007_command_plan_digest_must_match_authorization() {
        val s = authorized()
        val ok = transition(s, ActionEvent.CommandCreated(t, "cmd-digest", command(planDigest = "plan-1"), "rev-1"))
        assertEquals("cmd-1", ok.command!!.commandId)
        assertFails {
            transition(s, ActionEvent.CommandCreated(t, "cmd-digest", command(planDigest = "other"), "rev-1"))
        }
    }

    @Test
    fun ACT_008_belief_revision_hash_must_match() {
        val s = authorized(beliefRevisionHash = "rev-1")
        val ok = transition(s, ActionEvent.CommandCreated(t, "cmd-digest", command(), "rev-1"))
        val dispatchedOk = transition(ok, ActionEvent.CommandDispatched(t, "disp-1", "rev-1"))
        assertEquals(ActionPhase.DISPATCHED, dispatchedOk.actionPhase)
        val stale = transition(s, ActionEvent.CommandCreated(t, "cmd-digest", command(), "rev-stale"))
        assertEquals(PlanPhase.STALE_BELIEF, stale.planPhase)
        assertNotEquals(ActionPhase.DISPATCHED, stale.actionPhase)
    }

    @Test
    fun ACT_009_expired_authorization_cannot_produce_command() {
        val s = authorized()
        val fresh = transition(s, ActionEvent.CommandCreated(t.plusSeconds(1), "cmd-digest", command(), "rev-1"))
        assertEquals(ActionPhase.CREATED, fresh.actionPhase)
        assertEquals(PlanPhase.AUTHORIZED, fresh.planPhase)
        val expired = transition(s, ActionEvent.CommandCreated(t.plusSeconds(120), "cmd-digest", command(), "rev-1"))
        assertEquals(PlanPhase.EXPIRED, expired.planPhase)
        assertEquals(null, expired.command)
    }

    @Test
    fun ACT_010_idempotency_key_required() {
        val s = authorized()
        val ok = command(idempotencyKey = "idem-cmd")
        assertEquals("idem-cmd", ok.idempotencyKey)
        transition(s, ActionEvent.CommandCreated(t, ok.commandDigest, ok, "rev-1"))
        assertFails {
            Command("cmd-x", "plan-1", "auth-1", "", "digest")
        }
    }

    @Test
    fun ACT_011_compensation_is_a_new_action_chain() {
        val original = completed()
        val (parent, child) = requestCompensation(original, t, "comp-plan")
        assertEquals(CompensationPhase.REQUESTED, parent.compensationPhase)
        assertEquals(original.events, parent.events.take(original.events.size))
        assertTrue(parent.events.size > original.events.size)
        assertEquals(original.actionId, child.originActionId)
        assertNotEquals(original.actionId, child.actionId)
        assertEquals(PlanPhase.PROPOSED, child.planPhase)
        assertFails { rewriteActionHistory() }
    }

    @Test
    fun ACT_012_unknown_is_terminal_until_evidence() {
        val unknown = transition(dispatched(), ActionEvent.TimeoutObserved(t, "to-1"))
        assertEquals(ActionPhase.UNKNOWN, unknown.actionPhase)
        val still = transition(unknown, ActionEvent.TimeoutObserved(t.plusSeconds(1), "to-2"))
        assertEquals(ActionPhase.UNKNOWN, still.actionPhase)
        val observed = transition(unknown, ActionEvent.ObservationLinked(t, "accepted-later"))
        assertEquals(ActionPhase.OBSERVED, observed.actionPhase)
        val contradicted = transition(unknown, ActionEvent.ContradictionLinked(t, "accepted-contra"))
        assertEquals(ActionPhase.CONTRADICTED, contradicted.actionPhase)
        assertFails { unknownDoesNotSucceed() }
        assertFails {
            transition(unknown, ActionEvent.ExecutorCompleted(t, "rcpt-late"))
        }
    }

    @Test
    fun architecture_action_is_blind_and_has_no_now() {
        val production = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("a3.core.action")
        noClasses()
            .that().resideInAPackage("a3.core.action..")
            .should().dependOnClassesThat()
            .haveSimpleName("BeliefState")
            .check(production)
        noClasses()
            .that().resideInAPackage("a3.core.action..")
            .should().dependOnClassesThat()
            .haveSimpleName("BeliefWriter")
            .check(production)
        noClasses()
            .that().resideInAPackage("a3.core.action..")
            .should().callMethod(Instant::class.java, "now")
            .check(production)
        noClasses()
            .that().resideInAPackage("a3.core.action..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "a3.core.runtime..",
                "a3.prediction..",
                "a3.projection..",
                "a3.a3ui..",
                "a3.renderers..",
                "a3.intent..",
                "a3.adapters.."
            )
            .check(production)
        val main = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(main.isNotEmpty())
        for (file in main) {
            val text = file.readText()
            assertFalse(text.contains("Instant.now"), file.path)
            assertFalse(text.contains("now()"), file.path)
            assertFalse(text.contains("BeliefState"), file.path)
            assertFalse(text.contains("BeliefState.apply"), file.path)
            assertFalse(Regex("""\bevaluate\s*\(""").containsMatchIn(text), file.path)
        }
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("project(\":core:json\")"))
        assertFalse(gradle.contains("project(\":core:runtime\")"))
        assertFalse(gradle.contains("project(\":core:admission\")"))
        assertFalse(gradle.contains(":adapters"))
    }
}
