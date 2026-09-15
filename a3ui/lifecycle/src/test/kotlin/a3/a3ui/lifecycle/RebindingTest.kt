package a3.a3ui.lifecycle

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RebindingTest {
    private val registry = FormRegistry()

    private fun ref(key: String) = DataRef(key, "test-source", "lineage-$key")

    private fun binding(id: String = "surface-1", formKey: String = "card"): SurfaceBinding {
        val surface = SurfaceLifecycle.proposed(id)
        return SurfaceBinding.proposed(surface, formKey, ref("initial"), registry)
    }

    @Test
    fun RB_001_value_updates_generate_one_form() {
        val binding = binding()
        repeat(25) { binding.rebind(ref("value-$it")) }
        assertEquals(1, registry.generationCount, "generation counter")
        assertEquals(1, registry.generationCount("card"))
        assertEquals("value-24", binding.dataRef.key)
    }

    @Test
    fun RB_002_surface_id_is_stable_through_rebinds() {
        val binding = binding("stable-surface")
        repeat(10) { binding.rebind(ref("value-$it")) }
        assertEquals("stable-surface", binding.surfaceId)
        assertEquals("stable-surface", binding.surface.id)
    }

    @Test
    fun RB_003_binding_keeps_a_data_ref_not_a_value_copy() {
        val binding = binding()
        assertEquals(ref("initial"), binding.dataRef)
        assertFalse(SurfaceBinding::class.java.declaredFields.any { it.name.contains("value", ignoreCase = true) })
        assertFalse(Form::class.java.declaredFields.any { it.name.contains("value", ignoreCase = true) })
        assertTrue(DataRef::class.java.declaredFields.map { it.name }.containsAll(listOf("key", "source", "lineage")))
    }

    @Test
    fun RB_004_form_key_change_regenerates_and_is_declared() {
        val binding = binding()
        binding.regenerate("list")
        assertEquals(2, registry.generationCount, "generation counter")
        assertEquals("list", binding.form.formKey)
        assertEquals(2, binding.form.generation)
    }

    @Test
    fun RB_005_rebind_on_dismissed_rejects_explicitly() {
        val binding = binding()
        binding.surface.mount().activate().dismiss()
        assertFailsWith<IllegalSurfaceTransitionException> { binding.rebind(ref("late")) }
    }

    @Test
    fun RB_006_state_controls_when_rebind_is_applied() {
        val proposed = binding("proposed")
        proposed.rebind(ref("proposal-update"))
        assertEquals("proposal-update", proposed.dataRef.key)
        proposed.surface.mount()
        assertEquals("proposal-update", proposed.dataRef.key)

        val alive = binding("alive")
        alive.surface.mount().activate()
        alive.rebind(ref("live-update"))
        assertEquals("live-update", alive.dataRef.key)

        val frozen = binding("frozen")
        frozen.surface.mount().activate()
        frozen.surface.freeze(SurfaceSnapshot(preserved = mapOf("binding" to "declared")))
        frozen.rebind(ref("frozen-update"))
        assertEquals("initial", frozen.dataRef.key)
        assertTrue(frozen.hasPendingRestore)
        frozen.restore()
        assertEquals("frozen-update", frozen.dataRef.key)
        assertFalse(frozen.hasPendingRestore)
    }

    @Test
    fun RB_007_spec_contains_rebinding_invalidation_rule() {
        val spec = File("../../spec/SPEC_A3UI.md").readText()
        assertTrue(spec.contains("## 12. Re-binding e invalidazione"))
        assertTrue(spec.contains("formKey"))
        assertTrue(spec.contains("surfaceId"))
        assertTrue(spec.contains("MUST NOT rigenerare per soli valori"))
        assertTrue(spec.contains("cambio formKey/schema"))
    }

    @Test
    fun RB_008_frozen_paths_have_empty_diff() {
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
