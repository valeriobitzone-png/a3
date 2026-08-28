package a3.core.trust

import a3.core.model.TrustGrant
import a3.core.model.TrustTier
import java.time.Instant

class TrustGate {
    fun allows(grant: TrustGrant?, tier: TrustTier, now: Instant): Boolean {
        if (tier == TrustTier.READ || tier == TrustTier.PREPARE) return true
        if (grant == null || !grant.granted) return false
        if (grant.expiresAt != null && !now.isBefore(grant.expiresAt)) return false
        return grant.tier.ordinal >= tier.ordinal
    }
}
