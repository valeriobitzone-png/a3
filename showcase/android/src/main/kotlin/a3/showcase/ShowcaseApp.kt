// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.showcase

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import a3.overlay.A3UiProfile
import a3.overlay.MotionMode
import a3.overlay.ProfileDetect
import a3.renderers.android.compose.A3UiProfileProvider
import a3.renderers.android.compose.AlphaMaskedStrip
import a3.renderers.android.compose.ComposeRenderer
import a3.renderers.android.compose.EpistemicAnnounce
import a3.renderers.android.compose.GlassOpticsLayer
import a3.renderers.android.compose.ParticleBurst
import a3.renderers.android.compose.Theme
import kotlinx.coroutines.delay

@Composable
fun ShowcaseApp(
    formFactor: String = "phone",
    initialLevel: ShowcaseLevel = ShowcaseLevel.ALL,
    initialReduced: Boolean = false,
    initialTalkback: Boolean = false,
    initialGlass: Boolean = true,
    initialProfile: A3UiProfile? = null,
    initialDetected: A3UiProfile = A3UiProfile.HIGH,
    initialSilent: Boolean = false,
    initialAmbient: Boolean = false,
    initialHighContrast: Boolean = false,
    tour: Boolean = false,
    staticChrome: Boolean = false,
    journal: ShowcaseJournal = remember { ShowcaseJournal() },
    sink: ShowcaseSink = remember { LoggingSink(journal) },
    onFlush: (ShowcaseJournal) -> Unit = {}
) {
    var level by remember { mutableStateOf(initialLevel) }
    var reduced by remember { mutableStateOf(initialReduced) }
    var talkback by remember { mutableStateOf(initialTalkback) }
    var override by remember {
        mutableStateOf(initialProfile ?: if (!initialGlass) A3UiProfile.BLUR_OFF else null)
    }
    var detected by remember { mutableStateOf(initialDetected) }
    val decision = remember(override, detected) { ProfileDetect.resolve(detected, override) }
    val profile = decision.active
    val matrix = decision.matrix
    val glassOn = matrix.blurEnabled
    var silent by remember { mutableStateOf(initialSilent) }
    var highContrast by remember { mutableStateOf(initialHighContrast) }
    var modal by remember { mutableStateOf(false) }
    var ambientExpanded by remember { mutableStateOf(initialAmbient) }
    var particle by remember { mutableStateOf(false) }
    var sceneTick by remember { mutableStateOf(0) }

    val output = remember(formFactor, reduced, sceneTick) {
        CalendarFixture.output(formFactor, reduced)
    }
    val flags = level.flags()
    val announce = remember(talkback, journal, sink) {
        if (!talkback) EpistemicAnnounce.Silent
        else EpistemicAnnounce { id, phrase ->
            sink.announce(id, phrase)
        }
    }

    fun fireSensory(vararg causes: String) {
        for (cause in causes) {
            if (cause in ShowcaseSensory.audioCauses()) {
                sink.playAudio(cause, reduced, silent = silent)
            }
            if (cause in ShowcaseSensory.hapticCauses()) {
                sink.playHaptic(cause, reduced, engine = !silent)
            }
        }
        if ((flags.particles || level == ShowcaseLevel.ALL) && matrix.particles) {
            particle = true
        }
    }

    LaunchedEffect(level, talkback, output) {
        if (!talkback) return@LaunchedEffect
        val phrases = if (level == ShowcaseLevel.ALL) ShowcaseScene.announce()
        else ShowcaseSensory.announceAll(output)
        for ((id, phrase) in phrases) {
            sink.announce(id, phrase)
        }
    }

    LaunchedEffect(level, reduced, flags.sensory, sceneTick, silent) {
        if (!flags.sensory) return@LaunchedEffect
        val causes = if (level == ShowcaseLevel.ALL) ShowcaseScene.causes()
        else ShowcaseSensory.causesOf(output)
        for (cause in causes) {
            if (cause in ShowcaseSensory.audioCauses()) {
                sink.playAudio(cause, reduced, silent = silent)
            }
            if (cause in ShowcaseSensory.hapticCauses()) {
                sink.playHaptic(cause, reduced, engine = !silent)
            }
        }
    }

    LaunchedEffect(particle) {
        if (!particle) return@LaunchedEffect
        delay(420)
        particle = false
    }

    LaunchedEffect(tour) {
        if (!tour) return@LaunchedEffect
        delay(1800)
        fireSensory(ShowcaseSensory.Cause.CONFIRM)
        delay(900)
        modal = true
        delay(1400)
        modal = false
        ambientExpanded = true
        delay(1400)
        ambientExpanded = false
        talkback = true
        delay(800)
        for (next in ShowcaseLevel.NAV) {
            level = next
            delay(1600)
        }
        reduced = true
        sceneTick++
        delay(1600)
        reduced = false
        sceneTick++
        delay(800)
        level = ShowcaseLevel.ALL
        delay(1200)
        onFlush(journal)
    }

    val tokens = ShowcaseTokens.snapshot
    val paper = Color(((tokens.paper shr 16) and 0xFF) / 255f, ((tokens.paper shr 8) and 0xFF) / 255f, (tokens.paper and 0xFF) / 255f)
    val ink = Color(((tokens.ink shr 16) and 0xFF) / 255f, ((tokens.ink shr 8) and 0xFF) / 255f, (tokens.ink and 0xFF) / 255f)

    Column(
        Modifier
            .fillMaxSize()
            .background(paper)
            .testTag("showcase-host")
    ) {
        Box(Modifier.size(1.dp).testTag("showcase-level-${level.wire()}"))
        Box(Modifier.size(1.dp).testTag("profile-active-${profile.wire()}"))
        Box(Modifier.weight(1f).fillMaxWidth().testTag("showcase-scene")) {
            A3UiProfileProvider(profile) {
                Box(Modifier.fillMaxSize()) {
                    ShowcaseWallpaperLayer(blurred = false, Modifier.fillMaxSize())
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(
                                start = ShowcaseWallpaper.GUTTER_DP.dp,
                                top = ShowcaseWallpaper.GUTTER_DP.dp,
                                end = ShowcaseWallpaper.GUTTER_DP.dp,
                                bottom = 128.dp
                            )
                            .testTag("showcase-wallpaper-gutter")
                    ) {
                ShowcaseGlassPlate(Modifier.fillMaxSize(), frost = glassOn) {
                    if (flags.gradient) {
                        ShowcaseGradient(staticChrome = staticChrome || reduced || matrix.motion == MotionMode.SIMPLIFIED)
                    }
                    if (flags.parallax && matrix.motion == MotionMode.FULL) {
                        ShowcaseParallax()
                    }
                    Box(Modifier.fillMaxSize().padding(12.dp).testTag("showcase-glass-inset")) {
                        if (level == ShowcaseLevel.ALL) {
                            ShowcaseScenePane(
                                ink = ink,
                                amber = Color(
                                    ((tokens.amber shr 16) and 0xFF) / 255f,
                                    ((tokens.amber shr 8) and 0xFF) / 255f,
                                    (tokens.amber and 0xFF) / 255f
                                ),
                                type = Theme.type,
                                frost = glassOn,
                                reducedMotion = reduced || staticChrome,
                                highContrast = highContrast,
                                ambient = {
                                    ShowcaseAmbient(
                                        expanded = ambientExpanded,
                                        reduced = reduced || staticChrome,
                                        ink = ink,
                                        paper = paper
                                    )
                                }
                            )
                        } else if (level == ShowcaseLevel.OVERLAY) {
                            ShowcaseOverlayPane(ink = ink, type = Theme.type)
                        } else {
                            ComposeRenderer(
                                output = output,
                                onAction = { fireSensory(ShowcaseSensory.Cause.CONFIRM) },
                                announce = announce,
                                highContrast = highContrast,
                                profile = profile
                            )
                        }
                    }
                    if (flags.optics && matrix.noise) {
                        ShowcaseGrain()
                        GlassOpticsLayer(refract = true, noise = true)
                        AlphaMaskedStrip()
                    }
                    if (matrix.particles && (flags.particles || particle)) {
                        ShowcaseParticles(trigger = particle, staticChrome = staticChrome)
                        ParticleBurst(trigger = particle)
                    }
                    if (flags.ambient && level != ShowcaseLevel.ALL) {
                        ShowcaseAmbient(
                            expanded = ambientExpanded,
                            reduced = reduced || staticChrome,
                            ink = ink,
                            paper = paper,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                    if (modal && flags.modal) {
                        ShowcaseModal(open = true, reduced = reduced || staticChrome)
                    }
                    }
                }
            }
            }
            BasicText(
                text = decision.pillText,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .testTag("profile-pill"),
                style = Theme.type.copy(fontSize = 12.sp, color = ink)
            )
            if (decision.blurMessage != null) {
                BasicText(
                    text = decision.blurMessage!!,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .testTag("profile-blur-message"),
                    style = Theme.type.copy(fontSize = 12.sp, color = ink)
                )
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                Controls(
                    level = level,
                    reduced = reduced,
                    talkback = talkback,
                    glassOn = glassOn,
                    profile = profile,
                    sourceManual = override != null,
                    silent = silent,
                    highContrast = highContrast,
                    ink = ink,
                    onLevel = { level = it },
                    onReduced = {
                        reduced = !reduced
                        sceneTick++
                    },
                    onTalkback = { talkback = !talkback },
                    onGlass = {
                        override = if (glassOn) A3UiProfile.BLUR_OFF else A3UiProfile.HIGH
                    },
                    onProfile = { next -> override = next },
                    onSilent = { silent = !silent },
                    onHighContrast = { highContrast = !highContrast },
                    onModal = { modal = !modal },
                    onAmbient = { ambientExpanded = !ambientExpanded },
                    onSensory = { fireSensory(ShowcaseSensory.Cause.CONFIRM, ShowcaseSensory.Cause.TAP) }
                )
                ShowcaseGlassPlate(Modifier.padding(8.dp), nested = true, frost = glassOn) {
                    BasicText(
                        text = "${decision.pillText} reduced=${if (reduced) "on" else "off"} talkback=${if (talkback) "on" else "off"} blur=${if (glassOn) "on" else "off"} silent=${if (silent) "on" else "off"} contrast=${if (highContrast) "on" else "off"} ${level.wire()}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).testTag("showcase-status"),
                        style = Theme.type.copy(fontSize = 12.sp, color = ink)
                    )
                }
            }
        }
    }
}

@Composable
private fun Controls(
    level: ShowcaseLevel,
    reduced: Boolean,
    talkback: Boolean,
    glassOn: Boolean,
    profile: A3UiProfile,
    sourceManual: Boolean,
    silent: Boolean,
    highContrast: Boolean,
    ink: Color,
    onLevel: (ShowcaseLevel) -> Unit,
    onReduced: () -> Unit,
    onTalkback: () -> Unit,
    onGlass: () -> Unit,
    onProfile: (A3UiProfile?) -> Unit,
    onSilent: () -> Unit,
    onHighContrast: () -> Unit,
    onModal: () -> Unit,
    onAmbient: () -> Unit,
    onSensory: () -> Unit
) {
    val style = Theme.type.copy(fontSize = 13.sp, color = ink)
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("showcase-controls")) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (item in listOf(ShowcaseLevel.ALL, ShowcaseLevel.GLASS, ShowcaseLevel.AXIS, ShowcaseLevel.MOTION)) {
                ShowcaseGlassChip(item.wire(), "nav-${item.wire()}", item == level, ink) { onLevel(item) }
            }
        }
        Row(
            Modifier.padding(top = 4.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (item in listOf(ShowcaseLevel.DYNAMIC, ShowcaseLevel.SHADERS, ShowcaseLevel.SENSORY, ShowcaseLevel.OVERLAY)) {
                ShowcaseGlassChip(item.wire(), "nav-${item.wire()}", item == level, ink) { onLevel(item) }
            }
        }
        Row(
            Modifier.padding(top = 6.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShowcaseGlassChip("reduced ${if (reduced) "on" else "off"}", "toggle-reduced", reduced, ink, onReduced)
            ShowcaseGlassChip("talkback ${if (talkback) "on" else "off"}", "toggle-talkback", talkback, ink, onTalkback)
            ShowcaseGlassChip("blur ${if (glassOn) "on" else "off"}", "toggle-blur", !glassOn, ink, onGlass)
            ShowcaseGlassChip("silent ${if (silent) "on" else "off"}", "toggle-silent", silent, ink, onSilent)
            ShowcaseGlassChip("contrast ${if (highContrast) "on" else "off"}", "toggle-contrast", highContrast, ink, onHighContrast)
        }
        Row(
            Modifier.padding(top = 4.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ShowcaseGlassChip("auto", "profile-auto", !sourceManual, ink) { onProfile(null) }
            for (item in A3UiProfile.entries) {
                ShowcaseGlassChip(item.wire(), "profile-${item.wire()}", sourceManual && profile == item, ink) {
                    onProfile(item)
                }
            }
        }
        Row(
            Modifier.padding(top = 4.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShowcaseGlassChip("modal", "trigger-modal", false, ink, onModal)
            ShowcaseGlassChip("ambient", "trigger-ambient", false, ink, onAmbient)
            ShowcaseGlassChip("sensory", "trigger-sensory", false, ink, onSensory)
        }
        BasicText("a3ui showcase", style = style)
    }
}

@Composable
internal fun ShowcaseGradient(staticChrome: Boolean) {
    val pal = remember { ShowcaseChromeMath.palette() }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(staticChrome) {
        if (staticChrome) {
            t = 0f
            return@LaunchedEffect
        }
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (last != 0L) t += ((now - last) / 1_000_000_000f).coerceAtMost(0.032f)
            last = now
        }
    }
    Canvas(Modifier.fillMaxSize().testTag("animated-gradient")) {
        val step = 12
        var y = 0
        while (y < size.height.toInt()) {
            var x = 0
            while (x < size.width.toInt()) {
                val u = x / size.width
                val v = y / size.height
                val packed = ShowcaseChromeMath.shade(u, v, t, pal)
                drawRect(
                    color = Color(
                        ((packed shr 16) and 0xFF) / 255f,
                        ((packed shr 8) and 0xFF) / 255f,
                        (packed and 0xFF) / 255f
                    ),
                    topLeft = Offset(x.toFloat(), y.toFloat()),
                    size = Size(step.toFloat(), step.toFloat())
                )
                x += step
            }
            y += step
        }
    }
}

@Composable
internal fun ShowcaseParallax() {
    Box(Modifier.fillMaxSize().testTag("parallax-layers")) {
        Box(Modifier.offset(0.dp, 0.dp).size(12.dp).background(Color.Black.copy(alpha = 0.08f)).testTag("parallax-bg"))
        Box(Modifier.offset(0.dp, 3.dp).size(12.dp).background(Color.Black.copy(alpha = 0.10f)).testTag("parallax-mid"))
        Box(Modifier.offset(0.dp, 6.dp).size(12.dp).background(Color.Black.copy(alpha = 0.12f)).testTag("parallax-fg"))
    }
}

@Composable
internal fun ShowcaseGrain() {
    Canvas(Modifier.fillMaxSize().testTag("glass-noise-overlay")) {
        val step = 18
        var y = 0
        while (y < size.height.toInt()) {
            var x = 0
            while (x < size.width.toInt()) {
                val g = ShowcaseChromeMath.noiseAt(x, y)
                if (g > 0.012f) {
                    drawRect(
                        color = Color.White.copy(alpha = g.coerceIn(0f, 0.08f)),
                        topLeft = Offset(x.toFloat(), y.toFloat()),
                        size = Size(step.toFloat(), step.toFloat())
                    )
                }
                x += step
            }
            y += step
        }
    }
}

@Composable
internal fun ShowcaseParticles(trigger: Boolean, staticChrome: Boolean) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(trigger, staticChrome) {
        t = 0f
        if (!trigger || staticChrome) return@LaunchedEffect
        var last = 0L
        while (t < 1f) {
            val now = withFrameNanos { it }
            if (last != 0L) t = (t + (now - last) / 400_000_000f).coerceAtMost(1f)
            last = now
        }
    }
    val sparks = if (trigger) ShowcaseChromeMath.stepSparks(ShowcaseChromeMath.emit(), t) else emptyList()
    Canvas(Modifier.fillMaxSize().testTag(if (trigger) "particle-canvas" else "particle-canvas-idle")) {
        for (spark in sparks) {
            drawCircle(
                color = Color(
                    ((ShowcaseTokens.snapshot.amber shr 16) and 0xFF) / 255f,
                    ((ShowcaseTokens.snapshot.amber shr 8) and 0xFF) / 255f,
                    (ShowcaseTokens.snapshot.amber and 0xFF) / 255f,
                    spark.life
                ),
                radius = 6f * spark.life,
                center = Offset(spark.x * size.width, spark.y * size.height)
            )
        }
    }
}

