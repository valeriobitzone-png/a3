// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.a3ui.engine

import a3.a3ui.model.A3UISurface
import a3.a3ui.model.Binding
import a3.a3ui.model.Node
import a3.a3ui.serialize.CanonicalJson
import java.security.MessageDigest

/**
 * Clock-free hash of catalog content + axis. Motion, morph, haptics, and
 * producedAt are outside the digest (A3UI-1, MOTION-BOUNDARY).
 */
object PresentationHash {
    fun of(nodes: List<Node>, bindings: List<Binding>): String {
        val canonical = CanonicalJson.of(
            mapOf(
                "bindings" to bindings,
                "nodes" to nodes
            )
        )
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    fun of(tree: ComposedTree): String = of(tree.nodes, tree.bindings)

    fun of(surface: A3UISurface): String = of(surface.nodes, surface.bindings)

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { b -> "%02x".format(b) }
    }
}
