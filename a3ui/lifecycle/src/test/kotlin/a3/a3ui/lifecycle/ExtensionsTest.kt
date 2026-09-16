// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.a3ui.lifecycle

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExtensionsTest {
    @Test
    fun NS_001_valid_mono_extension_crosses_opaque_protocol() {
        val payload = mapOf("status" to "pending")
        val extensions = SurfaceExtensions.of(mapOf("x-mono-gate" to payload))
        assertEquals(payload, extensions["x-mono-gate"])
        assertEquals(setOf("x-mono-gate"), extensions.keys)
    }

    @Test
    fun NS_002_key_without_x_prefix_is_rejected_explicitly() {
        val error = assertFailsWith<InvalidExtensionKeyException> {
            SurfaceExtensions.of(mapOf("gate" to mapOf("status" to "pending")))
        }
        assertTrue(error.message!!.contains("x-"))
    }

    @Test
    fun NS_003_unknown_extension_is_ignored_by_renderer_stub() {
        val extensions = SurfaceExtensions.of(
            mapOf("x-mono-gate" to mapOf("status" to "pending"))
        )
        val rendered = ExtensionRendererStub().render("surface-3", extensions)
        assertTrue(rendered.drawn)
        assertEquals(setOf("x-mono-gate"), rendered.ignoredExtensions)
    }

    @Test
    fun NS_004_extensions_are_optional() {
        val rendered = ExtensionRendererStub().render("surface-without-extensions")
        assertTrue(rendered.drawn)
        assertTrue(rendered.ignoredExtensions.isEmpty())
        assertEquals(0, SurfaceExtensions.empty().size)
    }

    @Test
    fun NS_005_spec_contains_opaque_transport_rule() {
        val spec = File("../../spec/SPEC_A3UI.md").readText()
        assertTrue(spec.contains("## 13. Namespace di estensione"))
        assertTrue(spec.contains("MUST transport the opaque payload"))
        assertTrue(spec.contains("MUST NOT interpret it"))
        assertTrue(spec.contains("x-<consumatore>-*"))
    }

    @Test
    fun NS_006_frozen_paths_have_empty_diff() {
        val process = ProcessBuilder(
            "git", "diff", "--name-only", "--",
            "core/", "broker/", "agent/", "renderers/", "launcher/", "overlay/",
            "adapters/", "conformance/", "a3ui-web/", "spec/SPEC_A3-EP.md"
        ).directory(File("../.."))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor())
        assertTrue(output.isBlank(), output)
    }
}
