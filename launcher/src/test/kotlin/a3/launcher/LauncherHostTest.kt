package a3.launcher

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
}
