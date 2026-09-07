package a3.broker

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

private val FORBIDDEN = listOf("belief", "admission", "fold", "digest", "claim")

class Register(private val home: Path) {
    private val file: Path = home.resolve("log.txt")

    fun append(line: String) {
        require(FORBIDDEN.none { needle -> line.lowercase().contains(needle) }) {
            "register line contains a forbidden word"
        }
        Files.createDirectories(home)
        Files.writeString(
            file,
            line.trimEnd() + "\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }

    fun text(): String = if (Files.exists(file)) Files.readString(file) else ""

    fun forbiddenHits(): List<String> {
        val body = text().lowercase()
        return FORBIDDEN.filter { it in body }
    }
}

internal fun logLine(at: Instant, actor: String, rest: String): String =
    "${at}  $actor  $rest"
