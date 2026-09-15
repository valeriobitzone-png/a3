package a3.a3ui.lifecycle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SurfaceLifecycleTest {
    @Test
    fun LC_001_complete_path_is_valid() {
        val surface = SurfaceLifecycle.proposed("s-001")
        assertEquals(SurfaceState.PROPOSED, surface.state)
        surface.mount().activate()
        surface.freeze(SurfaceSnapshot(preserved = mapOf("draft" to "hello"), discarded = setOf("hover")))
        val restoration = surface.revive()
        assertEquals(SurfaceState.ALIVE, surface.state)
        assertEquals(mapOf("draft" to "hello"), restoration.restored)
        assertEquals(setOf("hover"), restoration.discarded)
        surface.dismiss()
        assertEquals(SurfaceState.DISMISSED, surface.state)
    }

    @Test
    fun LC_002_producer_dismiss_is_rejected() {
        val surface = SurfaceLifecycle.proposed("s-002")
        assertFailsWith<IllegalSurfaceTransitionException> { surface.producerDismiss() }
        assertEquals(SurfaceState.PROPOSED, surface.state)
    }

    @Test
    fun LC_003_shell_cannot_create_proposed() {
        assertFailsWith<IllegalSurfaceTransitionException> {
            SurfaceLifecycle.proposedByShell("s-003")
        }
    }

    @Test
    fun LC_004_pre_mount_proposal_is_queued_and_mounted_when_container_arrives() {
        val queue = SurfaceQueue()
        val surface = SurfaceLifecycle.proposed("s-004")
        queue.enqueue("host-1", surface)
        assertEquals(1, queue.pendingCount("host-1"))
        assertEquals(SurfaceState.PROPOSED, surface.state)

        val mounted = queue.mount("host-1")
        assertEquals(listOf(surface), mounted)
        assertEquals(SurfaceState.MOUNTED, surface.state)
        assertTrue(queue.isEmpty())
    }

    @Test
    fun LC_005_dismissed_surface_leaves_no_memory_reference() {
        val memory = SurfaceMemory()
        val surface = memory.register(SurfaceLifecycle.proposed("s-005"))
        surface.mount().activate()
        memory.dismiss(surface.id)
        assertEquals(SurfaceState.DISMISSED, surface.state)
        assertFalse(memory.contains(surface.id))
        assertEquals(0, memory.size())
        assertNotNull(surface)
    }

    @Test
    fun LC_006_revival_restores_declared_values_and_reports_the_rest() {
        val surface = SurfaceLifecycle.proposed("s-006").mount().activate()
        surface.freeze(
            SurfaceSnapshot(
                preserved = mapOf("text" to "draft", "selection" to 4),
                discarded = setOf("hover", "focus-owner")
            )
        )
        val result = surface.revive()
        assertEquals(mapOf("text" to "draft", "selection" to 4), result.restored)
        assertEquals(setOf("hover", "focus-owner"), result.discarded)
        assertEquals(result, surface.lastRestoration)
    }

    @Test
    fun LC_007_spec_contains_surface_lifecycle_and_transition_table() {
        val spec = locateSpec().toFile().readText()
        assertTrue(spec.contains("## 11. Surface lifecycle"))
        assertTrue(spec.contains("PROPOSED → MOUNTED → ALIVE → FROZEN → DISMISSED"))
        assertTrue(spec.contains("| From | To | Owner | Rule |"))
        assertTrue(spec.contains("Pre-mount"))
        assertTrue(spec.contains("MUST NOT dismis"))
    }

    private fun locateSpec(): Path {
        val candidates = listOf(
            Path.of("spec/SPEC_A3UI.md"),
            Path.of("../spec/SPEC_A3UI.md"),
            Path.of("../../spec/SPEC_A3UI.md")
        )
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("spec/SPEC_A3UI.md not found from ${Path.of(".").toAbsolutePath()}")
    }
}
