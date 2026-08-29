package a3.a3ui.engine

import a3.a3ui.model.A3UISurface
import a3.projection.model.Projection
import a3.projection.model.ProjectionCandidate

/**
 * Read-only compiler: projection intent → declarative temporal surface.
 * Does not emit renderer-owned artifacts. Does not write committed reality.
 */
interface A3UICompiler {
    fun compile(projection: Projection): A3UISurface
    fun compilePrefetch(candidate: ProjectionCandidate): A3UISurface
}
