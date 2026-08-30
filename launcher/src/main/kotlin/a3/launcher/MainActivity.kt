package a3.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(0xFFFFFFFF.toInt())
        val vm = A3HostViewModel.train()
        setContent {
            val ui by vm.ui.collectAsState()
            A3Screen(
                output = ui.output,
                onAction = vm::onAction,
                trustVisible = ui.trustHold,
                rollbackVisible = ui.rollbackVisible,
                onApproveTrust = vm::approveTrust,
                stage = ui.stage
            )
        }
    }
}
