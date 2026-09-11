package a3.agent.travel

import a3.adapters.mcp.McpCapabilityExecutor
import a3.adapters.mcp.McpToolCatalog
import a3.agent.admit.AgentAdmission
import a3.agent.admit.AdmissionView
import a3.agent.model.ActivityKind
import a3.core.admission.AdmissionPolicy
import a3.core.admission.SourceId
import a3.core.admission.SourceType
import a3.core.model.Capability
import a3.core.world.api.Claim
import java.time.Instant

/**
 * In-test MCP capability graph for scenario A. Not a live transport API.
 */
object TravelCatalog {
    const val SERVER = "agent-fixtures"

    fun capabilities(at: Instant): Map<String, Capability> {
        val exp = at.plusSeconds(3600)
        return mapOf(
            "transport.rail" to cap(
                "transport.rail",
                listOf(
                    Claim("transport.rail.title", "Frecciarossa Roma–Milano 06:00–08:55", 0.95, "train-fixture", at, exp),
                    Claim("transport.rail.price", "49.90 EUR", 0.95, "train-fixture", at, exp),
                    Claim("transport.rail.mode", "train", 1.0, "train-fixture", at, exp)
                )
            ),
            "transport.air" to cap(
                "transport.air",
                listOf(
                    Claim("transport.air.title", "Volo FCO–LIN 07:10–08:25", 0.70, "flight-fixture", at, exp),
                    Claim("transport.air.price", "86.00 EUR", 0.70, "flight-fixture", at, exp),
                    Claim("transport.air.mode", "flight", 0.70, "flight-fixture", at, exp)
                )
            ),
            "transport.car" to cap(
                "transport.car",
                listOf(
                    Claim("transport.car.title", "Auto A1 Roma–Milano ~5h20", 0.25, "drive-fixture", at, exp),
                    Claim("transport.car.price", "fuel+toll ~45 EUR", 0.25, "drive-fixture", at, exp),
                    Claim("transport.car.mode", "car", 0.25, "drive-fixture", at, exp)
                )
            ),
            "hotel.central" to cap(
                "hotel.central",
                listOf(
                    Claim("hotel.central.title", "Hotel Centrale Milano", 1.0, "hotel-fixture", at, exp),
                    Claim("hotel.central.price", "89.00 EUR", 1.0, "hotel-fixture", at, exp)
                )
            ),
            "hotel.navigli" to cap(
                "hotel.navigli",
                listOf(
                    Claim("hotel.navigli.title", "B&B Navigli (listing incerto)", 0.4, "hotel-model", at, exp),
                    Claim("hotel.navigli.price", "72.00 EUR", 0.4, "hotel-model", at, exp)
                )
            ),
            "hotel.unlisted" to cap(
                "hotel.unlisted",
                listOf(
                    Claim("hotel.unlisted.title", "Alloggio senza fonte prezzo", 0.0, "unattributed", at, exp),
                    Claim("hotel.unlisted.price", "60 EUR", 0.0, "unattributed", at, exp)
                )
            ),
            "calendar.free" to cap(
                "calendar.free",
                listOf(
                    Claim("calendar.free.title", "Libero 08:00–09:30 (calendario locale)", 1.0, "local-calendar", at, exp),
                    Claim("calendar.free.slot", "09:00", 1.0, "local-calendar", at, exp)
                )
            ),
            "calendar.conflict" to cap(
                "calendar.conflict",
                listOf(
                    Claim("calendar.conflict.title", "Possibile overlap 09:00 standup", 0.55, "local-calendar", at, exp),
                    Claim("calendar.conflict.slot", "09:00", 0.55, "local-calendar", at, exp)
                )
            ),
            "calendar.travel" to cap(
                "calendar.travel",
                listOf(
                    Claim("calendar.travel.title", "Tempo viaggio OSM-like 3h20 rail", 0.9, "osm-fixture", at, exp),
                    Claim("calendar.travel.slot", "05:40 departure", 0.9, "osm-fixture", at, exp)
                )
            )
        )
    }

    fun catalog(at: Instant): McpToolCatalog = McpToolCatalog(capabilities(at))

    fun executor(at: Instant): McpCapabilityExecutor = McpCapabilityExecutor(catalog(at), SERVER)

    fun query(
        kind: ActivityKind,
        at: Instant,
        policy: AdmissionPolicy,
        admitted: MutableSet<Pair<SourceId, String>>
    ): List<AdmissionView> {
        val mcp = executor(at)
        val caps = capabilities(at)
        val ids = when (kind) {
            ActivityKind.TRANSPORT -> listOf("transport.rail", "transport.air", "transport.car")
            ActivityKind.HOTEL -> listOf("hotel.central", "hotel.navigli", "hotel.unlisted")
            ActivityKind.CALENDAR -> listOf("calendar.free", "calendar.conflict", "calendar.travel")
            ActivityKind.PURCHASE -> emptyList()
        }
        return ids.map { id ->
            val cap = caps.getValue(id)
            val called = mcp.call(cap, at)
            val profile = if (id == "hotel.navigli") {
                called.confidenceProfile.copy(sourceType = SourceType.GROUNDED_BY_MODEL, opaque = "fixture-uncertain")
            } else called.confidenceProfile
            val candidate = called.copy(confidenceProfile = profile)
            AgentAdmission.consider(candidate, policy, at, admitted)
        }
    }

    private fun cap(id: String, effects: List<Claim>) = Capability(
        id = id,
        name = id,
        preconditions = emptyList(),
        effects = effects,
        reversible = true
    )
}
