package a3.broker

import a3.core.action.ActionEvent
import a3.core.action.ActionPhase
import a3.core.action.ActionState
import a3.core.action.Authorization
import a3.core.action.Command
import a3.core.action.ExecutionReceipt
import a3.core.action.transition
import a3.core.admission.CLAIM_ARRAY_SCHEMA
import a3.core.admission.ConfidenceProfile
import a3.core.admission.Esito
import a3.core.admission.ObservationCandidate
import a3.core.admission.SourceId
import a3.core.admission.SourceType
import a3.core.admission.acceptedObservation
import a3.core.admission.admissionPolicy
import a3.core.admission.evaluate
import a3.core.admission.schemaResourceBytes
import a3.core.json.CanonicalJson
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class RunLine(val tool: String, val sent: Boolean, val result: String, val reason: String?)

class Broker(
    val home: Path,
    private val secrets: RootSecrets,
    private val clock: () -> Instant,
    private val askConsent: (String) -> Boolean,
    private val timeout: Duration = Duration.ofSeconds(2)
) {
    private val lock = Any()
    private val register = Register(home)
    private val admitted = mutableSetOf<Pair<SourceId, String>>()
    private val inflight = ConcurrentHashMap<String, Inflight>()
    private val policy = admissionPolicy(
        policyId = "broker-permissive",
        policyVersion = "1",
        ttlSeconds = null,
        schemas = mapOf(CLAIM_ARRAY_SCHEMA to schemaResourceBytes(CLAIM_ARRAY_SCHEMA))
    )
    private var access: AttenuatedAccess? = null
    private var tools: LocalTools? = null
    private var baseUrl: String? = null
    private val client = ToolsClient(timeout)
    var lastBoundPrint: String? = null
        private set

    fun init(): String = synchronized(lock) {
        Files.createDirectories(home)
        access = AttenuatedAccess(secrets.loadOrCreate())
        Files.writeString(home.resolve("ready"), "broker\n")
        "broker ready\nirreversible actions stop here until you allow them"
    }

    fun connect(url: String): String = synchronized(lock) {
        ensureAccess()
        if (url.startsWith("mcp://filesystem") || url.contains("filesystem")) {
            Files.writeString(home.resolve("server.txt"), "mcp://filesystem")
        } else {
            Files.writeString(home.resolve("server.txt"), url.trim())
        }
        register.append(
            logLine(clock(), "you", """op: connected filesystem""")
        )
        "connected to filesystem"
    }

    fun flag(raw: String): String = synchronized(lock) {
            val toolsNamed = raw.substringAfter(":", raw)
            .split(',')
            .map { it.trim().trimEnd(',').lowercase() }
            .filter { it.isNotBlank() }
        Files.writeString(home.resolve("flags.txt"), toolsNamed.joinToString("\n") + "\n")
        register.append(
            logLine(clock(), "you", "op: flagged ${toolsNamed.joinToString(", ")}")
        )
        "irreversible: ${toolsNamed.joinToString(", ")}"
    }

    fun allow(intent: String, ttl: Duration): String = synchronized(lock) {
        val now = clock()
        val flagged = flags()
        val named = toolsNamedIn(intent)
        val scope = when {
            named.isNotEmpty() -> named
            flagged.isNotEmpty() -> flagged
            else -> listOf("send")
        }
        val screen = consentScreen(intent, scope, durationLabel(ttl))
        if (!askConsent(screen.text)) {
            return "did not allow\n${screen.text}"
        }
        currentGrant()?.let { endGrant(it, "replaced by a new permission", now) }
        val revision = restingRevision(now)
        val id = "g${now.toEpochMilli()}"
        val grant = Grant(
            id = id,
            intent = intent,
            until = now.plus(ttl),
            shownAs = screen.presentationHash,
            revision = revision,
            restingUntil = now.plus(Duration.ofDays(365)),
            scope = scope,
            ended = null
        )
        saveGrant(grant)
        register.append(
            logLine(
                now,
                "you",
                """op: allowed "${grant.intent}" until ${grant.until} shown-as ${grant.shownAs.take(12)}"""
            )
        )
        """allowed "${grant.intent}" until ${grant.until}
shown-as ${grant.shownAs.take(12)}
$id"""
    }

    fun revoke(grantId: String): String = synchronized(lock) {
        val now = clock()
        val grant = loadGrants().firstOrNull { it.id == grantId } ?: return "unknown permission"
        endGrant(grant, "permission pulled", now)
        denylist().let { ids ->
            Files.writeString(home.resolve("denylist.txt"), (ids + grantId).joinToString("\n") + "\n")
        }
        baseUrl?.let { client.revoke(it, grantId) }
        inflight.values.filter { it.grantId == grantId }.forEach { flight ->
            flight.unknown = "permission pulled"
        }
        "ended permission \"${grant.intent}\""
    }

    fun expireRestingFact() = synchronized(lock) {
        val now = clock()
        currentGrant()?.let { grant ->
            saveGrant(grant.copy(restingUntil = now.minusSeconds(1)))
        }
    }

    fun setRestingUntil(at: Instant) = synchronized(lock) {
        currentGrant()?.let { saveGrant(it.copy(restingUntil = at)) }
    }

    fun run(agentCmd: String): String {
        val toolsToRun = when (agentCmd.trim()) {
            "my-agent" -> listOf("list", "send")
            else -> listOf(agentCmd.trim().substringBefore(' ').lowercase())
        }
        val lines = toolsToRun.map { invoke(it) }
        return lines.joinToString("\n") { line ->
            val extra = line.reason?.let { "\nreason: $it" } ?: ""
            if (line.sent) "${line.tool}: ${line.result}$extra"
            else "${line.tool}: did not send$extra"
        }
    }

    fun log(): String = register.text()

    fun homeFiles(): List<Path> =
        if (Files.exists(home)) Files.walk(home).use { it.filter { p -> Files.isRegularFile(p) }.toList() } else emptyList()

    fun lastGrant(): Grant? = currentGrant()

    fun local(): LocalTools? = tools

    fun stop() {
        tools?.stop()
    }

    private fun invoke(tool: String): RunLine {
        data class Prepared(
            val grant: Grant?,
            val bound: BoundGrant?,
            val action: ActionState?,
            val flightId: String
        )
        val prepared = synchronized(lock) {
            val now = clock()
            if (tool in flags()) {
                val grant = currentGrant()
                when {
                    grant == null -> return notSent(tool, now, null, "no permission", "did not work")
                    grant.id in denylist() || grant.ended != null ->
                        return notSent(tool, now, grant, grant.ended ?: "permission pulled", "unknown")
                    !now.isBefore(grant.until) -> {
                        endGrant(grant, "time ran out", now)
                        return notSent(tool, now, grant, "time ran out", "unknown")
                    }
                    !now.isBefore(grant.restingUntil) -> {
                        endGrant(grant, "the fact it rested on expired", now)
                        return notSent(tool, now, grant, "the fact it rested on expired", "unknown")
                    }
                    tool !in grant.scope ->
                        return notSent(tool, now, grant, "permission does not cover this", "did not work")
                }
            }
            val grant = currentGrant()
            val bound = if (tool in flags() && grant != null) {
                val boundGrant = ensureAccess().bind(grant.scope, grant.until, grant.shownAs, grant.revision)
                lastBoundPrint = boundGrant.printed
                boundGrant
            } else null
            val action = if (tool in flags() && grant != null) startAction(grant, tool, now) else null
            val flightId = action?.dispatchId ?: "direct-$tool-$now"
            if (action != null && grant != null) {
                inflight[flightId] = Inflight(grant.id, action)
            }
            ensureEndpoint()
            Prepared(grant, bound, action, flightId)
        }
        val reply = client.call(baseUrl!!, tool, prepared.bound?.token, prepared.grant?.id)
        return synchronized(lock) {
            val stamped = clock()
            val after = inflight.remove(prepared.flightId)
            val latest = prepared.grant?.id?.let { id -> loadGrants().firstOrNull { it.id == id } }
            val pulled = after?.unknown
                ?: latest?.ended
                ?: when {
                    latest != null && latest.id in denylist() -> "permission pulled"
                    latest != null && !stamped.isBefore(latest.until) -> "time ran out"
                    latest != null && !stamped.isBefore(latest.restingUntil) -> "the fact it rested on expired"
                    else -> null
                }
            if (pulled != null && prepared.action != null) {
                if (latest != null && latest.ended == null) {
                    endGrant(latest, pulled, stamped)
                }
                val eventReason = pulled
                if (eventReason == "time ran out" || eventReason == "server silent") {
                    transition(prepared.action, ActionEvent.TimeoutObserved(stamped, eventReason))
                } else {
                    transition(prepared.action, ActionEvent.AuthorizationDenied(stamped, eventReason))
                }
                logCall(stamped, tool, latest ?: prepared.grant, sent = true, result = "unknown", reason = eventReason)
                return@synchronized RunLine(tool, sent = true, result = "unknown", reason = eventReason)
            }
            if (reply.timedOut) {
                prepared.action?.let { transition(it, ActionEvent.TimeoutObserved(stamped, "timeout")) }
                logCall(stamped, tool, latest ?: prepared.grant, sent = true, result = "unknown", reason = "time ran out")
                return@synchronized RunLine(tool, sent = true, result = "unknown", reason = "time ran out")
            }
            if (reply.silent) {
                prepared.action?.let { transition(it, ActionEvent.TimeoutObserved(stamped, "silent")) }
                logCall(stamped, tool, latest ?: prepared.grant, sent = true, result = "unknown", reason = "server silent")
                return@synchronized RunLine(tool, sent = true, result = "unknown", reason = "server silent")
            }
            if (tool !in flags()) {
                val result = if (reply.accepted) "worked" else "did not work"
                val reason = if (reply.accepted) null else "server refused"
                logCall(stamped, tool, latest ?: prepared.grant, sent = true, result = result, reason = reason)
                return@synchronized RunLine(tool, sent = true, result = result, reason = reason)
            }
            var state = prepared.action!!
            if (!reply.accepted) {
                state = transition(state, ActionEvent.ExecutorFailed(stamped, "server refused"))
                logCall(stamped, tool, latest ?: prepared.grant, sent = true, result = "did not work", reason = "server refused")
                return@synchronized RunLine(tool, sent = true, result = "did not work", reason = "server refused")
            }
            val receipt = ExecutionReceipt("rcpt-${prepared.flightId}", "cmd-$tool", prepared.flightId, true, stamped)
            state = transition(state, ActionEvent.ExecutorCompleted(receipt.at, receipt.receiptId))
            check(state.actionPhase == ActionPhase.COMPLETED)
            val candidate = observation(tool, stamped)
            val decision = evaluate(candidate, policy, candidate.ingestedAt, admitted)
            if (decision.esito != Esito.ADMIT) {
            logCall(stamped, tool, latest ?: prepared.grant, sent = true, result = "did not work", reason = "observation not accepted")
                return@synchronized RunLine(tool, sent = true, result = "did not work", reason = "observation not accepted")
            }
            val accepted = acceptedObservation(candidate, decision, stamped)
            admitted.add(candidate.source to candidate.id)
            state = transition(state, ActionEvent.ObservationLinked(stamped, accepted.candidate.id))
            check(state.actionPhase == ActionPhase.OBSERVED)
            logCall(stamped, tool, latest ?: prepared.grant, sent = true, result = "worked", reason = null)
            RunLine(tool, sent = true, result = "worked", reason = null)
        }
    }

    private fun notSent(
        tool: String,
        now: Instant,
        grant: Grant?,
        reason: String,
        result: String
    ): RunLine {
        logCall(now, tool, grant, sent = false, result = result, reason = reason)
        return RunLine(tool, sent = false, result = result, reason = reason)
    }

    private fun logCall(
        at: Instant,
        tool: String,
        grant: Grant?,
        sent: Boolean,
        result: String,
        reason: String?
    ) {
        val perm = if (tool in flags()) grant?.intent ?: "direct" else "direct"
        val reasonBit = if (reason != null) " reason: $reason" else if (result == "unknown") " reason: unavailable" else ""
        val verb = if (sent) "ran $tool" else "did not send $tool"
        register.append(
            logLine(at, "you", """call: $verb with permission "$perm" result: $result$reasonBit""")
        )
    }

    private fun startAction(grant: Grant, tool: String, now: Instant): ActionState {
        val plan = sha256Hex(CanonicalJson.bytes(mapOf("intent" to grant.intent, "tool" to tool)))
        val auth = Authorization(
            authorizationId = "auth-${grant.id}",
            planDigest = plan,
            beliefRevisionHash = grant.revision,
            principal = "you",
            scopes = grant.scope,
            resource = tool,
            maximumImpact = "irreversible",
            expiresAt = grant.until,
            idempotencyKey = "idem-${grant.id}-$tool-$now",
            policyId = "broker-gate",
            policyVersion = "1",
            policyDigest = grant.shownAs,
            consentPresentationHash = grant.shownAs
        )
        var state = ActionState("act-${grant.id}-$tool")
        state = transition(state, ActionEvent.PlanProposed(now, plan))
        state = transition(state, ActionEvent.PlanValidated(now, plan))
        state = transition(state, ActionEvent.AuthorizationGranted(now, auth.authorizationId, auth))
        val command = Command(
            commandId = "cmd-$tool",
            planDigest = plan,
            authorizationId = auth.authorizationId,
            idempotencyKey = auth.idempotencyKey,
            commandDigest = plan
        )
        state = transition(state, ActionEvent.CommandCreated(now, command.commandDigest, command, grant.revision))
        state = transition(state, ActionEvent.CommandDispatched(now, "disp-$tool-$now", grant.revision))
        return state
    }

    private fun observation(tool: String, at: Instant): ObservationCandidate {
        val data = listOf(
            mapOf(
                "k" to "tool.$tool",
                "v" to "ok",
                "confidence" to 1.0,
                "source" to "filesystem",
                "observed_at" to at
            )
        )
        return ObservationCandidate(
            source = SourceId("filesystem"),
            id = "obs-$tool-$at",
            occurredAt = at,
            observedAt = at,
            ingestedAt = at,
            data = data,
            dataschema = CLAIM_ARRAY_SCHEMA,
            confidenceProfile = ConfidenceProfile(SourceType.DIRECT, "")
        )
    }

    private fun endGrant(grant: Grant, reason: String, now: Instant) {
        saveGrant(grant.copy(ended = reason))
        register.append(
            logLine(now, "system", """op: ended permission "${grant.intent}" reason: $reason""")
        )
    }

    private fun startLocal() {
        if (tools != null) return
        val local = LocalTools(ensureAccess(), setOf("reserve", "send", "delete"))
        baseUrl = local.start()
        tools = local
    }

    private fun ensureEndpoint() {
        if (baseUrl != null) return
        val recorded = home.resolve("server.txt")
        val url = if (Files.exists(recorded)) Files.readString(recorded).trim() else "mcp://filesystem"
        if (url.startsWith("mcp://") || url.contains("filesystem")) startLocal()
        else baseUrl = url
    }

    private fun ensureAccess(): AttenuatedAccess {
        access?.let { return it }
        access = AttenuatedAccess(secrets.loadOrCreate())
        return access!!
    }

    private fun flags(): List<String> {
        val f = home.resolve("flags.txt")
        if (!Files.exists(f)) return emptyList()
        return Files.readAllLines(f).map { it.trim().lowercase() }.filter { it.isNotBlank() }
    }

    private fun denylist(): Set<String> {
        val f = home.resolve("denylist.txt")
        if (!Files.exists(f)) return emptySet()
        return Files.readAllLines(f).map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    private fun currentGrant(): Grant? =
        loadGrants().lastOrNull { it.ended == null }

    private fun loadGrants(): List<Grant> {
        val dir = home.resolve("grants")
        if (!Files.exists(dir)) return emptyList()
        return Files.newDirectoryStream(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) }.map { readGrant(it) }.sortedBy { it.id }
        }
    }

    private fun saveGrant(grant: Grant) {
        val dir = home.resolve("grants")
        Files.createDirectories(dir)
        val body = buildString {
            appendLine("id ${grant.id}")
            appendLine("intent ${grant.intent}")
            appendLine("until ${grant.until}")
            appendLine("shown ${grant.shownAs}")
            appendLine("revision ${grant.revision}")
            appendLine("resting ${grant.restingUntil}")
            appendLine("scope ${grant.scope.joinToString(",")}")
            appendLine("ended ${grant.ended ?: ""}")
        }
        Files.writeString(dir.resolve(grant.id + ".txt"), body)
    }

    private fun readGrant(path: Path): Grant {
        val map = Files.readAllLines(path).associate { line ->
            val sp = line.indexOf(' ')
            line.substring(0, sp) to line.substring(sp + 1)
        }
        return Grant(
            id = map.getValue("id"),
            intent = map.getValue("intent"),
            until = Instant.parse(map.getValue("until")),
            shownAs = map.getValue("shown"),
            revision = map.getValue("revision"),
            restingUntil = Instant.parse(map.getValue("resting")),
            scope = map.getValue("scope").split(',').filter { it.isNotBlank() },
            ended = map["ended"]?.ifBlank { null }
        )
    }

    private fun restingRevision(now: Instant): String =
        sha256Hex(CanonicalJson.bytes(mapOf("resting" to now.toString(), "home" to home.fileName.toString())))
}

data class Grant(
    val id: String,
    val intent: String,
    val until: Instant,
    val shownAs: String,
    val revision: String,
    val restingUntil: Instant,
    val scope: List<String>,
    val ended: String?
)

private class Inflight(
    val grantId: String,
    val action: ActionState,
    var unknown: String? = null
)
