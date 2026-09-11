package a3.core.envelope

import a3.core.json.CanonicalJson
import java.security.MessageDigest
import org.erdtman.jcs.JsonCanonicalizer

/**
 * RFC 8785 JSON Canonicalization Scheme via the Erdtman reference
 * implementation. Kotlin values are first rendered with the frozen
 * [CanonicalJson] engine, then passed through JCS so the bytes are
 * RFC 8785, not a private dialect.
 */
object Jcs {
    fun ofJson(json: String): String = String(bytesOfJson(json), Charsets.UTF_8)

    fun bytesOfJson(json: String): ByteArray = try {
        JsonCanonicalizer(json).encodedUTF8
    } catch (e: Exception) {
        throw EnvelopeReject("JCS failed: ${e.message}")
    }

    fun of(value: Any?): String = ofJson(CanonicalJson.encode(value))

    fun bytes(value: Any?): ByteArray = of(value).toByteArray(Charsets.UTF_8)

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    fun sha256Hex(value: Any?): String = sha256Hex(bytes(value))
}
