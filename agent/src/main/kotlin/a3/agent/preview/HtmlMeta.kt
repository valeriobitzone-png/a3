package a3.agent.preview

internal object HtmlMeta {
    private val META = Regex(
        "<meta\\s+([^>]+)/?>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val ATTR = Regex(
        """([a-zA-Z:_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))"""
    )
    private val TITLE = Regex(
        "<title[^>]*>([^<]*)</title>",
        RegexOption.IGNORE_CASE
    )
    private val ITEMPROP_PRICE = Regex(
        """<(?:meta|span|div)[^>]*itemprop\s*=\s*['"]price['"][^>]*(?:content\s*=\s*['"]([^'"]+)['"][^>]*)?(?:>([^<]*)</(?:span|div)>)?""",
        RegexOption.IGNORE_CASE
    )

    data class Tags(
        val og: Map<String, String>,
        val twitter: Map<String, String>,
        val name: Map<String, String>,
        val itemprop: Map<String, String>,
        val title: String?,
        val itemPrice: String?
    )

    fun parse(html: String): Tags {
        val og = LinkedHashMap<String, String>()
        val twitter = LinkedHashMap<String, String>()
        val name = LinkedHashMap<String, String>()
        val itemprop = LinkedHashMap<String, String>()
        for (match in META.findAll(html)) {
            val attrs = attrs(match.groupValues[1])
            val content = attrs["content"] ?: continue
            val property = attrs["property"]?.lowercase()
            val metaName = attrs["name"]?.lowercase()
            val item = attrs["itemprop"]?.lowercase()
            when {
                property != null && property.startsWith("og:") -> og[property] = decode(content)
                property != null && property.startsWith("product:") -> og[property] = decode(content)
                metaName != null && metaName.startsWith("twitter:") -> twitter[metaName] = decode(content)
                metaName != null -> name[metaName] = decode(content)
                item != null -> itemprop[item] = decode(content)
            }
        }
        val title = TITLE.find(html)?.groupValues?.get(1)?.let { decode(it.trim()) }
        val itemPrice = itemprop["price"]
            ?: ITEMPROP_PRICE.find(html)?.let { m ->
                listOf(m.groupValues[1], m.groupValues[2]).firstOrNull { it.isNotBlank() }
            }
        return Tags(og, twitter, name, itemprop, title, itemPrice?.let { decode(it.trim()) })
    }

    fun priceOf(tags: Tags): String? {
        val keys = listOf(
            "og:price:amount",
            "product:price:amount",
            "og:price",
            "product:price"
        )
        for (key in keys) {
            val v = tags.og[key]
            if (!v.isNullOrBlank()) return v
        }
        return tags.itemPrice ?: tags.itemprop["price"]
    }

    private fun attrs(raw: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (match in ATTR.findAll(raw)) {
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2].ifEmpty {
                match.groupValues[3].ifEmpty { match.groupValues[4] }
            }
            out[key] = value
        }
        return out
    }

    private fun decode(value: String): String =
        value.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
}
