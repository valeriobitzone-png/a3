// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.a3ui.conformance

import a3.a3ui.lifecycle.DataRef
import a3.a3ui.lifecycle.ExtensionRendererStub
import a3.a3ui.lifecycle.FormRegistry
import a3.a3ui.lifecycle.IllegalSurfaceTransitionException
import a3.a3ui.lifecycle.InvalidExtensionKeyException
import a3.a3ui.lifecycle.SurfaceBinding
import a3.a3ui.lifecycle.SurfaceExtensions
import a3.a3ui.lifecycle.SurfaceLifecycle
import a3.a3ui.lifecycle.SurfaceMemory
import a3.a3ui.lifecycle.SurfaceQueue
import a3.a3ui.lifecycle.SurfaceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B5a programmatic conformance; deliberately no Compose or graphical renderer dependency. */
class B5ConformanceTest {
    private fun ref(key: String) = DataRef(key, "conformance-source", "conformance-lineage-$key")

    @Test
    fun AC_024_surface_is_queued_before_mount_and_mounted_on_container_arrival() {
        val queue = SurfaceQueue()
        val surface = SurfaceLifecycle.proposed("ac-024")
        queue.enqueue("container-024", surface)
        assertEquals(SurfaceState.PROPOSED, surface.state)
        assertEquals(listOf(surface), queue.mount("container-024"))
        assertEquals(SurfaceState.MOUNTED, surface.state)
    }

    @Test
    fun AC_025_producer_dismiss_is_rejected_explicitly() {
        val surface = SurfaceLifecycle.proposed("ac-025")
        assertFailsWith<IllegalSurfaceTransitionException> { surface.producerDismiss() }
        assertEquals(SurfaceState.PROPOSED, surface.state)
    }

    @Test
    fun AC_026_dismissed_surface_has_no_memory_reference() {
        val memory = SurfaceMemory()
        val surface = memory.register(SurfaceLifecycle.proposed("ac-026"))
        surface.mount().activate()
        memory.dismiss(surface.id)
        assertEquals(SurfaceState.DISMISSED, surface.state)
        assertFalse(memory.contains("ac-026"))
        assertEquals(0, memory.size())
    }

    @Test
    fun AC_027_value_updates_keep_generation_counter_at_one() {
        val registry = FormRegistry()
        val binding = SurfaceBinding.proposed(
            SurfaceLifecycle.proposed("ac-027"), "card", ref("initial"), registry
        )
        repeat(12) { binding.rebind(ref("value-$it")) }
        assertEquals(1, registry.generationCount)
    }

    @Test
    fun AC_028_surface_id_remains_stable_through_rebinds() {
        val registry = FormRegistry()
        val binding = SurfaceBinding.proposed(
            SurfaceLifecycle.proposed("stable-ac-028"), "card", ref("initial"), registry
        )
        repeat(12) { binding.rebind(ref("value-$it")) }
        assertEquals("stable-ac-028", binding.surfaceId)
    }

    @Test
    fun AC_029_form_key_change_regenerates_to_generation_two() {
        val registry = FormRegistry()
        val binding = SurfaceBinding.proposed(
            SurfaceLifecycle.proposed("ac-029"), "card", ref("initial"), registry
        )
        binding.regenerate("list")
        assertEquals(2, registry.generationCount)
        assertEquals("list", binding.form.formKey)
    }

    @Test
    fun AC_030_x_mono_gate_extension_passes_opaque() {
        val extension = SurfaceExtensions.of(
            mapOf("x-private-gate" to mapOf("status" to "pending"))
        )
        assertEquals(mapOf("status" to "pending"), extension["x-private-gate"])
    }

    @Test
    fun AC_031_extension_without_x_prefix_is_rejected_explicitly() {
        assertFailsWith<InvalidExtensionKeyException> {
            SurfaceExtensions.of(mapOf("gate" to mapOf("status" to "pending")))
        }
    }

    @Test
    fun AC_032_unknown_extension_is_ignored_and_surface_is_drawn() {
        val extension = SurfaceExtensions.of(mapOf("x-private-gate" to "opaque"))
        val result = ExtensionRendererStub().render("ac-032", extension)
        assertTrue(result.drawn)
        assertEquals(setOf("x-private-gate"), result.ignoredExtensions)
    }

    @Test
    fun AC_033_surface_without_extensions_remains_valid() {
        val result = ExtensionRendererStub().render("ac-033")
        assertTrue(result.drawn)
        assertTrue(result.ignoredExtensions.isEmpty())
    }
}
