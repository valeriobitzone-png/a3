package a3.a3ui.lifecycle

/**
 * Holds producer proposals until their host container exists. Queued proposals are never lost
 * merely because mounting has not happened yet.
 */
class SurfaceQueue {
    private val pending = linkedMapOf<String, LinkedHashMap<String, SurfaceLifecycle>>()

    fun enqueue(containerId: String, surface: SurfaceLifecycle) {
        require(containerId.isNotBlank()) { "Container id must not be blank" }
        if (surface.state != SurfaceState.PROPOSED) {
            throw IllegalSurfaceTransitionException(
                "Only PROPOSED surfaces can enter the pre-mount queue: '${surface.id}' is ${surface.state}"
            )
        }
        pending.getOrPut(containerId) { linkedMapOf() }[surface.id] = surface
    }

    /** Mounts and returns all proposals waiting for this container. */
    fun mount(containerId: String): List<SurfaceLifecycle> {
        val waiting = pending.remove(containerId)?.values.orEmpty().toList()
        waiting.forEach { it.mount() }
        return waiting
    }

    fun pending(containerId: String): List<SurfaceLifecycle> =
        pending[containerId]?.values?.toList().orEmpty()

    fun pendingCount(containerId: String): Int = pending[containerId]?.size ?: 0

    fun isEmpty(): Boolean = pending.values.all { it.isEmpty() }
}
