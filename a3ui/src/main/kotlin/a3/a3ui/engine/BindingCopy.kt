package a3.a3ui.engine

import a3.a3ui.model.Binding
import a3.projection.model.PresentationState

/**
 * Resolves binding copy from presentation atoms. Missing atom → empty (null).
 * Does not invent placeholder text.
 */
object BindingCopy {
    fun of(binding: Binding, presentation: PresentationState): Any? {
        val atoms = presentation.atoms.sortedWith(compareBy({ it.k }, { it.meaning }))
        for (atom in atoms) {
            if (atom.k == binding.atomKey) return atom.v
        }
        return null
    }
}
