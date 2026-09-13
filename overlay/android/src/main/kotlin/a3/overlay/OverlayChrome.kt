package a3.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
    phase: OverlayPhase,
    mark: OverlayActionMark,
    blurUnavailable: Boolean,
    reducedMotion: Boolean = false,
    dismissOutside: Boolean = true,
    profile: ProfileDecision? = null,
    onPill: () -> Unit,
    onSettings: () -> Unit,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (phase == OverlayPhase.COLLAPSED) {
        Box(
            Modifier
                .fillMaxWidth()
                .testTag("overlay-collapsed"),
            contentAlignment = Alignment.TopEnd
        ) {
            Column(horizontalAlignment = Alignment.End) {
                if (profile != null) {
                    ProfileDebugChip(profile)
                }
                OverlayPill(mark = mark, onTap = onPill, onLongPress = onSettings)
            }
        }
        return
    }
    Box(Modifier.fillMaxSize().testTag("overlay-expanded")) {
            OverlaySheet(
            session = session,
            blurUnavailable = blurUnavailable,
            reducedMotion = reducedMotion,
            dismissOutside = dismissOutside,
            profile = profile,
            onChoose = onChoose,
            onDismiss = onDismiss
        )
        Box(Modifier.fillMaxWidth().padding(top = 16.dp, end = 16.dp), contentAlignment = Alignment.TopEnd) {
            Column(horizontalAlignment = Alignment.End) {
                if (profile != null) ProfileDebugChip(profile)
                OverlayPill(mark = mark, onTap = onPill, onLongPress = onSettings)
            }
        }
    }
}

@Composable
fun OverlayPill(
    mark: OverlayActionMark,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val text = OverlayLifecycle.pillText(mark)
    val ink = TextStyle(color = Color(0xFFF5F2EC), fontSize = 18.sp)
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xCC0C0C10))
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { onTap() }
                )
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("overlay-pill")
    ) {
        BasicText(text, modifier = Modifier.testTag("overlay-pill-mark"), style = ink)
    }
}

@Composable
fun OverlaySheet(
    session: OverlaySession,
    blurUnavailable: Boolean,
    reducedMotion: Boolean,
    dismissOutside: Boolean,
    profile: ProfileDecision? = null,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var drag by remember { mutableStateOf(0f) }
    val ink = TextStyle(color = Color(0xFFF5F2EC), fontSize = 16.sp)
    val mute = TextStyle(color = Color(0xFFDCD8D0), fontSize = 14.sp)
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (dismissOutside) Modifier.clickable(onClick = onDismiss) else Modifier
            )
            .pointerInput(reducedMotion) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag > 80f) onDismiss()
                        drag = 0f
                    },
                    onVerticalDrag = { _, dy -> drag += dy }
                )
            }
            .testTag("overlay-chrome")
    ) {
        if (reducedMotion) {
            BasicText("", modifier = Modifier.testTag("overlay-reduced-motion"))
        }
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 64.dp)
        ) {
            if (profile != null) {
                BasicText(
                    profile.pillText,
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("profile-pill"),
                    style = mute
                )
                BasicText(
                    profile.reason,
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("profile-reason"),
                    style = mute
                )
                Spacer(Modifier.height(8.dp))
            }
            if (blurUnavailable) {
                BasicText(
                    profile?.blurMessage ?: OverlayPolicy.BLUR_UNAVAILABLE,
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("overlay-blur-unavailable"),
                    style = TextStyle(color = Color(0xFFFFD278), fontSize = 16.sp)
                )
                Spacer(Modifier.height(10.dp))
            }
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
}

@Composable
fun ProfileDebugChip(decision: ProfileDecision) {
    val mute = TextStyle(color = Color(0xFFDCD8D0), fontSize = 12.sp)
    Box(
        Modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC0C0C10))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("profile-pill")
    ) {
        BasicText(decision.pillText, style = mute)
    }
}
