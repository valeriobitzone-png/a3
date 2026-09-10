package a3.renderers.android.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import a3.a3ui.model.EpistemicAction
import a3.a3ui.model.EpistemicFreshness
import a3.a3ui.model.EpistemicStatus
import a3.a3ui.model.EpistemicSupport
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
    GlassSurface(
        modifier = Modifier
            .testTag("a3-catalog")
            .fillMaxSize(),
        nested = false
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Theme.space)
        ) {
            for (node in nodes) {
                CatalogNode(node, gestures, onAction, extra = occupancy(node.role), depth = 1)
            }
        }
    }
}

@Composable
private fun CatalogNode(
    node: RenderedNode,
    gestures: List<SemanticGestureAction>,
    onAction: (String) -> Unit,
    extra: Modifier = Modifier,
    depth: Int = 0
) {
    val targeted = ArrayList<SemanticGestureAction>()
    for (gesture in gestures) {
        if (gesture.targetNodeId == node.id) targeted += gesture
    }
    val axis = Exposure.of(node)
    val highContrast = LocalHighContrast.current
    val announce = LocalEpistemicAnnounce.current
    val phrases = Exposure.announcePhrases(axis)
    LaunchedEffect(node.id, phrases) {
        for (phrase in phrases) announce.announce(node.id, phrase)
    }
    val tagged = nodeModifier(node, targeted, onAction)
        .exposureChrome(axis, highContrast)
        .exposureLayer(node)
    val modifier = extra.then(tagged)
    val nested = depth > 0
    when (node.role) {
        "stack" -> GlassSurface(modifier, nested) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Theme.space)) {
                for (child in node.children) {
                    CatalogNode(child, gestures, onAction, extra = occupancy(child.role), depth = depth + 1)
                }
            }
        }
        "row" -> GlassSurface(modifier, nested) {
            Row(Modifier.fillMaxWidth()) {
                for (child in node.children) {
                    CatalogNode(child, gestures, onAction, extra = Modifier.fillMaxWidth(), depth = depth + 1)
                }
            }
        }
        "list" -> GlassSurface(modifier, nested) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(node.children, key = { it.id }) { child ->
                    CatalogNode(child, gestures, onAction, extra = Modifier.fillMaxWidth(), depth = depth + 1)
                }
            }
        }
        "item" -> GlassSurface(modifier, nested) {
            Children(node, gestures, onAction, depth + 1); BoundCopy(node); AxisCopy(node); ExposureMarks(node)
        }
        "action" -> Box(modifier) { Children(node, gestures, onAction, depth + 1); BoundCopy(node); AxisCopy(node); ExposureMarks(node) }
        "field" -> Column(extra) {
            BasicTextField(
                value = node.text,
                onValueChange = {},
                readOnly = true,
                textStyle = Exposure.style(node, highContrast),
                modifier = tagged
            )
            AxisCopy(node)
            ExposureMarks(node)
        }
        "text" -> Column(extra) {
            BasicText(text = node.text, modifier = tagged, style = Exposure.style(node, highContrast))
            AxisCopy(node)
            ExposureMarks(node)
        }
        else -> Box(modifier)
    }
}

@Composable
private fun Children(
    node: RenderedNode,
    gestures: List<SemanticGestureAction>,
    onAction: (String) -> Unit,
    depth: Int
) {
    for (child in node.children) {
        CatalogNode(child, gestures, onAction, depth = depth)
    }
}

@Composable
private fun BoundCopy(node: RenderedNode) {
    if (node.text.isNotEmpty()) {
        BasicText(text = node.text, style = Exposure.style(node, LocalHighContrast.current))
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

@Composable
private fun ExposureMarks(node: RenderedNode) {
    val axis = Exposure.of(node)
    val reduced = LocalReducedMotion.current
    val verb = Exposure.verb(axis)
    if (verb != null && !reduced) {
        Box(Modifier.size(1.dp).testTag(node.id + "-motion-" + verb.wire()))
    }
    if (axis.isDefault()) return
    Row(Modifier.testTag(node.id + "-marks").sizeIn(minWidth = 8.dp, minHeight = 8.dp)) {
        if (axis.support == EpistemicSupport.MEDIUM) {
            BasicText("|", Modifier.testTag(node.id + "-medium"), Theme.type)
        }
        if (axis.support == EpistemicSupport.LOW) {
            BasicText("||", Modifier.testTag(node.id + "-low"), Theme.type)
        }
        if (axis.support == EpistemicSupport.UNKNOWN) {
            BasicText("uncertain", Modifier.testTag(node.id + "-uncertain"), Theme.type)
        }
        if (axis.freshness == EpistemicFreshness.AGING) {
            BasicText("/", Modifier.testTag(node.id + "-aging"), Theme.type)
        }
        if (axis.freshness == EpistemicFreshness.STALE) {
            BasicText("stale", Modifier.testTag(node.id + "-stale"), Theme.type)
        }
        if (axis.status == EpistemicStatus.HELD) {
            BasicText("[held]", Modifier.testTag(node.id + "-held"), Theme.type)
        }
        if (axis.status == EpistemicStatus.CONTRADICTED) {
            BasicText("contradicted", Modifier.testTag(node.id + "-contradicted"), Theme.type)
        }
        if (axis.action == EpistemicAction.PENDING) {
            PendingMark(node.id)
        }
        if (axis.action == EpistemicAction.UNKNOWN) {
            BasicText("[unknown]", Modifier.testTag(node.id + "-unknown"), Theme.type)
        }
        if (axis.action == EpistemicAction.DONE) {
            BasicText("✓", Modifier.testTag(node.id + "-done"), Theme.type)
        }
        if (axis.action == EpistemicAction.COMPENSATED) {
            BasicText("compensated", Modifier.testTag(node.id + "-compensated"), Theme.type)
        }
    }
}

@Composable
private fun PendingMark(id: String) {
    val reduced = LocalReducedMotion.current
    val angle = if (reduced) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = id + "-pending")
        val spinning by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing)
            ),
            label = "pending-spin"
        )
        spinning
    }
    Canvas(
        Modifier
            .size(12.dp)
            .testTag(id + "-pending")
            .graphicsLayer { rotationZ = angle }
    ) {
        drawArc(
            color = Color.Black,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx())
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
    val axis = node.axis
    val nonDefault = axis != null && !axis.isDefault()
    val spoken = Exposure.contentDescription(node)
    val axisDesc = node.stateDescription.takeIf { it.isNotEmpty() }
    val critical = Exposure.announcePhrases(Exposure.of(node)).isNotEmpty()
    modifier = modifier.semantics {
        if (spoken.isNotEmpty()) contentDescription = spoken
        if (axisDesc != null) stateDescription = axisDesc
        traversalIndex = if (nonDefault) 0f else 1f
        if (critical) liveRegion = LiveRegionMode.Assertive
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
