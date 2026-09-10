package a3.renderers.android.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import a3.a3ui.model.IntentCandidate
import a3.renderers.android.core.model.RenderedNode
import a3.renderers.android.core.model.SemanticGestureAction
import java.util.ArrayList

@Composable
fun ComposeCatalog(
    nodes: List<RenderedNode>,
    gestures: List<SemanticGestureAction>,
    onAction: (String) -> Unit
) {
    Column(
        Modifier
            .testTag("a3-catalog")
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Theme.space)
    ) {
        for (node in nodes) {
            CatalogNode(node, gestures, onAction, extra = occupancy(node.role))
        }
    }
}

@Composable
private fun CatalogNode(
    node: RenderedNode,
    gestures: List<SemanticGestureAction>,
    onAction: (String) -> Unit,
    extra: Modifier = Modifier
) {
    val targeted = ArrayList<SemanticGestureAction>()
    for (gesture in gestures) {
        if (gesture.targetNodeId == node.id) targeted += gesture
    }
    val tagged = nodeModifier(node, targeted, onAction)
    val modifier = extra.then(tagged)
    when (node.role) {
        "stack" -> Column(modifier, verticalArrangement = Arrangement.spacedBy(Theme.space)) {
            for (child in node.children) {
                CatalogNode(child, gestures, onAction, extra = occupancy(child.role))
            }
        }
        "row" -> Row(modifier) {
            for (child in node.children) {
                CatalogNode(child, gestures, onAction, extra = Modifier.fillMaxWidth())
            }
        }
        "list" -> LazyColumn(modifier) {
            items(node.children, key = { it.id }) { child ->
                CatalogNode(child, gestures, onAction, extra = Modifier.fillMaxWidth())
            }
        }
        "item" -> Box(modifier) { Children(node, gestures, onAction); BoundCopy(node); AxisCopy(node) }
        "action" -> Box(modifier) { Children(node, gestures, onAction); BoundCopy(node); AxisCopy(node) }
        "field" -> Column(extra) {
            BasicTextField(
                value = node.text,
                onValueChange = {},
                readOnly = true,
                textStyle = Theme.type,
                modifier = tagged
            )
            AxisCopy(node)
        }
        "text" -> Column(extra) {
            BasicText(text = node.text, modifier = tagged, style = Theme.type)
            AxisCopy(node)
        }
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

@Composable
private fun BoundCopy(node: RenderedNode) {
    if (node.text.isNotEmpty()) {
        BasicText(text = node.text, style = Theme.type)
    }
}

@Composable
private fun AxisCopy(node: RenderedNode) {
    if (node.stateDescription.isNotEmpty()) {
        BasicText(
            text = node.stateDescription,
            modifier = Modifier.testTag(node.id + "-axis"),
            style = Theme.type
        )
    }
}

private fun ColumnScope.occupancy(role: String): Modifier = when (role) {
    "list" -> Modifier.weight(Theme.listWeight).fillMaxWidth()
    "field", "text", "action" -> Modifier.weight(Theme.slotWeight).fillMaxWidth()
    "stack", "row" -> Modifier.weight(Theme.slotWeight).fillMaxWidth()
    else -> Modifier.fillMaxWidth()
}

private fun nodeModifier(
    node: RenderedNode,
    targeted: List<SemanticGestureAction>,
    onAction: (String) -> Unit
): Modifier {
    var modifier: Modifier = Modifier
        .testTag(node.id)
        .sizeIn(minWidth = 8.dp, minHeight = 8.dp)
    if (node.role == "action") {
        modifier = modifier.sizeIn(minWidth = Theme.actionMin, minHeight = Theme.actionMin)
    }
    val copy = if (node.text.isNotEmpty() && (node.role == "item" || node.role == "action")) {
        node.text
    } else {
        null
    }
    val axis = node.stateDescription.takeIf { it.isNotEmpty() }
    if (copy != null || axis != null) {
        modifier = modifier.semantics {
            if (copy != null) contentDescription = copy
            if (axis != null) stateDescription = axis
        }
    }
    var clickableAction: String? = null
    var clickableGesture: String? = null
    for (gesture in targeted) {
        if (gesture.gesture == "swipe-left") {
            val candidate = IntentCandidate(gesture.gesture, gesture.action, gesture.targetNodeId)
            modifier = modifier.pointerInput(candidate.action) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < 0f) onAction(candidate.action)
                }
            }
        } else {
            clickableAction = gesture.action
            clickableGesture = gesture.gesture
        }
    }
    if (clickableAction == null && node.role == "action") {
        clickableAction = targeted.firstOrNull()?.action
        clickableGesture = targeted.firstOrNull()?.gesture
    }
    val emit = clickableAction
    if (emit != null || node.role == "action") {
        val actionName = emit ?: ""
        val gestureName = clickableGesture ?: ""
        modifier = modifier.clickable(enabled = actionName.isNotEmpty()) {
            if (actionName.isNotEmpty()) {
                onAction(IntentCandidate(gestureName, actionName, node.id).action)
            }
        }
    }
    return modifier
}
