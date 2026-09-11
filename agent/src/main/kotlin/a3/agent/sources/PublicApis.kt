package a3.agent.sources

import a3.agent.model.HONEST_USER_AGENT
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class OpenLibraryDoc(
    val title: String,
    val coverUrl: String?,
    val key: String?,
    val author: String?
)

data class OpenLibraryHits(
    val query: String,
    val fetchedAt: Instant,
    val apiTimestamp: Instant,
    val docs: List<OpenLibraryDoc>
)

/**
 * Official public APIs adopted for structured data. No scraping.
 * eBay / Open Food Facts / MusicBrainz: URL builders (credentials not required for search links).
 * Open Library: live search.json used by AG-008.
 */
object AdoptedApis {
    val names = listOf("eBay Developers", "Open Library", "Open Food Facts", "MusicBrainz")

    fun ebaySearch(query: String): String =
        "https://www.ebay.com/sch/i.html?_nkw=${enc(query)}"

    fun openFoodFactsSearch(query: String): String =
        "https://world.openfoodfacts.org/cgi/search.pl?search_terms=${enc(query)}&search_simple=1&json=1"

    fun musicBrainzRecording(query: String): String =
        "https://musicbrainz.org/ws/2/recording/?query=${enc(query)}&fmt=json"

    fun openLibrarySearch(query: String, limit: Int = 3): String =
        "https://openlibrary.org/search.json?q=${enc(query)}&limit=$limit"

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}

class OpenLibraryClient(
    private val clock: () -> Instant,
    private val timeout: Duration = Duration.ofSeconds(8),
    private val base: String = "https://openlibrary.org"
) {
    init {
        System.setProperty("java.net.preferIPv4Stack", "true")
    }
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(timeout)
        .build()

    fun search(query: String, limit: Int = 3): OpenLibraryHits {
        val now = clock()
        val url = if (base == "https://openlibrary.org") {
            AdoptedApis.openLibrarySearch(query, limit)
        } else {
            "$base/search.json?q=${AdoptedApis.run { java.net.URLEncoder.encode(query, Charsets.UTF_8) }}&limit=$limit"
        }
        val curled = curlGet(url)
        if (curled != null) {
            val docs = parseDocs(curled.body, limit)
            if (docs.isNotEmpty()) {
                val apiTs = curled.date?.let { parseHttpDate(it) } ?: now
                return OpenLibraryHits(query, now, apiTs, docs)
            }
        }
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .header("User-Agent", HONEST_USER_AGENT)
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            "Open Library HTTP ${response.statusCode()}"
        }
        val body = response.body()
        val apiTs = response.headers().firstValue("date").orElse(null)?.let { parseHttpDate(it) } ?: now
        val docs = parseDocs(body, limit)
        require(docs.isNotEmpty()) { "Open Library returned no docs for '$query'" }
        return OpenLibraryHits(query, now, apiTs, docs)
    }

    private data class CurlBody(val body: String, val date: String?)

    private fun curlGet(url: String): CurlBody? {
        return try {
            val body = Files.createTempFile("openlibrary", ".json")
            val headers = Files.createTempFile("openlibrary", ".hdr")
            val proc = ProcessBuilder(
                "curl", "-sS", "-L", "-m", "45", "--ipv4",
                "-A", HONEST_USER_AGENT,
                "-H", "Accept: application/json",
                "-D", headers.toAbsolutePath().toString(),
                "-o", body.toAbsolutePath().toString(),
                "-w", "%{http_code}",
                url
            ).start()
            val code = proc.inputStream.bufferedReader().readText().trim()
            val err = proc.errorStream.bufferedReader().readText()
            val wait = proc.waitFor()
            val text = Files.readString(body)
            val hdr = Files.readString(headers)
            Files.deleteIfExists(body)
            Files.deleteIfExists(headers)
            if (wait != 0 || !code.startsWith("2")) {
                System.err.println("openlibrary curl exit=$wait code=$code err=$err")
                return null
            }
            val date = hdr.lineSequence()
                .firstOrNull { it.startsWith("date:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
            CurlBody(text, date)
        } catch (e: Exception) {
            System.err.println("openlibrary curl ${e.message}")
            null
        }
    }

    companion object {
        internal fun parseDocs(json: String, limit: Int): List<OpenLibraryDoc> {
            val array = sliceArray(json, "docs") ?: return emptyList()
            return objects(array).take(limit).map { obj ->
                val cover = jsonNumber(obj, "cover_i")
                OpenLibraryDoc(
                    title = jsonString(obj, "title") ?: "untitled",
                    coverUrl = cover?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" },
                    key = jsonString(obj, "key"),
                    author = firstAuthor(obj)
                )
            }
        }

        private fun firstAuthor(obj: String): String? {
            val names = sliceArray(obj, "author_name") ?: return null
            val m = Regex("\"((?:\\\\.|[^\"\\\\])*)\"").find(names)
            return m?.groupValues?.get(1)
        }

        private fun sliceArray(json: String, key: String): String? {
            val needle = "\"$key\""
            val keyAt = json.indexOf(needle)
            if (keyAt < 0) return null
            val bracket = json.indexOf('[', keyAt)
            if (bracket < 0) return null
            var depth = 0
            for (i in bracket until json.length) {
                when (json[i]) {
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return json.substring(bracket + 1, i)
                    }
                }
            }
            return null
        }

        private fun objects(arrayInner: String): List<String> {
            val out = ArrayList<String>()
            var depth = 0
            var start = -1
            for (i in arrayInner.indices) {
                when (arrayInner[i]) {
                    '{' -> {
                        if (depth == 0) start = i
                        depth++
                    }
                    '}' -> {
                        depth--
                        if (depth == 0 && start >= 0) {
                            out += arrayInner.substring(start, i + 1)
                            start = -1
                        }
                    }
                }
            }
            return out
        }

        private fun jsonString(obj: String, key: String): String? {
            val m = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(obj)
            return m?.groupValues?.get(1)?.replace("\\\"", "\"")
        }

        private fun jsonNumber(obj: String, key: String): Long? {
            val m = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)").find(obj)
            return m?.groupValues?.get(1)?.toLongOrNull()
        }

        private fun parseHttpDate(raw: String): Instant? =
            runCatching { ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
    }
}
