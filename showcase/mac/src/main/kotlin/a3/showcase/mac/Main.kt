package a3.showcase.mac

import a3.showcase.LoggingSink
import a3.showcase.ShowcaseJournal
import a3.showcase.ShowcaseLevel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import java.io.File

fun main(args: Array<String>) = application {
    val level = ShowcaseLevel.parse(args.find { it.startsWith("--level=") }?.substringAfter("="))
    val reduced = args.contains("--reduced")
    val talkback = args.contains("--talkback")
    val glass = !args.contains("--blur-off")
    val silent = args.contains("--silent")
    val ambient = args.contains("--ambient")
    val tour = args.contains("--record")
    val journal = ShowcaseJournal()
    val sink = MacShowcaseSink(journal)
    Window(
        onCloseRequest = {
            sink.flush(journal)
            exitApplication()
        },
        title = "a3ui showcase",
        state = WindowState(width = 1280.dp, height = 800.dp)
    ) {
            ShowcaseApp(
                formFactor = "desktop",
                initialLevel = level,
                initialReduced = reduced,
                initialTalkback = talkback,
                initialGlass = glass,
                initialSilent = silent,
                initialAmbient = ambient,
                tour = tour,
            journal = journal,
            sink = sink,
            onFlush = { j -> sink.flush(j) }
        )
    }
}

internal fun assetDir(): File {
    val fromProp = System.getProperty("a3.showcase.assets")
    if (!fromProp.isNullOrBlank()) return File(fromProp)
    return File("../../review-assets/showcase/mac")
}
