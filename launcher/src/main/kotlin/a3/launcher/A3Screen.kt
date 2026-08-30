package a3.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import a3.renderers.android.compose.ComposeRenderer
import a3.renderers.android.core.model.RenderedOutput

@Composable
fun A3Screen(
    output: RenderedOutput?,
    onAction: (String) -> Unit,
    trustVisible: Boolean,
    rollbackVisible: Boolean,
    onApproveTrust: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("a3-host")
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (output != null) {
                ComposeRenderer(output, onAction)
            } else {
                Column(Modifier.fillMaxSize().testTag("a3-empty")) { }
            }
        }
        TrustHoldOverlay(visible = trustVisible, onApprove = onApproveTrust)
        RollbackOverlay(visible = rollbackVisible)
    }
}

@Composable
fun TrustHoldOverlay(visible: Boolean, onApprove: () -> Unit) {
    if (!visible) return
    Box(Modifier.testTag("trust-dialog")) {
        Box(
            Modifier
                .testTag("trust-approve")
                .clickable(role = Role.Button, onClick = onApprove)
        ) {
            BasicText("approve")
        }
    }
}

@Composable
fun RollbackOverlay(visible: Boolean, onReturn: () -> Unit = {}) {
    if (!visible) return
    Box(
        Modifier
            .testTag("rollback-overlay")
            .clickable(role = Role.Button, onClick = onReturn)
    ) {
        BasicText("return")
    }
}
