package a3.projection.engine

import a3.projection.model.PresentationState
import a3.projection.model.RenderedOutput

fun interface PresentationRenderer {
    fun render(state: PresentationState): RenderedOutput
}
