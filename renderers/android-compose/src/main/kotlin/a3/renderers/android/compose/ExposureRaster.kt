package a3.renderers.android.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import a3.a3ui.model.EpistemicAction
import a3.a3ui.model.EpistemicFreshness
import a3.a3ui.model.EpistemicStatus
import a3.a3ui.model.EpistemicSupport
import a3.renderers.android.core.model.RenderedNode
import a3.renderers.android.core.model.RenderedOutput
import java.io.File
import java.util.ArrayList

/**
 * Seeded raster of [RenderedOutput] + [EpistemicAxis]. Same marks as the
 * catalog chrome; clock-free; anti-alias off so a fixed size is a seed.
 */
object ExposureRaster {
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
    ): Bitmap {
        val leaves = flatten(output.nodes)
        if (leaves.size <= 1) {
            return paint(leaf(output.nodes), width, height, highContrast)
        }
        val bitmap = Bitmap.createBitmap(width, height * leaves.size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        var index = 0
        for (node in leaves) {
            val slice = paint(node, width, height, highContrast)
            canvas.drawBitmap(slice, 0f, (index * height).toFloat(), null)
            index++
        }
        return bitmap
    }

    fun paint(
        node: RenderedNode,
        width: Int = SEED_WIDTH,
        height: Int = SEED_HEIGHT,
        highContrast: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val axis = Exposure.of(node)
        val ink = android.graphics.Color.BLACK
        val amber = if (highContrast) ink else android.graphics.Color.rgb(204, 136, 0)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isAntiAlias = false
            style = Paint.Style.STROKE
            color = ink
            strokeWidth = 3f
        }
        when (axis.support) {
            EpistemicSupport.MEDIUM -> canvas.drawLine(2f, 0f, 2f, height.toFloat(), stroke)
            EpistemicSupport.LOW -> {
                canvas.drawLine(
                    LOW_BAR_X1.toFloat(),
                    0f,
                    LOW_BAR_X1.toFloat(),
                    height.toFloat(),
                    stroke
                )
                canvas.drawLine(
                    LOW_BAR_X2.toFloat(),
                    0f,
                    LOW_BAR_X2.toFloat(),
                    height.toFloat(),
                    stroke
                )
            }
            EpistemicSupport.UNKNOWN -> {
                val dashed = Paint(stroke).apply {
                    pathEffect = DashPathEffect(floatArrayOf(16f, 10f), 0f)
                    strokeWidth = 3f
                }
                val inset = UNKNOWN_INSET.toFloat()
                canvas.drawRect(inset, inset, width - inset, height - inset, dashed)
            }
            EpistemicSupport.HIGH -> { }
        }
        when (axis.freshness) {
            EpistemicFreshness.AGING -> {
                if (highContrast) {
                    val hatch = Paint(stroke).apply { strokeWidth = 1f }
                    var x = 0f
                    while (x < width + height) {
                        canvas.drawLine(x, 0f, x - height, height.toFloat(), hatch)
                        x += 8f
                    }
                } else {
                    val wash = Paint().apply {
                        isAntiAlias = false
                        color = ink
                        alpha = 15
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), wash)
                }
            }
            EpistemicFreshness.STALE -> {
                val border = Paint(stroke).apply { color = amber }
                canvas.drawRect(1f, 1f, width - 1f, height - 1f, border)
                if (highContrast) {
                    canvas.drawRect(5f, 5f, width - 5f, height - 5f, Paint(stroke).apply { strokeWidth = 1f })
                }
            }
            EpistemicFreshness.FRESH -> { }
        }
        val text = Paint().apply {
            isAntiAlias = false
            color = ink
            alpha = (255 * Exposure.textAlpha(axis)).toInt().coerceIn(0, 255)
            textSize = 28f
            isStrikeThruText = axis.status == EpistemicStatus.CONTRADICTED ||
                axis.action == EpistemicAction.COMPENSATED
        }
        canvas.drawText(node.text.ifEmpty { "·" }, 16f, 36f, text)
        val mark = Paint(text).apply {
            alpha = 255
            textSize = 18f
            isStrikeThruText = axis.action == EpistemicAction.COMPENSATED
        }
        if (axis.status == EpistemicStatus.HELD) canvas.drawText("[held]", 200f, 36f, mark)
        if (axis.status == EpistemicStatus.CONTRADICTED) canvas.drawText("contradicted", 160f, 60f, mark)
        if (axis.support == EpistemicSupport.UNKNOWN) canvas.drawText("uncertain", 16f, 60f, mark)
        if (axis.freshness == EpistemicFreshness.STALE) canvas.drawText("stale", 120f, 60f, mark)
        if (axis.freshness == EpistemicFreshness.AGING) canvas.drawText("/", 200f, 60f, mark)
        if (axis.support == EpistemicSupport.MEDIUM) canvas.drawText("|", 8f, 60f, mark)
        if (axis.support == EpistemicSupport.LOW) canvas.drawText("||", 8f, 60f, mark)
        if (axis.action == EpistemicAction.UNKNOWN) canvas.drawText("[unknown]", 200f, 60f, mark)
        if (axis.action == EpistemicAction.DONE) canvas.drawText("v", 240f, 36f, mark)
        if (axis.action == EpistemicAction.COMPENSATED) canvas.drawText("compensated", 140f, 60f, mark)
        if (axis.action == EpistemicAction.PENDING) {
            canvas.drawArc(RectF(280f, 8f, 304f, 32f), 0f, 270f, false, stroke)
        }
        return bitmap
    }

    fun inkCount(bitmap: Bitmap, x: Int): Int {
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            if (bitmap.getPixel(x, y) != android.graphics.Color.WHITE) count++
            y++
        }
        return count
    }

    fun rowInk(bitmap: Bitmap, y: Int): Int {
        var count = 0
        var x = 0
        while (x < bitmap.width) {
            if (bitmap.getPixel(x, y) != android.graphics.Color.WHITE) count++
            x++
        }
        return count
    }

    fun fingerprint(bitmap: Bitmap): Long {
        var hash = 17L
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                hash = 31L * hash + bitmap.getPixel(x, y).toLong()
                y += 1
            }
            x += 1
        }
        return hash xor (bitmap.width.toLong() shl 16) xor bitmap.height.toLong()
    }

    fun write(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    fun leaf(nodes: List<RenderedNode>): RenderedNode {
        val found = findLeaf(nodes)
        return found ?: nodes.first()
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

    private fun findLeaf(nodes: List<RenderedNode>): RenderedNode? {
        for (node in nodes) {
            if (node.role == "text" || node.role == "item" || node.role == "action" || node.role == "field") {
                return node
            }
            val child = findLeaf(node.children)
            if (child != null) return child
        }
        return null
    }
}
