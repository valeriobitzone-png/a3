package a3.broker

import org.biscuitsec.biscuit.crypto.KeyPair
import org.biscuitsec.biscuit.token.Biscuit
import org.biscuitsec.biscuit.token.builder.Block
import java.security.SecureRandom
import java.time.Instant
import java.util.Date

class AttenuatedAccess(rootHex: String) {
    private val root = KeyPair(rootHex)

    fun bind(
        toolScope: List<String>,
        until: Instant,
        consentPresentationHash: String,
        restingRevision: String
    ): BoundGrant {
        val base = Biscuit.builder(root)
            .add_authority_fact("user(\"a3-operator\")")
            .build()
        val block = Block()
        for (tool in toolScope) {
            block.add_fact("scope(\"$tool\")")
        }
        block.add_fact("consent_presentation(\"$consentPresentationHash\")")
        block.add_fact("resting_revision(\"$restingRevision\")")
        block.add_check("check if operation(\$op), scope(\$op)")
        block.expiration_date(Date.from(until))
        val token = base.attenuate(SecureRandom(), KeyPair(SecureRandom()), block)
        return BoundGrant(token.serialize_b64url(), token.print())
    }

    fun allows(tokenB64: String, tool: String): Boolean {
        val token = Biscuit.from_b64url(tokenB64, root.public_key())
        val authorizer = token.authorizer()
        authorizer.add_fact("operation(\"$tool\")")
        authorizer.set_time()
        authorizer.allow()
        return runCatching { authorizer.authorize(); true }.getOrDefault(false)
    }
}

data class BoundGrant(
    val token: String,
    val printed: String
)
