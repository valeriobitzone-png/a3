package a3.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OverlayChrome(
    session: OverlaySession,
    blurUnavailable: Boolean,
    onHide: () -> Unit,
    onSettings: () -> Unit,
    onChoose: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf(0f) }
    val ink = TextStyle(color = Color(0xFFF5F2EC), fontSize = 16.sp)
    val mute = TextStyle(color = Color(0xFFDCD8D0), fontSize = 14.sp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag > 80f) onHide()
                        drag = 0f
                    },
                    onVerticalDrag = { _, dy -> drag += dy }
                )
            }
            .testTag("overlay-chrome")
    ) {
        Column(
            Modifier
                .align(Alignment.End)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xCC0C0C10))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onSettings() },
                        onTap = { expanded = !expanded }
                    )
                }
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("overlay-ambient")
        ) {
            BasicText(session.ambient, style = ink)
            if (expanded) {
                BasicText("swipe down to hide · long-press settings", style = mute)
            }
        }
        if (blurUnavailable) {
            Spacer(Modifier.height(10.dp))
            BasicText(
                OverlayPolicy.BLUR_UNAVAILABLE,
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("overlay-blur-unavailable"),
                style = TextStyle(color = Color(0xFFFFD278), fontSize = 16.sp)
            )
        }
        Spacer(Modifier.height(24.dp))
        BasicText(session.intent, modifier = Modifier.testTag("overlay-intent"), style = ink)
        Spacer(Modifier.height(16.dp))
        session.cards.forEach { card ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0x38FFFFFF))
                    .pointerInput(card.id) { detectTapGestures { onChoose(card.id) } }
                    .padding(20.dp)
                    .testTag("overlay-card-${card.id}")
            ) {
                BasicText(card.title, style = ink)
                val sub = listOfNotNull(card.subtitle, card.price).joinToString("  ·  ")
                if (sub.isNotBlank()) BasicText(sub, style = mute)
                BasicText("apri nel browser", style = mute)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
