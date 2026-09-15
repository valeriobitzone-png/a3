package a3.a3ui.lifecycle

class InvalidExtensionKeyException(message: String) : IllegalArgumentException(message)

/** Validates only the namespace prefix; payload meaning belongs to the consumer. */
object ExtensionValidator {
    fun validateKey(key: String) {
        if (!key.startsWith("x-") || key.length <= 2) {
            throw InvalidExtensionKeyException(
                "Extension key '$key' is invalid: keys MUST start with x-"
            )
        }
    }
}

/**
 * Optional surface extensions. Values are retained as opaque payloads and are never inspected by
 * A3UI. A defensive map copy prevents later mutation from changing the transported envelope.
 */
class SurfaceExtensions private constructor(
    private val entries: Map<String, Any?>
) {
    val keys: Set<String>
        get() = entries.keys

    val size: Int
        get() = entries.size

    operator fun get(key: String): Any? = entries[key]

    fun asMap(): Map<String, Any?> = entries.toMap()

    companion object {
        fun of(entries: Map<String, Any?>): SurfaceExtensions {
            entries.keys.forEach(ExtensionValidator::validateKey)
            return SurfaceExtensions(entries.toMap())
        }

        fun empty(): SurfaceExtensions = SurfaceExtensions(emptyMap())
    }
}

data class RenderedSurfaceStub(
    val surfaceId: String,
    val drawn: Boolean,
    val ignoredExtensions: Set<String>
)

/**
 * Headless renderer contract test double. It draws the base surface and ignores unrecognized
 * extensions; it does not interpret extension payloads.
 */
class ExtensionRendererStub(
    recognizedKeys: Set<String> = emptySet()
) {
    private val recognized = recognizedKeys.onEach(ExtensionValidator::validateKey).toSet()

    fun render(surfaceId: String, extensions: SurfaceExtensions = SurfaceExtensions.empty()): RenderedSurfaceStub {
        require(surfaceId.isNotBlank()) { "Surface id must not be blank" }
        return RenderedSurfaceStub(
            surfaceId = surfaceId,
            drawn = true,
            ignoredExtensions = extensions.keys - recognized
        )
    }
}
