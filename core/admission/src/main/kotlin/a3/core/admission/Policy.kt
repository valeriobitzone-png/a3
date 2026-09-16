// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.admission

import a3.core.json.CanonicalJson
import java.security.MessageDigest
import java.util.Collections
import java.util.TreeMap

class AdmissionPolicy internal constructor(
    val policyId: String,
    val policyVersion: String,
    val ttlSeconds: Long?,
    val blacklist: Set<SourceId>,
    val holdModelGrounded: Boolean,
    val schemas: Map<String, ByteArray>,
    val validatorProfile: String,
    val validatorVersion: String,
    val policyDigest: String
)

fun admissionPolicy(
    policyId: String,
    policyVersion: String,
    ttlSeconds: Long?,
    blacklist: Set<SourceId> = emptySet(),
    holdModelGrounded: Boolean = false,
    schemas: Map<String, ByteArray>,
    validatorProfile: String = "a3.core.json.SchemaValidator",
    validatorVersion: String = "1"
): AdmissionPolicy {
    require(policyId.isNotBlank()) { "policyId must be non-blank" }
    require(policyVersion.isNotBlank()) { "policyVersion must be non-blank" }
    if (ttlSeconds != null && ttlSeconds < 0L) {
        throw IllegalArgumentException("ttlSeconds < 0")
    }
    val frozenSchemas = TreeMap<String, ByteArray>()
    for ((name, bytes) in schemas) {
        frozenSchemas[name] = bytes.copyOf()
    }
    val digest = policyDigest(
        policyId = policyId,
        policyVersion = policyVersion,
        ttlSeconds = ttlSeconds,
        blacklist = blacklist,
        holdModelGrounded = holdModelGrounded,
        schemas = frozenSchemas,
        validatorProfile = validatorProfile,
        validatorVersion = validatorVersion
    )
    return AdmissionPolicy(
        policyId = policyId,
        policyVersion = policyVersion,
        ttlSeconds = ttlSeconds,
        blacklist = blacklist.toSet(),
        holdModelGrounded = holdModelGrounded,
        schemas = Collections.unmodifiableMap(frozenSchemas),
        validatorProfile = validatorProfile,
        validatorVersion = validatorVersion,
        policyDigest = digest
    )
}

fun policyDigest(
    policyId: String,
    policyVersion: String,
    ttlSeconds: Long?,
    blacklist: Set<SourceId>,
    holdModelGrounded: Boolean,
    schemas: Map<String, ByteArray>,
    validatorProfile: String,
    validatorVersion: String
): String {
    val schemaBytes = TreeMap<String, String>()
    for ((name, bytes) in schemas) {
        schemaBytes[name] = sha256Hex(bytes)
    }
    val payload = buildMap<String, Any?> {
        put("blacklist", blacklist.map { it.value }.sorted())
        put("holdModelGrounded", holdModelGrounded)
        put("policyId", policyId)
        put("policyVersion", policyVersion)
        put("schemaBytes", schemaBytes)
        put("schemaSet", schemas.keys.sorted())
        put("ttlSeconds", ttlSeconds)
        put("validatorProfile", validatorProfile)
        put("validatorVersion", validatorVersion)
    }
    return sha256Hex(CanonicalJson.encode(payload).toByteArray(Charsets.UTF_8))
}

fun schemaResourceBytes(name: String): ByteArray {
    val url = a3.core.json.SchemaValidator::class.java.classLoader.getResource("a3/schemas/$name")
        ?: throw IllegalStateException("schema $name not found")
    return url.readBytes()
}

internal fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { b -> "%02x".format(b) }
}

class PolicyRegistry {
    private val byKey = LinkedHashMap<Pair<String, String>, AdmissionPolicy>()

    fun register(policy: AdmissionPolicy): AdmissionPolicy {
        val key = policy.policyId to policy.policyVersion
        val existing = byKey[key]
        if (existing != null && existing.policyDigest != policy.policyDigest) {
            throw IllegalStateException(
                "policy ${policy.policyId}@${policy.policyVersion} digest mismatch"
            )
        }
        byKey[key] = policy
        return policy
    }

    fun get(policyId: String, policyVersion: String): AdmissionPolicy? =
        byKey[policyId to policyVersion]
}
