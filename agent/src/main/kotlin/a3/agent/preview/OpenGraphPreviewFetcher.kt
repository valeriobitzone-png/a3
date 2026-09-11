package a3.agent.preview

import a3.agent.model.HONEST_USER_AGENT
import a3.agent.model.LinkPreview
import a3.agent.model.PreviewLevel
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class HttpGet(
    val status: Int,
    val body: String,
    val timedOut: Boolean = false,
    val error: String? = null
)

fun interface PreviewHttp {
    fun get(url: String, timeout: Duration): HttpGet
}

class JdkPreviewHttp(
    private val userAgent: String = HONEST_USER_AGENT
) : PreviewHttp {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    override fun get(url: String, timeout: Duration): HttpGet {
        return try {
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            HttpGet(response.statusCode(), response.body() ?: "")
        } catch (_: HttpTimeoutException) {
            HttpGet(0, "", timedOut = true, error = "timeout")
        } catch (e: Exception) {
            val timedOut = e.message?.contains("timed out", ignoreCase = true) == true ||
                e.javaClass.simpleName.contains("Timeout")
            HttpGet(0, "", timedOut = timedOut, error = e.message)
        }
    }
}

/**
 * Honest OpenGraph preview. Respects robots.txt. Never forces a fetch.
 * Fallback: OpenGraph → Twitter Card → meta → minima (retailer+query) → deep-link.
 */
