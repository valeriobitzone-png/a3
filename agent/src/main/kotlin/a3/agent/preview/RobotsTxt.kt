package a3.agent.preview

/**
 * Minimal robots.txt: longest matching User-agent group (non-wildcard wins).
 * Disallow prefix match. Missing/unreadable robots → allow. Never override a deny.
 */
object RobotsTxt {
    data class Group(val agents: List<String>, val disallows: List<String>)

    fun allowed(robotsBody: String?, userAgent: String, path: String): Boolean {
        if (robotsBody.isNullOrBlank()) return true
        val groups = parse(robotsBody)
        if (groups.isEmpty()) return true
        val ua = userAgent.trim()
        val pathNorm = if (path.startsWith("/")) path else "/$path"
        val ranked = groups.mapNotNull { group ->
            val score = group.agents.maxOf { agent ->
                when {
                    agent == "*" -> 1
                    ua.startsWith(agent, ignoreCase = true) -> agent.length + 10
                    else -> 0
                }
            }
            if (score > 0) group to score else null
        }
        val chosen = ranked.maxByOrNull { it.second }?.first ?: return true
        return chosen.disallows.none { rule ->
            rule.isNotEmpty() && pathNorm.startsWith(rule)
        }
    }

    fun parse(body: String): List<Group> {
        val groups = ArrayList<Group>()
        val agents = ArrayList<String>()
        val disallows = ArrayList<String>()
        var inRules = false
        fun flush() {
            if (agents.isEmpty()) return
            groups += Group(agents.toList(), disallows.toList())
            agents.clear()
            disallows.clear()
            inRules = false
        }
        for (raw in body.lineSequence()) {
            val line = raw.substringBefore("#").trim()
            if (line.isEmpty()) continue
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val key = line.take(idx).trim().lowercase()
            val value = line.substring(idx + 1).trim()
            when (key) {
                "user-agent" -> {
                    if (inRules) flush()
                    agents += value
                }
                "disallow" -> {
                    inRules = true
                    disallows += value
                }
            }
        }
        flush()
        return groups
    }
}
