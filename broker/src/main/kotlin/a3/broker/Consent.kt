package a3.broker

import a3.core.json.CanonicalJson
import java.security.MessageDigest
import java.time.Duration

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { b -> "%02x".format(b) }

internal fun durationLabel(ttl: Duration): String {
    val seconds = ttl.seconds
    return when {
        seconds % 3600L == 0L && seconds >= 3600L ->
            "${seconds / 3600L} hour" + if (seconds == 3600L) "" else "s"
        seconds % 60L == 0L && seconds >= 60L ->
            "${seconds / 60L} minute" + if (seconds == 60L) "" else "s"
        else -> "$seconds second" + if (seconds == 1L) "" else "s"
    }
}

data class ConsentScreen(
    val text: String,
    val presentationHash: String
)

internal fun consentScreen(plan: String, scope: List<String>, duration: String): ConsentScreen {
    val text = buildString {
        appendLine("plan: $plan")
        appendLine("scope: ${scope.joinToString(", ")}")
        append("duration: $duration")
    }
    val canonical = CanonicalJson.bytes(
        mapOf(
            "duration" to duration,
            "plan" to plan,
            "scope" to scope.sorted()
        )
    )
    return ConsentScreen(text = text, presentationHash = sha256Hex(canonical))
}

internal fun parseTtl(raw: String): Duration {
    val s = raw.trim()
    val n = s.dropLast(1).toLong()
    return when (s.last()) {
        's' -> Duration.ofSeconds(n)
        'm' -> Duration.ofMinutes(n)
        'h' -> Duration.ofHours(n)
        else -> throw IllegalArgumentException("duration must end in s, m, or h")
    }
}

internal val KNOWN_TOOLS = listOf("list", "read", "reserve", "send", "delete")

internal fun toolsNamedIn(intent: String): List<String> {
    val words = intent.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()
    return KNOWN_TOOLS.filter { it in words }
}
