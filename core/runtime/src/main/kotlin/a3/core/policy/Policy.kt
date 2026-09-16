// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.policy

import a3.core.model.TrustTier
import java.util.TreeMap

enum class PolicyDecision { ALLOW, CONFIRM, DENY }

class Policy(
    rules: Map<TrustTier, PolicyDecision> = mapOf(
        TrustTier.READ to PolicyDecision.ALLOW,
        TrustTier.PREPARE to PolicyDecision.ALLOW,
        TrustTier.COMMIT to PolicyDecision.CONFIRM,
        TrustTier.IRREVERSIBLE to PolicyDecision.CONFIRM
    )
) {
    private val rules: Map<TrustTier, PolicyDecision> = TreeMap(rules)

    fun evaluate(tier: TrustTier, @Suppress("UNUSED_PARAMETER") reversible: Boolean): PolicyDecision {
        if (tier == TrustTier.IRREVERSIBLE) return PolicyDecision.CONFIRM
        return rules[tier] ?: PolicyDecision.DENY
    }
}
