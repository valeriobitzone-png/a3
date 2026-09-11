package a3.agent

import com.sun.net.httpserver.HttpServer
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import javax.imageio.ImageIO

class PreviewFixtureServer(
    private val robotsBody: String,
    private val slowMs: Long = 0
) {
    private var server: HttpServer? = null
    val paths = CopyOnWriteArrayList<String>()
    lateinit var origin: String
        private set

    fun start(): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.executor = Executors.newCachedThreadPool()
        http.createContext("/") { ex ->
            val path = ex.requestURI.path
            paths += path
            when {
                path == "/robots.txt" -> text(ex, 200, robotsBody, "text/plain")
                path == "/cover.png" -> bytes(ex, 200, png(), "image/png")
                path == "/og" -> text(ex, 200, ogHtml(), "text/html")
                path == "/twitter" -> text(ex, 200, twitterHtml(), "text/html")
                path == "/meta" -> text(ex, 200, metaHtml(), "text/html")
                path == "/bare" -> text(ex, 200, "<html><body>no meta</body></html>", "text/html")
                path == "/amazon" -> text(ex, 403, "automated access denied", "text/html")
                path == "/slow" -> {
                    if (slowMs > 0) Thread.sleep(slowMs)
                    text(ex, 200, ogHtml(), "text/html")
                }
                path == "/product" -> text(ex, 200, ogHtml(), "text/html")
                else -> text(ex, 404, "missing", "text/plain")
            }
        }
        http.start()
        server = http
        origin = "http://127.0.0.1:${http.address.port}"
        return origin
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun ogHtml(): String = """
        <html><head>
        <meta property="og:title" content="Sony WH-1000XM5">
        <meta property="og:description" content="Cuffie over-ear wireless">
        <meta property="og:image" content="$origin/cover.png">
        <meta property="og:url" content="$origin/og">
        <meta property="og:price:amount" content="89.00">
        </head><body>product</body></html>
    """.trimIndent()

    private fun twitterHtml(): String = """
        <html><head>
        <meta name="twitter:card" content="summary">
        <meta name="twitter:title" content="Twitter cuffie">
        <meta name="twitter:description" content="Card only">
        <meta name="twitter:image" content="$origin/cover.png">
        </head><body>twitter</body></html>
    """.trimIndent()

    private fun metaHtml(): String = """
        <html><head>
        <title>Meta cuffie</title>
        <meta name="description" content="Descrizione base">
        </head><body>meta</body></html>
    """.trimIndent()

    private fun text(
        ex: com.sun.net.httpserver.HttpExchange,
        status: Int,
        body: String,
        type: String
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Content-Type", type)
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun bytes(
        ex: com.sun.net.httpserver.HttpExchange,
        status: Int,
        body: ByteArray,
        type: String
    ) {
        ex.responseHeaders.add("Content-Type", type)
        ex.sendResponseHeaders(status, body.size.toLong())
        ex.responseBody.use { it.write(body) }
    }

    private fun png(): ByteArray {
        val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color(200, 40, 40)
        g.fillRect(0, 0, 32, 32)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }
}
