package a3.launcher

import a3.renderers.android.core.model.RenderedNode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class LauncherHostTest {
    @Test
    fun L4_prefetch_hits_then_misses_after_belief_version_bump() {
        val vm = A3HostViewModel.train()
        vm.start()
        assertEquals(0, vm.ui.value.belief.version)
        val hit = vm.prefetchHit()
        assertNotNull(hit)
        assertNotNull(vm.prefetchGet())
        vm.onAction("confirm")
        assertTrue(vm.ui.value.trustHold)
        vm.approveTrust()
        assertTrue(vm.ui.value.belief.version > 0)
        assertNull(vm.prefetchHit())
        assertNull(vm.prefetchGet())
    }

    @Test
    fun L9_first_frame_output_is_non_null_without_calling_start() {
        val vm = A3HostViewModel.train()
        assertNotNull(vm.ui.value.output)
        assertNotNull(vm.ui.value.surface)
        assertNotNull(vm.ui.value.plan)
    }

    @Test
    fun F2_presentation_uses_frozen_meanings_only() {
        val atoms = DemoFixtures.presentation().atoms
        assertTrue(atoms.count { it.meaning == "timetable" } >= 2)
        assertTrue(atoms.any { it.meaning == "passenger" })
        assertTrue(atoms.any { it.meaning == "price" })
        assertTrue(atoms.any { it.meaning == "confirm" || it.k.endsWith(".owned") })
    }

    @Test
    fun F3_interpreter_copies_binding_onto_item_and_action() {
        val output = A3HostViewModel.train().ui.value.output!!
        assertEquals("08:45", find(output.nodes, "item_train.slot.a")!!.text)
        assertEquals("09:12", find(output.nodes, "item_train.slot.b")!!.text)
        assertEquals("10:03", find(output.nodes, "item_train.slot.c")!!.text)
        assertEquals("hold 08:45", find(output.nodes, "action_ticket.owned")!!.text)
        assertEquals("Ada", find(output.nodes, "field_train.passenger")!!.text)
        assertEquals("12.40", find(output.nodes, "text_train.price")!!.text)
    }

    private fun find(nodes: List<RenderedNode>, id: String): RenderedNode? {
        for (node in nodes) {
            if (node.id == id) return node
            val child = find(node.children, id)
            if (child != null) return child
        }
        return null
    }
}
