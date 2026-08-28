package a3.core.model

import java.time.Instant

typealias Fact = a3.core.world.api.Fact
typealias Observation = a3.core.world.Observation
typealias Event = a3.core.world.Event

data class Intent(
    val id: String,
    val expression: String,
    val modality: String,
    val source: String,
    val confidence: Double,
    val goalRef: String? = null
) {
    init {
        require(confidence in 0.0..1.0)
        require(modality in MODALITIES) { "invalid modality: $modality" }
        require(source in INTENT_SOURCES) { "invalid source: $source" }
    }

    companion object {
        val MODALITIES = listOf("voice", "text", "gesture", "context")
        val INTENT_SOURCES = listOf("explicit", "inferred")
    }
}

data class Goal(
    val id: String,
    val intentRef: String,
    val desiredState: List<Fact>,
    val constraints: List<Constraint> = emptyList(),
    val deadline: Instant? = null,
    val priority: Double = 0.0
) {
    init {
        require(priority in 0.0..1.0) { "priority must be in [0,1]" }
    }
}

data class Constraint(val key: String, val op: String, val value: Any?)

enum class TrustTier {
    READ, PREPARE, COMMIT, IRREVERSIBLE;

    fun wire(): String = name.lowercase()

    companion object {
        fun fromWire(value: String): TrustTier = valueOf(value.uppercase())
    }
}

enum class Risk {
    LOW, MEDIUM, HIGH;

    fun wire(): String = name.lowercase()
}

data class Cost(
    val money: Double = 0.0,
    val timeMin: Double = 0.0,
    val energy: String = "low"
) {
    init {
        require(energy in ENERGY) { "invalid energy: $energy" }
    }

    operator fun plus(other: Cost): Cost = Cost(
        money = money + other.money,
        timeMin = timeMin + other.timeMin,
        energy = maxEnergy(energy, other.energy)
    )

    companion object {
        val ENERGY = listOf("low", "med", "high")
        private val RANK = mapOf("low" to 0, "med" to 1, "high" to 2)
        fun maxEnergy(a: String, b: String): String =
            if ((RANK[a] ?: 0) >= (RANK[b] ?: 0)) a else b
    }
}

data class Capability(
    val id: String,
    val name: String,
    val preconditions: List<Fact>,
    val inputs: List<String> = emptyList(),
    val effects: List<Fact>,
    val cost: Cost = Cost(),
    val risk: Risk = Risk.LOW,
    val reversible: Boolean = true,
    val reliability: Double = 1.0,
    val privacy: String = "local-first",
    val deps: List<String> = emptyList(),
    val adapter: String? = null
) {
    init {
        require(reliability in 0.0..1.0)
        require(privacy in PRIVACY) { "invalid privacy: $privacy" }
    }

    companion object {
        val PRIVACY = listOf("local-first", "device", "account", "third-party")
    }
}

data class CapabilityGraph(
    val capabilities: Map<String, Capability>
)

data class PlanStep(
    val seq: Int,
    val capability: String,
    val trustTier: TrustTier,
    val reversible: Boolean
)

data class ExpectedOutcome(
    val goalRef: String,
    val facts: List<Fact>
)

data class Plan(
    val id: String,
    val goalRef: String,
    val steps: List<PlanStep>,
    val expectedOutcome: ExpectedOutcome,
    val totalCost: Cost = Cost(),
    val status: String = "proposed"
) {
    init {
        require(status in STATUSES) { "invalid plan status: $status" }
    }

    companion object {
        val STATUSES = listOf("proposed", "approved", "executing", "done", "aborted")
    }
}

data class ObservedOutcome(
    val executionRef: String,
    val facts: List<Fact>
)

data class Outcome(
    val id: String,
    val goalRef: String,
    val stateDelta: List<Fact>,
    val confidence: Double,
    val status: String,
    val cost: Cost? = null,
    val reversibility: String? = null
) {
    init {
        require(confidence in 0.0..1.0)
        require(status in STATUSES) { "invalid outcome status: $status" }
        if (reversibility != null) require(reversibility in REVERSIBILITY)
    }

    companion object {
        val STATUSES = listOf("proposed", "achieved", "failed")
        val REVERSIBILITY = listOf("reversible", "irreversible")
    }
}

data class TrustGrant(
    val id: String,
    val capabilityRef: String,
    val tier: TrustTier,
    val granted: Boolean,
    val expiresAt: Instant? = null,
    val scope: String = "single",
    val receipt: String = "",
    val revocable: Boolean = true,
    val ttl: Int? = null
)

data class Prediction(
    val id: String,
    val contextRef: String,
    val states: List<Map<String, Any?>> = emptyList(),
    val committed: String? = null
)

data class Projection(
    val id: String,
    val stateRef: Long,
    val formFactor: String,
    val uiStateRef: String,
    val interactionRequirements: List<String> = emptyList()
) {
    init {
        require(formFactor in FORM_FACTORS) { "invalid form_factor: $formFactor" }
    }

    companion object {
        val FORM_FACTORS = listOf("phone", "tablet", "car", "glasses", "desktop")
    }
}

data class WorldStateSnapshot(
    val version: Long,
    val t: Instant,
    val facts: List<Fact>,
    val schema: String = "a3/state",
    val transitions: List<StateTransition> = emptyList()
)

data class StateTransition(
    val from: Long,
    val to: Long,
    val eventId: String
)
