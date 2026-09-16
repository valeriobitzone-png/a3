// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.a3ui.lifecycle

/** Lifecycle states for an ephemeral surface. */
enum class SurfaceState {
    PROPOSED,
    MOUNTED,
    ALIVE,
    FROZEN,
    DISMISSED
}

/** The only actors allowed to request lifecycle operations. */
enum class SurfaceActor {
    PRODUCER,
    SHELL
}

/** A declared snapshot: preserved values are restored; discarded keys are intentionally not restored. */
data class SurfaceSnapshot(
    val preserved: Map<String, Any?> = emptyMap(),
    val discarded: Set<String> = emptySet()
) {
    init {
        require(preserved.keys.none { key -> key in discarded }) {
            "A frozen surface cannot both preserve and discard a key"
        }
    }
}

data class SurfaceRestoration(
    val restored: Map<String, Any?>,
    val discarded: Set<String>
)

class IllegalSurfaceTransitionException(message: String) : IllegalStateException(message)

/**
 * Headless state machine. Producers can only propose; the host/renderer shell owns mounting,
 * freezing, revival and dismissal.
 */
class SurfaceLifecycle private constructor(
    val id: String,
    private val dismissedListeners: MutableList<() -> Unit> = mutableListOf()
) {
    var state: SurfaceState = SurfaceState.PROPOSED
        private set

    var snapshot: SurfaceSnapshot? = null
        private set

    var lastRestoration: SurfaceRestoration? = null
        private set

    companion object {
        fun proposed(id: String): SurfaceLifecycle {
            require(id.isNotBlank()) { "Surface id must not be blank" }
            return SurfaceLifecycle(id)
        }

        /** The shell MUST NOT create PROPOSED surfaces. */
        fun proposedByShell(id: String): SurfaceLifecycle =
            throw IllegalSurfaceTransitionException("Shell MUST NOT create PROPOSED surface '$id'")
    }

    /** Producer operation: proposal only. A producer can never dismiss a surface. */
    fun producerDismiss(): Nothing =
        throw IllegalSurfaceTransitionException("Producer MUST NOT dismiss surface '$id'")

    fun mount(): SurfaceLifecycle {
        requireShell(SurfaceState.PROPOSED, SurfaceState.MOUNTED)
        return this
    }

    fun activate(): SurfaceLifecycle {
        requireShell(SurfaceState.MOUNTED, SurfaceState.ALIVE)
        return this
    }

    fun freeze(snapshot: SurfaceSnapshot): SurfaceLifecycle {
        requireShell(SurfaceState.ALIVE, SurfaceState.FROZEN)
        this.snapshot = snapshot
        return this
    }

    fun revive(): SurfaceRestoration {
        requireShell(SurfaceState.FROZEN, SurfaceState.ALIVE)
        val saved = snapshot ?: throw IllegalSurfaceTransitionException(
            "Frozen surface '$id' has no preservation declaration"
        )
        val restoration = SurfaceRestoration(saved.preserved.toMap(), saved.discarded.toSet())
        lastRestoration = restoration
        state = SurfaceState.ALIVE
        return restoration
    }

    internal fun onDismiss(listener: () -> Unit) {
        dismissedListeners += listener
    }

    fun dismiss(): SurfaceLifecycle {
        requireShell(setOf(SurfaceState.MOUNTED, SurfaceState.ALIVE, SurfaceState.FROZEN), SurfaceState.DISMISSED)
        snapshot = null
        lastRestoration = null
        dismissedListeners.toList().forEach { it() }
        return this
    }

    private fun requireShell(expected: SurfaceState, next: SurfaceState) {
        if (state != expected) reject(expected.name, next)
        state = next
    }

    private fun requireShell(expected: Set<SurfaceState>, next: SurfaceState) {
        if (state !in expected) reject(expected.joinToString(), next)
        state = next
    }

    private fun reject(expected: String, next: SurfaceState): Nothing =
        throw IllegalSurfaceTransitionException(
            "Illegal transition for surface '$id': $state -> $next; expected $expected"
        )
}
