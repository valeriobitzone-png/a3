package a3.overlay

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

object OverlayCompositor {
    fun compose(
        screen: BufferedImage,
        session: OverlaySession,
        backdrop: BackdropMode,
        phase: OverlayPhase = OverlayPhase.COLLAPSED,
        mark: OverlayActionMark = OverlayActionMark.UNKNOWN,
        ambientExpanded: Boolean = false,
        profile: ProfileDecision = ProfileDetect.resolve(A3UiProfile.HIGH, null)
    ): BufferedImage {
        val seize = phase == OverlayPhase.EXPANDED
        val matrix = profile.matrix
        // Display blur on Mac is NSVisualEffectView (native OverlayMain), not this
        // offscreen BufferedImage. MUST NOT run OverlayBlur on the compose path.
        val base = when {
            !seize -> copy(screen)
            backdrop == BackdropMode.REAL_BLUR && matrix.blurEnabled -> copy(screen)
            else -> {
                val copied = copy(screen)
                if (seize && !matrix.blurEnabled) tint(copied) else copied
            }
        }
        val g = base.createGraphics()
        hints(g)
        if (seize && (backdrop == BackdropMode.UNAVAILABLE || !matrix.blurEnabled)) {
            banner(g, base.width, profile.blurMessage ?: OverlayPolicy.BLUR_UNAVAILABLE)
        }
        profileChip(g, base.width, profile.pillText)
        pill(g, base.width, OverlayLifecycle.pillText(mark), ambientExpanded)
        if (seize) {
            val cardW = minOf(920, (base.width * 0.72).toInt().coerceAtLeast(280))
            val cardH = 118
            val x = (base.width - cardW) / 2
            var y = (base.height * 0.22).toInt().coerceAtLeast(160)
            for (card in session.cards) {
                glassCard(g, x, y, cardW, cardH, card)
                y += cardH + 22
            }
        }
        g.dispose()
        return base
    }

    /**
     * CPU box-blur of an offscreen bitmap. Diagnosis of the old harness only —
     * not the Mac display path. [compose] MUST NOT call this.
     */
    fun blur(src: BufferedImage, radius: Int): BufferedImage {
        val argb = ensureArgb(src)
        val w = argb.width
        val h = argb.height
        val px = IntArray(w * h)
        argb.getRGB(0, 0, w, h, px, 0, w)
        val outPx = OverlayBlur.blur(px, w, h, radius)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        out.setRGB(0, 0, w, h, outPx, 0, w)
        return out
    }

    data class Hotspot(
        val copyMs: Double,
        val blurCpuMs: Double,
        val paintMs: Double
    ) {
        val totalMs: Double get() = copyMs + blurCpuMs + paintMs
        val blurSharePct: Double get() = if (totalMs <= 0.0) 0.0 else 100.0 * blurCpuMs / totalMs
    }

    /** Times copy / CPU OverlayBlur / paint on an offscreen CG-less BufferedImage. */
    fun hotspot(
        screen: BufferedImage,
        session: OverlaySession,
        radius: Int,
        profile: ProfileDecision = ProfileDetect.resolve(A3UiProfile.HIGH, null)
    ): Hotspot {
        val t0 = System.nanoTime()
        val copied = copy(screen)
        val t1 = System.nanoTime()
        blur(copied, radius)
        val t2 = System.nanoTime()
        compose(screen, session, BackdropMode.UNAVAILABLE, OverlayPhase.EXPANDED, profile = profile)
        val t3 = System.nanoTime()
        return Hotspot(
            copyMs = (t1 - t0) / 1_000_000.0,
            blurCpuMs = (t2 - t1) / 1_000_000.0,
            paintMs = (t3 - t2) / 1_000_000.0
        )
    }

    fun variance(a: BufferedImage, b: BufferedImage): Double {
        val w = minOf(a.width, b.width)
        val h = minOf(a.height, b.height)
        var acc = 0.0
        var n = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val pa = a.getRGB(x, y)
                val pb = b.getRGB(x, y)
                acc += kotlin.math.abs(((pa shr 16) and 0xFF) - ((pb shr 16) and 0xFF))
                acc += kotlin.math.abs(((pa shr 8) and 0xFF) - ((pb shr 8) and 0xFF))
                acc += kotlin.math.abs((pa and 0xFF) - (pb and 0xFF))
                n += 3
                x += 8
            }
            y += 8
        }
        return acc / kotlin.math.max(1, n)
    }

    private fun glassCard(g: Graphics2D, x: Int, y: Int, w: Int, h: Int, card: OverlayCard) {
        val shape = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 28f, 28f)
        g.color = Color(255, 255, 255, 56)
        g.fill(shape)
        g.color = Color(255, 255, 255, 90)
        g.stroke = BasicStroke(1.2f)
        g.draw(shape)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.color = Color(250, 248, 242)
        g.drawString(card.title.take(64), x + 22, y + 42)
        g.font = Font("SansSerif", Font.PLAIN, 16)
        g.color = Color(220, 216, 208)
        val sub = listOfNotNull(card.subtitle, card.price).joinToString("  ·  ")
        if (sub.isNotBlank()) g.drawString(sub.take(72), x + 22, y + 72)
        g.font = Font("SansSerif", Font.PLAIN, 13)
        g.color = Color(180, 190, 210)
        g.drawString("apri nel browser", x + 22, y + 98)
    }

    private fun pill(g: Graphics2D, width: Int, text: String, expanded: Boolean) {
        val label = text
        g.font = Font("SansSerif", Font.BOLD, 18)
        val tw = g.fontMetrics.stringWidth(label)
        val w = tw + 36
        val h = 36
        val x = width - w - 28
        val y = 72
        val shape = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 18f, 18f)
        g.color = Color(12, 12, 16, 180)
        g.fill(shape)
        g.color = Color(245, 242, 236)
        g.drawString(label, x + 18, y + 24)
        if (expanded) return
    }

    private fun profileChip(g: Graphics2D, width: Int, text: String) {
        g.font = Font("SansSerif", Font.PLAIN, 13)
        val tw = g.fontMetrics.stringWidth(text)
        val w = tw + 28
        val h = 26
        val x = width - w - 28
        val y = 28
        val shape = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 12f, 12f)
        g.color = Color(12, 12, 16, 204)
        g.fill(shape)
        g.color = Color(220, 216, 208)
        g.drawString(text, x + 14, y + 18)
    }

    private fun tint(src: BufferedImage): BufferedImage {
        val g = src.createGraphics()
        g.color = Color(12, 12, 16, 88)
        g.fillRect(0, 0, src.width, src.height)
        g.dispose()
        return src
    }

    private fun banner(g: Graphics2D, width: Int, text: String) {
        g.font = Font("SansSerif", Font.PLAIN, 16)
        val tw = g.fontMetrics.stringWidth(text)
        g.color = Color(0, 0, 0, 140)
        g.fillRoundRect((width - tw) / 2 - 18, 80, tw + 36, 36, 16, 16)
        g.color = Color(255, 210, 120)
        g.drawString(text, (width - tw) / 2, 104)
    }

    private fun ensureArgb(src: BufferedImage): BufferedImage {
        if (src.type == BufferedImage.TYPE_INT_ARGB) return src
        val copy = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val g = copy.createGraphics()
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return copy
    }

    private fun copy(src: BufferedImage): BufferedImage = ensureArgb(src).let { argb ->
        val out = BufferedImage(argb.width, argb.height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(argb, 0, 0, null)
        g.dispose()
        out
    }

    private fun hints(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }
}
