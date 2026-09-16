// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.agent.plan

import a3.agent.model.ActivityKind
import a3.agent.model.ProposedPlan
import a3.core.json.CanonicalJson
import java.security.MessageDigest
import java.util.Locale

/**
 * Deterministic rule-based plan decomposition. No language-model orchestrator. Same text → same plan.
 */
object PlanDecomposer {
    private val PURCHASE = Regex("(?i)\\b(compra|acquista|buy|cuffie|headphones)\\b")
    private val APPOINTMENT = Regex("(?i)\\bappuntamento\\b")
    private val FROM = Regex("(?i)\\bda\\s+([A-ZÀ-ÿ][\\p{L}'-]+)")
    private val TO = Regex("(?i)(?:^|[\\s,])a\\s+([A-ZÀ-ÿ][\\p{L}'-]+)")
    private val TIME = Regex("(?i)\\balle\\s+(\\d{1,2}:\\d{2})")
    private val BUDGET = Regex("(?i)\\bsotto\\s+(\\d+)\\s*€?")
    private val QUERY_WORDS = Regex("(?i)\\b(cuffie|headphones|libro|book)\\b")

    fun decompose(intentText: String): ProposedPlan {
        val raw = intentText.trim()
        require(raw.isNotBlank()) { "intent must be non-blank" }
        val purchase = PURCHASE.containsMatchIn(raw) && !APPOINTMENT.containsMatchIn(raw)
        val activities = if (purchase) {
            listOf(ActivityKind.PURCHASE)
        } else {
            listOf(ActivityKind.TRANSPORT, ActivityKind.HOTEL, ActivityKind.CALENDAR)
        }
        val fromCity = FROM.find(raw)?.groupValues?.get(1)
        val toCity = TO.find(raw)?.groupValues?.get(1)
        val time = TIME.find(raw)?.groupValues?.get(1)
        val budget = BUDGET.find(raw)?.groupValues?.get(1)?.toInt()
        val query = QUERY_WORDS.find(raw)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
            ?: if (purchase) words(raw).drop(1).firstOrNull() else null
        val intentDigest = sha256(CanonicalJson.bytes(mapOf("intent" to raw)))
        val planDigest = sha256(
            CanonicalJson.bytes(
                mapOf(
                    "activities" to activities.map { it.name },
                    "budgetEur" to budget,
                    "fromCity" to fromCity,
                    "intent" to raw,
                    "query" to query,
                    "time" to time,
                    "toCity" to toCity
                )
            )
        )
        return ProposedPlan(
            intentText = raw,
            intentDigest = intentDigest,
            planDigest = planDigest,
            activities = activities,
            fromCity = fromCity,
            toCity = toCity,
            time = time,
            query = query,
            budgetEur = budget
        )
    }

    private fun words(raw: String): List<String> =
        raw.lowercase(Locale.ROOT).split(Regex("[^\\p{L}0-9]+")).filter { it.isNotBlank() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