@Composable
internal fun ShowcaseAmbient(expanded: Boolean, reduced: Boolean, ink: Color, paper: Color, modifier: Modifier = Modifier) {
    val tokens = ShowcaseTokens.snapshot
    var t by remember { mutableFloatStateOf(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded, reduced) {
        val target = if (expanded) 1f else 0f
        if (reduced) {
            t = target
            return@LaunchedEffect
        }
        val spring = tokens.comfortable
        var s = ShowcaseChromeMath.SpringState(t, 0f)
        var last = 0L
        var frames = 0
        withFrameNanos { last = it }
        while (kotlin.math.abs(s.x - target) > 0.01f && frames < 480) {
            val now = withFrameNanos { it }
            val raw = (now - last) / 1_000_000_000f
            val dt = if (raw <= 0f) 1f / 240f else raw.coerceAtMost(0.032f)
            last = now
            s = ShowcaseChromeMath.springStep(s, target, spring.mass, spring.stiffness, spring.damping, dt)
            t = s.x.coerceIn(0f, 1.2f)
            frames++
        }
        t = target
    }
    val rect = ShowcaseChromeMath.morph(t)
    Box(
        modifier
            .zIndex(4f)
            .padding(8.dp)
            .size(rect.w.dp, rect.h.dp)
            .background(Color.Black.copy(alpha = tokens.fillOpacity), RoundedCornerShape(rect.radius.dp))
            .testTag("ambient-indicator"),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().testTag(if (expanded) "ambient-expanded" else "ambient-collapsed"))
        BasicText(
            text = if (expanded) ShowcaseScene.DEADLINE else "09:00",
            modifier = Modifier.testTag("ambient-deadline"),
            style = Theme.type.copy(fontSize = 12.sp, color = paper)
        )
    }
}

