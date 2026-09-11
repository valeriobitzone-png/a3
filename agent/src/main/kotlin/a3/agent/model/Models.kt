package a3.agent.model

import a3.a3ui.engine.ComposedTree
import a3.a3ui.engine.EpistemicFacts
import a3.a3ui.model.EpistemicAxis
import a3.core.action.ActionState
import a3.core.world.api.Claim
import java.time.Instant

enum class ActivityKind {
    TRANSPORT,
    HOTEL,
    CALENDAR,
    PURCHASE
}

enum class PreviewLevel {
    OPENGRAPH,
    TWITTER,
    META,
    MINIMA,
    DEEPLINK;

    fun wire(): String = name.lowercase().replace('_', '-')
}

enum class SourceKind {
    FIXTURE,
    WEB_DOCUMENT,
    PUBLIC_API
}

enum class ChoiceKind {
    DETAIL,
    OUTBOUND,
    BOOK
}

enum class AgentOutcome {
    WORKED,
    DID_NOT_WORK,
    UNKNOWN
}

data class ProposedPlan(
    val intentText: String,
    val intentDigest: String,
    val planDigest: String,
    val activities: List<ActivityKind>,
    val fromCity: String?,
    val toCity: String?,
    val time: String?,
    val query: String?,
    val budgetEur: Int?
)

data class LinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val price: String?,
    val level: PreviewLevel,
    val fetchedAt: Instant?,
    val expiresAt: Instant?,
    val robotsAllowed: Boolean,
    val pageFetched: Boolean,
    val amazonUnreliable: Boolean,
    val fallbackReason: String?,
    val retailer: String,
    val query: String,
    val fromCache: Boolean = false
) {
    init {
        require(!pageFetched || robotsAllowed) {
            "page must not be fetched when robots deny"
        }
    }
}

data class SurfaceOption(
    val id: String,
    val group: ActivityKind,
    val title: String,
    val subtitle: String?,
    val price: String?,
    val imageUrl: String?,
    val url: String?,
    val retailer: String?,
    val previewLevel: PreviewLevel?,
    val sourceKind: SourceKind,
    val amazonUnreliable: Boolean,
    val reversible: Boolean,
    val outbound: Boolean,
    val axis: EpistemicAxis,
    val facts: Map<String, EpistemicFacts>,
    val claims: List<Claim>,
    val tree: ComposedTree,
    val admissionHeld: Boolean
)

data class SurfaceGroup(
    val kind: ActivityKind,
    val options: List<SurfaceOption>
)

data class AgentSession(
    val plan: ProposedPlan,
    val action: ActionState,
    val groups: List<SurfaceGroup>
)

data class ChoiceOutcome(
    val kind: ChoiceKind,
    val gated: Boolean,
    val opened: Boolean,
    val openedUrl: String?,
    val brokerLine: String?,
    val consentText: String?,
    val action: ActionState,
    val outcome: AgentOutcome,
    val tree: ComposedTree
)

data class RetailerSpec(
    val id: String,
    val label: String,
    val searchUrl: String,
    val amazonUnreliable: Boolean = false
)

const val HONEST_USER_AGENT =
    "A3-Agent/0.1 (+https://a3.local/agent; personal surface preview; respects robots.txt)"
