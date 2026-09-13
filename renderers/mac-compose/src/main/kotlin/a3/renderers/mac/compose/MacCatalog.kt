package a3.renderers.mac.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.focusable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import a3.renderers.android.core.model.RenderedNode
import a3.renderers.android.core.model.SemanticGestureAction
import java.util.ArrayList

@Composable
fun MacCatalog(
    nodes: List<RenderedNode>,
    gestures: List<SemanticGestureAction>,
    onAction: (String) -> Unit
) {
    MacGlassSurface(
        modifier = Modifier
            .testTag("a3-catalog")
            .fillMaxSize(),
        nested = false
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(MacTheme.space)
        ) {
            Box(
                Modifier
                    .size(0.dp)
                    .testTag("skip-to-marks")
                    .focusable()
                    .semantics {
                        contentDescription = "Salta ai marchi"
                        traversalIndex = -1f
                    }
            )
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
    val axis = MacExposure.of(node)
    val highContrast = LocalHighContrast.current
    val densityHint = LocalDensityHint.current
    val pressSource = remember(node.id) { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    val tagged = nodeModifier(node, targeted, onAction, pressSource)
        .macChrome(axis, highContrast)
        .macMotion(node)
    val modifier = extra.then(tagged)
    val nested = depth > 0
    when (node.role) {
        "stack" -> MacGlassSurface(modifier, nested) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MacTheme.space)) {
                for (child in node.children) {
                    CatalogNode(child, gestures, onAction, extra = occupancy(child.role), depth = depth + 1)
                }
            }
        }
        "row" -> MacGlassSurface(modifier, nested) {
            Row(Modifier.fillMaxWidth()) {
                for (child in node.children) {
                    CatalogNode(child, gestures, onAction, extra = Modifier.fillMaxWidth(), depth = depth + 1)
                }
            }
        }
        "list" -> MacGlassSurface(extra, nested) {
            LazyColumn(tagged.fillMaxSize().rubberBand()) {
                items(node.children, key = { it.id }) { child ->
                    CatalogNode(child, gestures, onAction, extra = Modifier.fillMaxWidth(), depth = depth + 1)
                }
            }
        }
        "item" -> MacGlassSurface(modifier, nested) {
            Children(node, gestures, onAction, depth + 1); BoundCopy(node, pressed); AxisCopy(node); Marks(node)
        }
        "action" -> Box(modifier.contactRipple()) { Children(node, gestures, onAction, depth + 1); BoundCopy(node, pressed); AxisCopy(node); Marks(node) }
        "field" -> Column(extra) {
            BasicTextField(
                value = node.text,
                onValueChange = {},
                readOnly = true,
                textStyle = typedStyle(node, false),
                modifier = tagged.graphicsLayer { scaleX = DynamicType.width(densityHint) }
            )
            AxisCopy(node)
            Marks(node)
        }
        "text" -> Column(extra) {
            BasicText(
                text = node.text,
                modifier = tagged.graphicsLayer { scaleX = DynamicType.width(densityHint) },
                style = typedStyle(node, false)
            )
            AxisCopy(node)
            Marks(node)
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
private fun BoundCopy(node: RenderedNode, pressed: Boolean = false) {
    if (node.text.isNotEmpty()) {
        val width = DynamicType.width(LocalDensityHint.current)
        BasicText(
            text = node.text,
            style = typedStyle(node, pressed && node.role == "action"),
            modifier = Modifier.graphicsLayer { scaleX = width }
        )
    }
}

@Composable
private fun typedStyle(node: RenderedNode, pressed: Boolean): androidx.compose.ui.text.TextStyle {
    val base = MacExposure.style(node, LocalHighContrast.current)
    return base.copy(
        fontWeight = FontWeight(DynamicType.weight(pressed)),
        letterSpacing = DynamicType.trackingEm(base.fontSize.value).em
    )
}

@Composable
private fun AxisCopy(node: RenderedNode) {
    if (node.stateDescription.isNotEmpty()) {
        BasicText(
            text = node.stateDescription,
            modifier = Modifier.testTag(node.id + "-axis"),
            style = MacTheme.type
        )
    }
}

@Composable
private fun Marks(node: RenderedNode) {
    val axis = MacExposure.of(node)
    val reduced = LocalReducedMotion.current
    val verb = MacExposure.verb(axis)
    if (verb != null && !reduced) {
        Box(Modifier.size(1.dp).testTag(node.id + "-motion-" + verb.wire()))
    }
    if (axis.isDefault()) return
    Row(Modifier.testTag(node.id + "-marks").sizeIn(minWidth = 8.dp, minHeight = 8.dp)) {
        if (axis.support == "medium") {
            BasicText("|", Modifier.testTag(node.id + "-medium"), MacTheme.type)
        }
        if (axis.support == "low") {
            BasicText("||", Modifier.testTag(node.id + "-low"), MacTheme.type)
        }
        if (axis.support == "unknown") {
            BasicText("uncertain", Modifier.testTag(node.id + "-uncertain"), MacTheme.type)
        }
        if (axis.freshness == "aging") {
            BasicText("/", Modifier.testTag(node.id + "-aging"), MacTheme.type)
        }
        if (axis.freshness == "stale") {
            BasicText("stale", Modifier.testTag(node.id + "-stale"), MacTheme.type)
        }
        if (axis.status == "held") {
            BasicText("[held]", Modifier.testTag(node.id + "-held"), MacTheme.type)
        }
        if (axis.status == "contradicted") {
            BasicText("contradicted", Modifier.testTag(node.id + "-contradicted"), MacTheme.type)
        }
        if (axis.action == "pending") {
            PendingMark(node.id)
        }
        if (axis.action == "unknown") {
            BasicText("[unknown]", Modifier.testTag(node.id + "-unknown"), MacTheme.type)
        }
        if (axis.action == "done") {
            BasicText("✓", Modifier.testTag(node.id + "-done"), MacTheme.type)
        }
        if (axis.action == "compensated") {
            BasicText("compensated", Modifier.testTag(node.id + "-compensated"), MacTheme.type)
        }
        EpistemicGlyph(node)
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
        val pts = GlyphGeometry.points(GlyphGeometry.Kind.SPINNER, if (reduced) 1f else angle / 360f, reduced)
        if (pts.size >= 2) {
            val path = Path()
            path.moveTo(pts[0].x * size.width, pts[0].y * size.height)
            var i = 1
            while (i < pts.size) {
                path.lineTo(pts[i].x * size.width, pts[i].y * size.height)
                i++
            }
            drawPath(
                path,
                color = Color.Black,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

private fun ColumnScope.occupancy(role: String): Modifier = when (role) {
    "list" -> Modifier.weight(MacTheme.listWeight).fillMaxWidth()
    "field", "text", "action" -> Modifier.weight(MacTheme.slotWeight).fillMaxWidth()
    "stack", "row" -> Modifier.weight(MacTheme.slotWeight).fillMaxWidth()
    else -> Modifier.fillMaxWidth()
}

@Composable
private fun nodeModifier(
    node: RenderedNode,
    targeted: List<SemanticGestureAction>,
    onAction: (String) -> Unit,
    pressSource: MutableInteractionSource
): Modifier {
    var modifier: Modifier = Modifier
        .testTag(node.id)
        .sizeIn(minWidth = 8.dp, minHeight = 8.dp)
    if (node.role == "action") {
        modifier = modifier.sizeIn(minWidth = MacTheme.actionMin, minHeight = MacTheme.actionMin)
    }
    val axis = MacExposure.of(node)
    val spoken = MacExposure.contentDescription(node)
    val axisDesc = MacExposure.markStateDescription(node).takeIf { it.isNotEmpty() }
    val critical = axis.support == "unknown" ||
        axis.freshness == "stale" ||
        axis.status == "held" ||
        axis.status == "contradicted" ||
        axis.action == "unknown" ||
        axis.action == "compensated"
    val forbid = node.role == "action" && !MacExposure.ctaEnabled(axis)
    modifier = modifier.semantics {
        if (spoken.isNotEmpty()) contentDescription = spoken
        if (axisDesc != null) stateDescription = axisDesc
        traversalIndex = if (!axis.isDefault()) 0f else 1f
        if (critical) liveRegion = LiveRegionMode.Assertive
        if (forbid) disabled()
    }
    val click = targeted.firstOrNull { it.gesture != "swipe-left" }?.action
        ?: if (node.role == "action") targeted.firstOrNull()?.action else null
    if (click != null || node.role == "action") {
        val actionName = click ?: ""
        modifier = modifier.actionPress(enabled = actionName.isNotEmpty() && !forbid, source = pressSource) {
            if (actionName.isNotEmpty()) onAction(actionName)
        }
    }
    return modifier
}
