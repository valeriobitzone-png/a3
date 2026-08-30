package a3.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = A3HostViewModel.train()
        vm.start()
        setContent {
            val ui by vm.ui.collectAsState()
            val output = ui.output
            if (output != null) {
                A3Screen(
                    output = output,
                    onAction = vm::onAction,
                    trustVisible = ui.trustHold,
                    rollbackVisible = ui.rollbackVisible,
                    onApproveTrust = vm::approveTrust
                )
            }
        }
    }
}
