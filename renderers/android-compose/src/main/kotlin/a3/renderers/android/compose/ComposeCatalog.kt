package a3.renderers.android.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField as FoundationInput
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import a3.renderers.android.core.model.RenderedNode
import a3.renderers.android.core.model.SemanticGestureAction
import java.util.ArrayList

@Composable
fun ComposeCatalog(
    nodes: List<RenderedNode>,
    gestures: List<SemanticGestureAction>,
    onAction: (String) -> Unit
) {
    Column(Modifier.testTag("a3-catalog")) {
        for (node in nodes) {
            CatalogNode(node, gestures, onAction)
        }
    }
}

@Composable
private fun CatalogNode(
    node: RenderedNode,
    gestures: List<SemanticGestureAction>,
    onAction: (String) -> Unit
) {
    val targeted = ArrayList<SemanticGestureAction>()
    for (gesture in gestures) {
        if (gesture.targetNodeId == node.id) targeted += gesture
    }
    val modifier = nodeModifier(node, targeted, onAction)
    when (node.role) {
        "stack" -> Column(modifier) { Children(node, gestures, onAction) }
        "row" -> Row(modifier) { Children(node, gestures, onAction) }
        "list" -> LazyColumn(modifier) {
            items(node.children, key = { it.id }) { child ->
                CatalogNode(child, gestures, onAction)
            }
        }
        "item" -> Box(modifier) { Children(node, gestures, onAction) }
        "action" -> Box(modifier.size(48.dp)) { Children(node, gestures, onAction) }
        "field" -> FoundationInput(
            value = node.text,
            onValueChange = {},
            readOnly = true,
            modifier = modifier
        )
        "text" -> BasicText(text = node.text, modifier = modifier)
        else -> Box(modifier)
    }
}

@Composable
private fun Children(
    node: RenderedNode,
    gestures: List<SemanticGestureAction>,
    onAction: (String) -> Unit
) {
    for (child in node.children) {
        CatalogNode(child, gestures, onAction)
    }
}

private fun nodeModifier(
    node: RenderedNode,
    targeted: List<SemanticGestureAction>,
    onAction: (String) -> Unit
): Modifier {
    var modifier: Modifier = Modifier
        .testTag(node.id)
        .sizeIn(minWidth = 8.dp, minHeight = 8.dp)
    if (node.role == "list") {
        modifier = modifier.height(160.dp)
    }
    var clickableAction: String? = null
    for (gesture in targeted) {
        if (gesture.gesture == "swipe-left") {
            val action = gesture.action
            modifier = modifier.pointerInput(action) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < 0f) onAction(action)
                }
            }
        } else {
            clickableAction = gesture.action
        }
    }
    if (clickableAction == null && node.role == "action") {
        clickableAction = targeted.firstOrNull()?.action
    }
    val emit = clickableAction
    if (emit != null || node.role == "action") {
        val actionName = emit ?: ""
        modifier = modifier.clickable(enabled = actionName.isNotEmpty()) {
            if (actionName.isNotEmpty()) onAction(actionName)
        }
    }
    return modifier
}
