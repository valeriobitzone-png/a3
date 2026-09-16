// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.agent.surface

import a3.a3ui.model.EpistemicAction
import a3.a3ui.model.EpistemicFreshness
import a3.a3ui.model.EpistemicStatus
import a3.a3ui.model.EpistemicSupport
import a3.a3ui.model.Node
import a3.agent.model.AgentSession
import a3.agent.model.ChoiceOutcome
import a3.agent.model.SurfaceOption
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Raster of real composed surfaces (atoms + epistemic axis), not concept mocks.
 */
object SurfaceShot {
    private val paper = Color(18, 18, 22)
    private val ink = Color(245, 242, 236)
    private val muted = Color(170, 168, 160)
    private val amber = Color(220, 160, 60)
    private val held = Color(90, 140, 220)
    private val unknown = Color(200, 80, 90)
    private val ok = Color(90, 180, 120)

    fun session(session: AgentSession, title: String): BufferedImage {
        val lines = ArrayList<Line>()
        lines += Line(title, ink, 18, true)
        lines += Line("plan ${session.plan.activities.joinToString("/") { it.name.lowercase() }}", muted, 12, false)
        for (group in session.groups) {
            lines += Line(group.kind.name, ink, 15, true)
            for (opt in group.options) {
                lines += Line(opt.title, ink, 13, false)
                val extra = listOfNotNull(
                    opt.price,
                    opt.previewLevel?.wire()?.let { "level=$it" },
                    axisLabel(opt),
                    opt.imageUrl?.let { "image" },
                    opt.subtitle?.take(48)
                )
                lines += Line(extra.joinToString("  ·  "), color(opt), 11, false)
            }
        }
        return paint(lines, 900, 64 + lines.size * 28)
    }

    fun option(option: SurfaceOption, title: String): BufferedImage {
        val lines = listOf(
            Line(title, ink, 18, true),
            Line(option.title, ink, 16, false),
            Line(option.subtitle ?: "", muted, 12, false),
            Line("price ${option.price ?: "—"}", ink, 13, false),
            Line("image ${option.imageUrl ?: "—"}", muted, 12, false),
            Line("level ${option.previewLevel?.wire() ?: "—"}", ink, 13, false),
            Line(axisLabel(option), color(option), 13, false),
            Line("nodes ${flatten(option.tree.nodes).joinToString { it.id }}", muted, 11, false)
        )
        return paint(lines, 900, 420)
    }

    fun gate(consent: String, opened: Boolean): BufferedImage {
        val lines = listOf(
            Line("outbound gate", ink, 18, true),
            Line(if (opened) "opened" else "did not open", if (opened) ok else unknown, 16, false),
            Line("broker default ON · irreversible: open", muted, 12, false)
        ) + consent.lines().map { Line(it.take(80), ink, 13, false) }
        return paint(lines, 900, 64 + (lines.size) * 28)
    }

    fun outcome(choice: ChoiceOutcome): BufferedImage {
        val axis = flatten(choice.tree.nodes).mapNotNull { it.axis }.firstOrNull()
        val verb = axis?.action?.wire() ?: choice.outcome.name.lowercase()
        val lines = listOf(
            Line("action outcome", ink, 18, true),
            Line("${choice.outcome.name} · phase ${choice.action.actionPhase}", ink, 14, false),
            Line("epistemic verb $verb", colorForAction(axis?.action), 14, false),
            Line("gated=${choice.gated} opened=${choice.opened}", muted, 12, false)
        )
        return paint(lines, 900, 280)
    }

    fun write(image: BufferedImage, file: File) {
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    fun html(session: AgentSession): String = buildString {
        appendLine("<!doctype html><meta charset=utf-8><title>agent surface</title>")
        for (group in session.groups) {
            appendLine("<h2>${group.kind}</h2><ul>")
            for (opt in group.options) {
                appendLine(
                    "<li data-level='${opt.previewLevel?.wire() ?: ""}' data-axis='${axisLabel(opt)}'>" +
                        "${escape(opt.title)} ${escape(opt.price ?: "")}</li>"
                )
            }
            appendLine("</ul>")
        }
    }

    private fun paint(lines: List<Line>, width: Int, height: Int): BufferedImage {
        val image = BufferedImage(width, height.coerceAtLeast(200), BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = paper
        g.fillRect(0, 0, width, height)
        var y = 36
        for (line in lines) {
            g.font = Font("SansSerif", if (line.bold) Font.BOLD else Font.PLAIN, line.size)
            g.color = line.color
            g.drawString(line.text.take(110), 24, y)
            y += line.size + 12
        }
        g.dispose()
        return image
    }

    private fun axisLabel(option: SurfaceOption): String {
        val axis = option.axis
        val bits = ArrayList<String>()
        bits += "support ${axis.support.wire()}"
        bits += "freshness ${axis.freshness.wire()}"
        bits += "status ${axis.status.wire()}"
        if (axis.action != EpistemicAction.NA) bits += "action ${axis.action.wire()}"
        if (option.admissionHeld) bits += "HELD"
        return bits.joinToString(" ")
    }

    private fun color(option: SurfaceOption): Color {
        val axis = option.axis
        return when {
            axis.status == EpistemicStatus.HELD || option.admissionHeld -> held
            axis.freshness == EpistemicFreshness.STALE -> amber
            axis.support == EpistemicSupport.UNKNOWN || axis.support == EpistemicSupport.LOW -> unknown
            else -> muted
        }
    }

    private fun colorForAction(action: EpistemicAction?): Color = when (action) {
        EpistemicAction.DONE -> ok
        EpistemicAction.UNKNOWN, EpistemicAction.PENDING -> unknown
        else -> muted
    }

    private fun flatten(nodes: List<Node>): List<Node> {
        val out = ArrayList<Node>()
        fun walk(node: Node) {
            out += node
            node.children.forEach { walk(it) }
        }
        nodes.forEach { walk(it) }
        return out
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private data class Line(val text: String, val color: Color, val size: Int, val bold: Boolean)
}
