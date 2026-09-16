// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.a3ui.lifecycle

/** A reference to external data; a form never stores the referenced value. */
data class DataRef(
    val key: String,
    val source: String,
    val lineage: String
) {
    init {
        require(key.isNotBlank()) { "DataRef key must not be blank" }
        require(source.isNotBlank()) { "DataRef source is required" }
        require(lineage.isNotBlank()) { "DataRef lineage is required" }
    }
}

/** Renderer-independent form identity. Its generation is the only shape-generation event. */
data class Form(
    val formKey: String,
    val schemaKey: String,
    val generation: Int
)

/**
 * One generated form per formKey. Value updates do not reach this registry and therefore cannot
 * generate a second shape.
 */
class FormRegistry {
    private val forms = linkedMapOf<String, Form>()
    private var totalGenerationCount = 0

    val generationCount: Int
        get() = totalGenerationCount

    fun generationCount(formKey: String): Int = forms[formKey]?.generation ?: 0

    fun getOrCreate(formKey: String, schemaKey: String = formKey): Form {
        require(formKey.isNotBlank()) { "formKey must not be blank" }
        require(schemaKey.isNotBlank()) { "schemaKey must not be blank" }
        val current = forms[formKey]
        if (current != null && current.schemaKey == schemaKey) return current
        totalGenerationCount += 1
        return Form(formKey, schemaKey, totalGenerationCount).also { forms[formKey] = it }
    }

    fun contains(formKey: String): Boolean = formKey in forms
}

/**
 * Stable binding between one surface identity and a generated form. Re-binding changes only the
 * DataRef. A formKey/schema change explicitly requests a new form generation.
 */
class SurfaceBinding private constructor(
    val surface: SurfaceLifecycle,
    private val registry: FormRegistry,
    initialForm: Form,
    initialDataRef: DataRef
) {
    var form: Form = initialForm
        private set

    val surfaceId: String
        get() = surface.id

    var dataRef: DataRef = initialDataRef
        private set

    private var pendingRestore: DataRef? = null

    companion object {
        fun proposed(
            surface: SurfaceLifecycle,
            formKey: String,
            dataRef: DataRef,
            registry: FormRegistry
        ): SurfaceBinding {
            require(surface.state == SurfaceState.PROPOSED) {
                "A binding must start with a PROPOSED surface"
            }
            return SurfaceBinding(surface, registry, registry.getOrCreate(formKey), dataRef)
        }
    }

    /** Rebind data without regenerating the form. Frozen updates wait for restore. */
    fun rebind(next: DataRef): SurfaceBinding {
        rejectIfDismissed()
        when (surface.state) {
            SurfaceState.FROZEN -> pendingRestore = next
            SurfaceState.PROPOSED, SurfaceState.MOUNTED, SurfaceState.ALIVE -> dataRef = next
            SurfaceState.DISMISSED -> error("unreachable")
        }
        return this
    }

    /** Explicitly change form identity/schema; this is the only value-independent regeneration. */
    fun regenerate(formKey: String, schemaKey: String = formKey): SurfaceBinding {
        rejectIfDismissed()
        form = registry.getOrCreate(formKey, schemaKey)
        return this
    }

    /** Revive the surface and apply the one queued frozen DataRef, if any. */
    fun restore(): DataRef {
        if (surface.state != SurfaceState.FROZEN) {
            throw IllegalSurfaceTransitionException(
                "Cannot restore binding '${surface.id}' while surface is ${surface.state}"
            )
        }
        surface.revive()
        pendingRestore?.let {
            dataRef = it
            pendingRestore = null
        }
        return dataRef
    }

    val hasPendingRestore: Boolean
        get() = pendingRestore != null

    private fun rejectIfDismissed() {
        if (surface.state == SurfaceState.DISMISSED) {
            throw IllegalSurfaceTransitionException(
                "Cannot rebind dismissed surface '${surface.id}'"
            )
        }
    }
}