@Composable
internal fun ShowcaseModal(open: Boolean, reduced: Boolean) {
    val tokens = ShowcaseTokens.snapshot
    var t by remember { mutableFloatStateOf(if (open) 1f else 0f) }
    LaunchedEffect(open, reduced) {
        val target = if (open) 1f else 0f
        if (reduced) {
            t = target
            return@LaunchedEffect
        }
        val spring = tokens.comfortable
        var s = ShowcaseChromeMath.SpringState(t, 0f)
        var last = 0L
        var frames = 0
        withFrameNanos { last = it }
        while (kotlin.math.abs(s.x - target) > 0.01f && frames < 480) {
            val now = withFrameNanos { it }
            val raw = (now - last) / 1_000_000_000f
            val dt = if (raw <= 0f) 1f / 240f else raw.coerceAtMost(0.032f)
            last = now
            s = ShowcaseChromeMath.springStep(s, target, spring.mass, spring.stiffness, spring.damping, dt)
            t = s.x
            frames++
        }
        t = target
    }
    val blur = ShowcaseChromeMath.blurPx(t)
    val alpha = ShowcaseChromeMath.overlayAlpha(t)
    Box(Modifier.fillMaxSize().zIndex(tokens.fillOpacity * 10f).testTag("modal-surface")) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("modal-backdrop")
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= 31 && blur > 0.5f) {
                        renderEffect = RenderEffect.createBlurEffect(
                            blur,
                            blur,
                            Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    }
                }
        )
        Box(
            Modifier
                .fillMaxSize()
                .testTag("modal-scrim")
                .background(Color.Black.copy(alpha = alpha))
        )
        Box(Modifier.align(Alignment.Center).padding(24.dp).testTag("modal-body")) {
            BasicText("confirm", style = Theme.type.copy(color = Color.White))
        }
    }
}