class OpenGraphPreviewFetcher(
    private val clock: () -> Instant,
    private val http: PreviewHttp = JdkPreviewHttp(),
    private val timeout: Duration = Duration.ofSeconds(3),
    private val previewTtl: Duration = Duration.ofMinutes(15),
    private val minInterval: Duration = Duration.ZERO,
    private val userAgent: String = HONEST_USER_AGENT
) {
    private val cache = ConcurrentHashMap<String, LinkPreview>()
    private val lock = Any()
    private var lastCallMs = 0L
    val pageGets = ConcurrentHashMap<String, Int>()
    val robotsGets = ConcurrentHashMap<String, Int>()

    fun fetch(
        url: String,
        retailer: String,
        query: String,
        amazonUnreliable: Boolean = false
    ): LinkPreview {
        val now = clock()
        cache[url]?.let { hit ->
            val exp = hit.expiresAt
            if (exp != null && now.isBefore(exp)) return hit.copy(fromCache = true)
        }
        val minima = minima(url, retailer, query, amazonUnreliable, now)
        val uri = runCatching { URI.create(url) }.getOrNull()
            ?: return remember(minima.copy(level = PreviewLevel.DEEPLINK, fallbackReason = "bad-url"))
        val origin = originOf(uri)
        val robotsUrl = "$origin/robots.txt"
        acquire()
        robotsGets.merge(robotsUrl, 1, Int::plus)
        val robots = http.get(robotsUrl, timeout)
        if (robots.timedOut) {
            return remember(minima.copy(fallbackReason = "robots-timeout", robotsAllowed = true, pageFetched = false))
        }
        val robotsBody = if (robots.status in 200..299) robots.body else null
        val allowed = RobotsTxt.allowed(robotsBody, userAgent, uri.rawPath.ifBlank { "/" })
        if (!allowed) {
            return remember(
                minima.copy(
                    robotsAllowed = false,
                    pageFetched = false,
                    fallbackReason = "robots-deny"
                )
            )
        }
        acquire()
        pageGets.merge(url, 1, Int::plus)
        val page = http.get(url, timeout)
        if (page.timedOut) {
            return remember(minima.copy(fallbackReason = "timeout", robotsAllowed = true, pageFetched = false))
        }
        if (antiBot(page) || amazonUnreliable && (page.status == 403 || page.status == 503)) {
            return remember(
                minima.copy(
                    fallbackReason = "anti-bot",
                    amazonUnreliable = true,
                    robotsAllowed = true,
                    pageFetched = true
                )
            )
        }
        if (page.status !in 200..299 || page.body.isBlank()) {
            return remember(
                minima.copy(
                    fallbackReason = "http-${page.status}",
                    amazonUnreliable = amazonUnreliable,
                    pageFetched = true
                )
            )
        }
        val tags = HtmlMeta.parse(page.body)
        val selected = select(tags, url, retailer, query, amazonUnreliable, now)
        return remember(selected)
    }

    private fun select(
        tags: HtmlMeta.Tags,
        url: String,
        retailer: String,
        query: String,
        amazonUnreliable: Boolean,
        now: Instant
    ): LinkPreview {
        val expires = now.plus(previewTtl)
        val ogTitle = tags.og["og:title"]
        val ogDesc = tags.og["og:description"]
        val ogImage = tags.og["og:image"]
        val ogUrl = tags.og["og:url"]
        val ogPrice = HtmlMeta.priceOf(tags)
        if (!ogTitle.isNullOrBlank() || !ogImage.isNullOrBlank()) {
            return LinkPreview(
                url = url,
                title = ogTitle ?: tags.title,
                description = ogDesc,
                imageUrl = ogImage,
                price = ogPrice,
                level = PreviewLevel.OPENGRAPH,
                fetchedAt = now,
                expiresAt = expires,
                robotsAllowed = true,
                pageFetched = true,
                amazonUnreliable = amazonUnreliable,
                fallbackReason = if (amazonUnreliable) "amazon-unreliable" else null,
                retailer = retailer,
                query = query
            ).let { if (ogUrl.isNullOrBlank()) it else it }
        }
        val twTitle = tags.twitter["twitter:title"]
        val twDesc = tags.twitter["twitter:description"]
        val twImage = tags.twitter["twitter:image"]
        if (!twTitle.isNullOrBlank() || !twImage.isNullOrBlank() || tags.twitter["twitter:card"] != null) {
            return LinkPreview(
                url = url,
                title = twTitle ?: tags.title,
                description = twDesc ?: tags.name["description"],
                imageUrl = twImage,
                price = ogPrice,
                level = PreviewLevel.TWITTER,
                fetchedAt = now,
                expiresAt = expires,
                robotsAllowed = true,
                pageFetched = true,
                amazonUnreliable = amazonUnreliable,
                fallbackReason = "no-opengraph",
                retailer = retailer,
                query = query
            )
        }
        val metaDesc = tags.name["description"]
        if (!tags.title.isNullOrBlank() || !metaDesc.isNullOrBlank()) {
            return LinkPreview(
                url = url,
                title = tags.title,
                description = metaDesc,
                imageUrl = null,
                price = ogPrice,
                level = PreviewLevel.META,
                fetchedAt = now,
                expiresAt = expires,
                robotsAllowed = true,
                pageFetched = true,
                amazonUnreliable = amazonUnreliable,
                fallbackReason = "no-opengraph-twitter",
                retailer = retailer,
                query = query
            )
        }
        return minima(url, retailer, query, amazonUnreliable, now).copy(
            pageFetched = true,
            robotsAllowed = true,
            fallbackReason = "no-meta"
        )
    }

    private fun minima(
        url: String,
        retailer: String,
        query: String,
        amazonUnreliable: Boolean,
        now: Instant
    ): LinkPreview {
        val title = if (query.isBlank()) retailer else "$retailer · $query"
        return LinkPreview(
            url = url,
            title = title,
            description = "deep-link + metadata minimi",
            imageUrl = null,
            price = null,
            level = PreviewLevel.MINIMA,
            fetchedAt = now,
            expiresAt = now.plus(previewTtl),
            robotsAllowed = true,
            pageFetched = false,
            amazonUnreliable = amazonUnreliable,
            fallbackReason = null,
            retailer = retailer,
            query = query
        )
    }

    private fun remember(preview: LinkPreview): LinkPreview {
        cache[preview.url] = preview
        return preview
    }

    private fun antiBot(page: HttpGet): Boolean {
        if (page.status == 403 || page.status == 429) return true
        val body = page.body.lowercase()
        return "captcha" in body ||
            "robot check" in body ||
            "automated access" in body ||
            "api-gateway" in body && "denied" in body
    }

    private fun originOf(uri: URI): String {
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return "${uri.scheme}://${uri.host}$port"
    }

    private fun acquire() {
        if (minInterval.isZero || minInterval.isNegative) return
        synchronized(lock) {
            val wait = lastCallMs + minInterval.toMillis() - System.currentTimeMillis()
            if (wait > 0) Thread.sleep(wait)
            lastCallMs = System.currentTimeMillis()
        }
    }
}
