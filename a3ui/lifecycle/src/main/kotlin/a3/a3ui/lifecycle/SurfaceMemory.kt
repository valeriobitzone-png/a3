package a3.a3ui.lifecycle

/** Registry owned by the shell; dismissed surfaces are deallocated immediately. */
class SurfaceMemory {
    private val registry = linkedMapOf<String, SurfaceLifecycle>()

    fun register(surface: SurfaceLifecycle): SurfaceLifecycle {
        require(surface.state != SurfaceState.DISMISSED) {
            "A dismissed surface cannot be registered"
        }
        check(surface.id !in registry) { "Surface '${surface.id}' is already registered" }
        registry[surface.id] = surface
        surface.onDismiss { registry.remove(surface.id) }
        return surface
    }

    fun get(id: String): SurfaceLifecycle? = registry[id]

    fun contains(id: String): Boolean = id in registry

    fun ids(): Set<String> = registry.keys.toSet()

    fun size(): Int = registry.size

    /** Shell dismissal is the deallocation boundary. */
    fun dismiss(id: String) {
        registry[id]?.dismiss()
    }
}
