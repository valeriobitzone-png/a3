package a3.broker

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

data class ToolReply(
    val accepted: Boolean,
    val body: String,
    val silent: Boolean = false,
    val timedOut: Boolean = false
)

class LocalTools(
    private val access: AttenuatedAccess,
    private val irreversible: Set<String>
) {
    private var server: HttpServer? = null
    private var boundUrl: String = ""
    var delayMs: Long = 0
    var hang: Boolean = false
    val calls = mutableListOf<String>()
    private val revoked = ConcurrentHashMap.newKeySet<String>()

    fun start(): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.executor = Executors.newCachedThreadPool()
        http.createContext("/health") { ex ->
            val bytes = "ok".toByteArray(StandardCharsets.UTF_8)
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        http.createContext("/revoke") { ex ->
            val id = field(ex.requestBody.readAllBytes().toString(StandardCharsets.UTF_8), "grant")
            if (id.isNotBlank()) revoked.add(id)
            val bytes = """{"accepted":true}""".toByteArray(StandardCharsets.UTF_8)
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        http.createContext("/tool") { ex ->
            if (hang) Thread.sleep(60_000)
            if (delayMs > 0) Thread.sleep(delayMs)
            val raw = ex.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            val tool = field(raw, "tool")
            val token = field(raw, "token")
            val grant = field(raw, "grant")
            synchronized(calls) { calls += tool }
            val needsToken = tool in irreversible
            val ok = when {
                grant.isNotBlank() && grant in revoked -> false
                needsToken && token.isBlank() -> false
                needsToken && !access.allows(token, tool) -> false
                else -> true
            }
            val body = if (ok) "ok:$tool" else "refused"
            val json = """{"accepted":$ok,"body":"$body"}"""
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            ex.sendResponseHeaders(if (ok) 200 else 403, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        http.start()
        server = http
        boundUrl = "http://127.0.0.1:${http.address.port}"
        return boundUrl
    }

    fun address(): String = boundUrl

    fun stop() {
        server?.stop(0)
        server = null
    }
}

class ToolsClient(private val timeout: Duration) {
    private val http = HttpClient.newBuilder().connectTimeout(timeout).build()

    fun call(base: String, tool: String, token: String?, grantId: String?): ToolReply {
        val payload =
            """{"tool":"$tool","token":"${token.orEmpty()}","grant":"${grantId.orEmpty()}"}"""
        val req = HttpRequest.newBuilder(URI.create("$base/tool"))
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()
        return try {
            val res = http.send(req, HttpResponse.BodyHandlers.ofString())
            ToolReply(accepted = res.body().contains("\"accepted\":true"), body = res.body())
        } catch (_: java.net.http.HttpTimeoutException) {
            ToolReply(accepted = false, body = "", silent = true, timedOut = true)
        } catch (_: Exception) {
            ToolReply(accepted = false, body = "", silent = true)
        }
    }

    fun revoke(base: String, grantId: String) {
        val req = HttpRequest.newBuilder(URI.create("$base/revoke"))
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString("""{"grant":"$grantId"}"""))
            .build()
        runCatching { http.send(req, HttpResponse.BodyHandlers.ofString()) }
    }
}

private fun field(raw: String, name: String): String {
    val key = "\"$name\":\""
    val from = raw.indexOf(key)
    if (from < 0) return ""
    val start = from + key.length
    val end = raw.indexOf('"', start)
    if (end < 0) return ""
    return raw.substring(start, end)
}
