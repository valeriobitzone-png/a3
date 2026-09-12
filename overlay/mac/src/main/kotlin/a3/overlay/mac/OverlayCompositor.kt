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
        ambientExpanded: Boolean = false
    ): BufferedImage {
        val base = when (backdrop) {
            BackdropMode.REAL_BLUR -> blur(screen, 18)
            BackdropMode.UNAVAILABLE -> copy(screen)
        }
        val g = base.createGraphics()
        hints(g)
        if (backdrop == BackdropMode.UNAVAILABLE) {
            banner(g, base.width, OverlayPolicy.BLUR_UNAVAILABLE)
        }
        pill(g, base.width, session.ambient, ambientExpanded)
        val cardW = minOf(920, (base.width * 0.72).toInt().coerceAtLeast(280))
        val cardH = 118
        val x = (base.width - cardW) / 2
        var y = (base.height * 0.22).toInt().coerceAtLeast(160)
        for (card in session.cards) {
            glassCard(g, x, y, cardW, cardH, card)
            y += cardH + 22
        }
        g.dispose()
        return base
    }

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
        val label = if (expanded) "$text  ·  swipe down to hide" else text
        g.font = Font("SansSerif", Font.BOLD, 15)
        val tw = g.fontMetrics.stringWidth(label)
        val w = tw + 48
        val h = 36
        val x = width - w - 28
        val y = 28
        val shape = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 18f, 18f)
        g.color = Color(12, 12, 16, 180)
        g.fill(shape)
        g.color = Color(245, 242, 236)
        g.drawString(label, x + 24, y + 24)
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
