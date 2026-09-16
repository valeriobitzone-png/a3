// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import a3.renderers.android.core.model.RenderedNode
import a3.renderers.android.core.model.RenderedOutput
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.ArrayList
import javax.imageio.ImageIO

/**
 * Seeded AWT raster of RenderedOutput. Same marks as MacCatalog / android-compose v0.10.
 * Clock-free; anti-alias off.
 */
object MacRaster {
    const val SEED_WIDTH = 320
    const val SEED_HEIGHT = 80
    const val LOW_BAR_X1 = 2
    const val LOW_BAR_X2 = 7
    const val UNKNOWN_INSET = 12

    fun paint(
        output: RenderedOutput,
        width: Int = SEED_WIDTH,
        height: Int = SEED_HEIGHT,
        highContrast: Boolean = false
    ): BufferedImage {
        val leaves = flatten(output.nodes)
        if (leaves.size <= 1) {
            return paint(leaf(output.nodes), width, height, highContrast)
        }
        val image = BufferedImage(width, height * leaves.size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, image.width, image.height)
        var index = 0
        for (node in leaves) {
            val slice = paint(node, width, height, highContrast)
            g.drawImage(slice, 0, index * height, null)
            index++
        }
        g.dispose()
        return image
    }

    fun paintSpoken(
        output: RenderedOutput,
        width: Int = SEED_WIDTH,
        height: Int = SEED_HEIGHT
    ): BufferedImage {
        val leaves = flatten(output.nodes)
        val line = 22
        val image = BufferedImage(width, height * leaves.size + line * leaves.size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, image.width, image.height)
        var y = 0
        for (node in leaves) {
            g.drawImage(paint(node, width, height, highContrast = false), 0, y, null)
            y += height
            g.color = Color.BLACK
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
            g.font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            g.drawString(MacExposure.contentDescription(node), 8, y - 6)
        }
        g.dispose()
        return image
    }

    fun paint(
        node: RenderedNode,
        width: Int = SEED_WIDTH,
        height: Int = SEED_HEIGHT,
        highContrast: Boolean = false
    ): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        val axis = MacExposure.of(node)
        val ink = Color.BLACK
        val amber = if (highContrast) ink else Color(204, 136, 0)
        val stroke = BasicStroke(3f)
        g.stroke = stroke
        g.color = ink
        when (axis.support) {
            "medium" -> g.drawLine(2, 0, 2, height)
            "low" -> {
                g.drawLine(LOW_BAR_X1, 0, LOW_BAR_X1, height)
                g.drawLine(LOW_BAR_X2, 0, LOW_BAR_X2, height)
            }
            "unknown" -> {
                g.stroke = BasicStroke(
                    3f,
                    BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER,
                    10f,
                    floatArrayOf(16f, 10f),
                    0f
                )
                val inset = UNKNOWN_INSET
                g.drawRect(inset, inset, width - inset * 2, height - inset * 2)
                g.stroke = stroke
            }
        }
        when (axis.freshness) {
            "aging" -> {
                if (highContrast) {
                    g.stroke = BasicStroke(1f)
                    var x = 0
                    while (x < width + height) {
                        g.drawLine(x, 0, x - height, height)
                        x += 8
                    }
                    g.stroke = stroke
                } else {
                    g.color = Color(0, 0, 0, 15)
                    g.fillRect(0, 0, width, height)
                    g.color = ink
                }
            }
            "stale" -> {
                g.color = amber
                g.drawRect(1, 1, width - 2, height - 2)
                if (highContrast) {
                    g.color = ink
                    g.stroke = BasicStroke(1f)
                    g.drawRect(5, 5, width - 10, height - 10)
                    g.stroke = stroke
                }
                g.color = ink
            }
        }
        val alpha = (255 * MacExposure.textAlpha(axis)).toInt().coerceIn(0, 255)
        g.color = Color(0, 0, 0, alpha)
        g.font = Font(Font.DIALOG, Font.PLAIN, 28)
        if (axis.status == "contradicted" || axis.action == "compensated") {
            val fm = g.fontMetrics
            val text = node.text.ifEmpty { "·" }
            val x = 16
            val y = 36
            g.drawString(text, x, y)
            g.color = Color(0, 0, 0, alpha)
            g.drawLine(x, y - fm.ascent / 2, x + fm.stringWidth(text), y - fm.ascent / 2)
        } else {
            g.drawString(node.text.ifEmpty { "·" }, 16, 36)
        }
        g.color = ink
        g.font = Font(Font.DIALOG, Font.PLAIN, 18)
        if (axis.status == "held") g.drawString("[held]", 200, 36)
        if (axis.status == "contradicted") g.drawString("contradicted", 160, 60)
        if (axis.support == "unknown") g.drawString("uncertain", 16, 60)
        if (axis.freshness == "stale") g.drawString("stale", 120, 60)
        if (axis.freshness == "aging") g.drawString("/", 200, 60)
        if (axis.support == "medium") g.drawString("|", 8, 60)
        if (axis.support == "low") g.drawString("||", 8, 60)
        if (axis.action == "unknown") g.drawString("[unknown]", 200, 60)
        if (axis.action == "done") g.drawString("v", 240, 36)
        if (axis.action == "compensated") g.drawString("compensated", 140, 60)
        if (axis.action == "pending") {
            g.stroke = stroke
            g.draw(Arc2D.Float(280f, 8f, 24f, 24f, 0f, 270f, Arc2D.OPEN))
        }
        g.dispose()
        return image
    }

    fun inkCount(image: BufferedImage, x: Int): Int {
        var count = 0
        var y = 0
        while (y < image.height) {
            if (image.getRGB(x, y) != Color.WHITE.rgb && image.getRGB(x, y) != 0) count++
            y++
        }
        return count
    }

    fun rowInk(image: BufferedImage, y: Int): Int {
        var count = 0
        var x = 0
        while (x < image.width) {
            if (image.getRGB(x, y) != Color.WHITE.rgb && image.getRGB(x, y) != 0) count++
            x++
        }
        return count
    }

    fun fingerprint(image: BufferedImage): Long {
        var hash = 17L
        var x = 0
        while (x < image.width) {
            var y = 0
            while (y < image.height) {
                hash = 31L * hash + image.getRGB(x, y).toLong()
                y += 1
            }
            x += 1
        }
        return hash xor (image.width.toLong() shl 16) xor image.height.toLong()
    }

    fun write(image: BufferedImage, file: File) {
        file.parentFile?.mkdirs()
        ImageIO.write(image, "png", file)
    }

    fun flatten(nodes: List<RenderedNode>): List<RenderedNode> {
        val out = ArrayList<RenderedNode>()
        fun walk(node: RenderedNode) {
            if (node.role == "text" || node.role == "item" || node.role == "action" || node.role == "field") {
                out += node
            }
            for (child in node.children) walk(child)
        }
        for (node in nodes) walk(node)
        return out
    }

    fun leaf(nodes: List<RenderedNode>): RenderedNode {
        val found = flatten(nodes).firstOrNull()
        return found ?: nodes.first()
    }
}
