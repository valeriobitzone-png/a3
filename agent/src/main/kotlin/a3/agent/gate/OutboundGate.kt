package a3.agent.gate

import a3.broker.Broker
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

fun interface UrlOpener {
    fun open(url: String): Boolean
}

data class GateResult(
    val opened: Boolean,
    val brokerLine: String?,
    val consentText: String?,
    val allowed: Boolean
)

/**
 * Outbound (open URL) and irreversible (reserve) go through broker.
 * Gate default ON via `flag irreversible: open, reserve`.
 */
class OutboundGate(
    private val broker: Broker,
    private val opener: UrlOpener,
    private val ttl: Duration = Duration.ofMinutes(5)
) {
    private val lastConsent = AtomicReference<String?>(null)

    fun arm() {
        broker.init()
        broker.connect("mcp://filesystem")
        broker.flag("irreversible: open, reserve")
    }

    fun lastConsentText(): String? = lastConsent.get()

    fun rememberConsent(text: String) {
        lastConsent.set(text)
    }

    fun open(url: String, retailer: String): GateResult {
        val intent = "sto per aprire $url su $retailer"
        val allowed = broker.allow(intent, ttl)
        lastConsent.compareAndSet(null, allowed)
        if (!allowed.startsWith("allowed")) {
            return GateResult(
                opened = false,
                brokerLine = allowed,
                consentText = lastConsent.get() ?: allowed,
                allowed = false
            )
        }
        val ran = broker.run("open")
        val sent = ran.contains("worked") && !ran.contains("did not send")
        val opened = sent && opener.open(url)
        return GateResult(opened = opened, brokerLine = ran, consentText = lastConsent.get(), allowed = sent)
    }

    fun openWithoutAllow(): GateResult {
        val ran = broker.run("open")
        val sent = ran.contains("worked") && !ran.contains("did not send")
        return GateResult(false, ran, lastConsent.get(), sent)
    }

    fun reserve(): GateResult {
        val ran = broker.run("reserve")
        val sent = ran.contains("worked") && !ran.contains("did not send")
        return GateResult(false, ran, lastConsent.get(), sent)
    }

    fun readDirect(): GateResult {
        val ran = broker.run("read")
        val sent = ran.contains("worked") && !ran.contains("did not send")
        return GateResult(false, ran, lastConsent.get(), sent)
    }
}
