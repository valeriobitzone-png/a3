package a3.agent

import a3.agent.admit.AgentAdmission
import a3.agent.gate.OutboundGate
import a3.agent.gate.UrlOpener
import a3.agent.model.ActivityKind
import a3.agent.model.AgentOutcome
import a3.agent.model.AgentSession
import a3.agent.model.ChoiceKind
import a3.agent.model.ChoiceOutcome
import a3.agent.model.RetailerSpec
import a3.agent.model.SurfaceGroup
import a3.agent.model.SurfaceOption
import a3.agent.plan.PlanDecomposer
import a3.agent.preview.OpenGraphPreviewFetcher
import a3.agent.sources.OpenLibraryClient
import a3.agent.surface.AgentSurfaces
import a3.agent.travel.TravelCatalog
import a3.core.action.ActionEvent
import a3.core.action.ActionState
import a3.core.action.Authorization
import a3.core.action.Command
import a3.core.action.transition
import a3.core.admission.SourceId
import a3.broker.Broker
import java.time.Instant

class AgentRuntime(
    private val clock: () -> Instant,
    private val broker: Broker,
    private val preview: OpenGraphPreviewFetcher,
    private val retailers: List<RetailerSpec>,
    opener: UrlOpener,
    private val openLibrary: OpenLibraryClient? = null,
    private val includeOpenLibraryOnPurchase: Boolean = false
) {
    val gate = OutboundGate(broker, opener)
    private val admitted = mutableSetOf<Pair<SourceId, String>>()
    private val policy = AgentAdmission.policy()
    private var armed = false

    fun arm() {
        if (armed) return
        gate.arm()
        armed = true
    }

    fun propose(intentText: String): AgentSession {
        arm()
        val now = clock()
        val plan = PlanDecomposer.decompose(intentText)
        var action = ActionState("act-${plan.intentDigest.take(12)}")
        action = transition(action, ActionEvent.PlanProposed(now, plan.intentDigest))
        action = transition(action, ActionEvent.PlanValidated(now, plan.planDigest))
        val groups = plan.activities.map { kind ->
            SurfaceGroup(kind, optionsFor(kind, plan.query ?: plan.intentText, now))
        }
        return AgentSession(plan, action, groups)
    }

    fun choose(session: AgentSession, optionId: String, kind: ChoiceKind): ChoiceOutcome {
        val option = session.groups.flatMap { it.options }.first { it.id == optionId }
        val now = clock()
        return when (kind) {
            ChoiceKind.DETAIL -> {
                require(option.reversible) { "detail requires reversible option" }
                val direct = gate.readDirect()
                val action = succeed(session.action, now, session.plan.planDigest)
                ChoiceOutcome(
                    kind = kind,
                    gated = false,
                    opened = false,
                    openedUrl = null,
                    brokerLine = direct.brokerLine,
                    consentText = null,
                    action = action,
                    outcome = AgentOutcome.WORKED,
                    tree = AgentSurfaces.outcomeTree(action, AgentOutcome.WORKED, now)
                )
            }
            ChoiceKind.OUTBOUND -> {
                val url = option.url ?: error("outbound requires url")
                val retailer = option.retailer ?: "retailer"
                val result = gate.open(url, retailer)
                val outcome = if (result.opened) AgentOutcome.WORKED else AgentOutcome.DID_NOT_WORK
                val action = if (result.opened) {
                    succeed(session.action, now, session.plan.planDigest)
                } else {
                    deny(session.action, now)
                }
                ChoiceOutcome(
                    kind = kind,
                    gated = true,
                    opened = result.opened,
                    openedUrl = if (result.opened) url else null,
                    brokerLine = result.brokerLine,
                    consentText = result.consentText,
                    action = action,
                    outcome = outcome,
                    tree = AgentSurfaces.outcomeTree(action, outcome, now)
                )
            }
            ChoiceKind.BOOK -> {
                val result = gate.reserve()
                val outcome = if (result.allowed) AgentOutcome.WORKED else AgentOutcome.DID_NOT_WORK
                val action = if (result.allowed) {
                    succeed(session.action, now, session.plan.planDigest)
                } else {
                    deny(session.action, now)
                }
                ChoiceOutcome(
                    kind = kind,
                    gated = true,
                    opened = false,
                    openedUrl = null,
                    brokerLine = result.brokerLine,
                    consentText = result.consentText,
                    action = action,
                    outcome = outcome,
                    tree = AgentSurfaces.outcomeTree(action, outcome, now)
                )
            }
        }
    }

    fun unknownTimeout(session: AgentSession): ChoiceOutcome {
        val now = clock()
        val dispatched = dispatch(session.action, now, session.plan.planDigest)
        val action = transition(dispatched, ActionEvent.TimeoutObserved(now, "to-agent"))
        val outcome = AgentOutcome.UNKNOWN
        return ChoiceOutcome(
            kind = ChoiceKind.BOOK,
            gated = true,
            opened = false,
            openedUrl = null,
            brokerLine = null,
            consentText = null,
            action = action,
            outcome = outcome,
            tree = AgentSurfaces.outcomeTree(action, outcome, now)
        )
    }

    private fun optionsFor(kind: ActivityKind, query: String, now: Instant): List<SurfaceOption> {
        return when (kind) {
            ActivityKind.PURCHASE -> purchase(query, now)
            else -> TravelCatalog.query(kind, now, policy, admitted).mapIndexed { i, view ->
                AgentSurfaces.optionFromAdmission(kind, view, now, i)
            }
        }
    }

    private fun purchase(query: String, now: Instant): List<SurfaceOption> {
        val q = query.ifBlank { "cuffie" }
        val options = ArrayList<SurfaceOption>()
        for (retailer in retailers) {
            val previewDoc = preview.fetch(
                url = retailer.searchUrl,
                retailer = retailer.id,
                query = q,
                amazonUnreliable = retailer.amazonUnreliable
            )
            val admittedPreview = AgentAdmission.consider(
                AgentAdmission.candidate(
                    source = "web-document",
                    id = "preview-${retailer.id}-$now",
                    at = now,
                    claims = AgentSurfaces.optionFromPreview(previewDoc, now).claims
                ),
                policy,
                now,
                admitted
            )
            val option = AgentSurfaces.optionFromPreview(previewDoc, now).let { built ->
                if (admittedPreview.held) {
                    AgentSurfaces.composeOption(
                        id = built.id,
                        group = built.group,
                        title = built.title,
                        subtitle = built.subtitle,
                        price = built.price,
                        imageUrl = built.imageUrl,
                        url = built.url,
                        retailer = built.retailer,
                        previewLevel = built.previewLevel,
                        sourceKind = built.sourceKind,
                        amazonUnreliable = built.amazonUnreliable,
                        reversible = built.reversible,
                        outbound = built.outbound,
                        claims = built.claims,
                        primary = built.claims.firstOrNull { it.k.endsWith(".price") } ?: built.claims.first(),
                        admissionHeld = true,
                        asOf = now
                    )
                } else built
            }
            options += option
        }
        if (includeOpenLibraryOnPurchase) {
            val hits = openLibrary?.search(q, 1)
            if (hits != null) {
                options += AgentSurfaces.optionFromLibrary(
                    title = hits.docs.first().title,
                    coverUrl = hits.docs.first().coverUrl,
                    apiTimestamp = hits.apiTimestamp,
                    asOf = now,
                    index = 0
                )
            }
        }
        return options
    }

    private fun deny(state: ActionState, now: Instant): ActionState =
        transition(state, ActionEvent.AuthorizationDenied(now, "no consent"))

    private fun succeed(state: ActionState, now: Instant, planDigest: String): ActionState {
        val dispatched = dispatch(state, now, planDigest)
        return transition(dispatched, ActionEvent.ExecutorCompleted(now, "rcpt-$now"))
    }

    private fun dispatch(state: ActionState, now: Instant, planDigest: String): ActionState {
        val auth = Authorization(
            authorizationId = "auth-$now",
            planDigest = planDigest,
            beliefRevisionHash = "rev-1",
            principal = "user",
            scopes = listOf("execute"),
            resource = "agent",
            maximumImpact = "outbound",
            expiresAt = now.plusSeconds(600),
            idempotencyKey = "idem-auth-$now",
            policyId = "agent-surface",
            policyVersion = "1",
            policyDigest = "agent-surface-1"
        )
        var s = transition(state, ActionEvent.AuthorizationGranted(now, "auth-digest", auth))
        val command = Command(
            commandId = "cmd-$now",
            planDigest = planDigest,
            authorizationId = auth.authorizationId,
            idempotencyKey = "idem-cmd-$now",
            commandDigest = "cmd-digest-$now"
        )
        s = transition(s, ActionEvent.CommandCreated(now, command.commandDigest, command, "rev-1"))
        s = transition(s, ActionEvent.CommandDispatched(now, "disp-$now", "rev-1"))
        return s
    }
}
