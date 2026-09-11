package a3.agent

import a3.a3ui.engine.Epistemic
import a3.a3ui.model.EpistemicAction
import a3.a3ui.model.EpistemicFreshness
import a3.a3ui.model.EpistemicStatus
import a3.a3ui.model.EpistemicSupport
import a3.a3ui.model.Node
import a3.agent.gate.UrlOpener
import a3.agent.model.ActivityKind
import a3.agent.model.AgentOutcome
import a3.agent.model.ChoiceKind
import a3.agent.model.PreviewLevel
import a3.agent.model.RetailerSpec
import a3.agent.model.SourceKind
import a3.agent.plan.PlanDecomposer
import a3.agent.preview.OpenGraphPreviewFetcher
import a3.agent.sources.AdoptedApis
import a3.agent.sources.OpenLibraryClient
import a3.agent.surface.AgentSurfaces
import a3.agent.surface.SurfaceShot
import a3.broker.Broker
import a3.broker.MemorySecrets
import a3.core.action.ActionPhase
import a3.core.action.PlanPhase
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentSurfaceTest {
    private val t0 = Instant.parse("2026-09-11T08:00:00Z")
    private val clock = AtomicReference(t0)
    private val brokers = ArrayList<Broker>()
    private val sites = ArrayList<PreviewFixtureServer>()
    private val opened = ArrayList<String>()
    private val consent = AtomicBoolean(false)

    @BeforeTest
    fun reset() {
        clock.set(t0)
        consent.set(false)
        opened.clear()
    }

    @AfterTest
    fun tearDown() {
        brokers.forEach { it.stop() }
        brokers.clear()
        sites.forEach { it.stop() }
        sites.clear()
        opened.clear()
    }

    @Test
    fun AG_001_intent_A_three_groups_two_to_three_options() {
        val runtime = travelRuntime()
        val session = runtime.propose(INTENT_A)
        assertEquals(
            listOf(ActivityKind.TRANSPORT, ActivityKind.HOTEL, ActivityKind.CALENDAR),
            session.groups.map { it.kind }
        )
        for (group in session.groups) {
            assertTrue(group.options.size in 2..3, "${group.kind} ${group.options.size}")
        }
        writeAsset("scenario-a.png", SurfaceShot.session(session, "scenario A · Milano 09:00"))
        File(assets(), "scenario-a.html").writeText(SurfaceShot.html(session))
    }

    @Test
    fun AG_002_every_option_has_axis_uncertain_held_or_unknown() {
        val runtime = travelRuntime()
        val session = runtime.propose(INTENT_A)
        val asOf = clock.get()
        for (opt in session.groups.flatMap { it.options }) {
            assertNotNull(opt.axis)
            val facts = opt.facts.values.firstOrNull()
            if (facts != null && Epistemic.requiresExposure(facts, asOf)) {
                val exposed = flatten(opt.tree.nodes).mapNotNull { it.axis }
                assertTrue(exposed.isNotEmpty(), "uncertain ${opt.id} missing axis on nodes")
                assertTrue(exposed.any { !it.isDefault() }, opt.id)
            }
        }
        val held = session.groups.first { it.kind == ActivityKind.HOTEL }.options
            .first { it.admissionHeld }
        assertEquals(EpistemicStatus.HELD, held.axis.status)
        assertTrue(flatten(held.tree.nodes).any { it.axis?.status == EpistemicStatus.HELD }, "HELD not rendered")
        val unknown = session.groups.first { it.kind == ActivityKind.HOTEL }.options
            .first { it.id.contains("unlisted") }
        assertEquals(EpistemicSupport.UNKNOWN, unknown.axis.support)
        assertTrue(
            flatten(unknown.tree.nodes).any { it.axis?.support == EpistemicSupport.UNKNOWN },
            "UNKNOWN not rendered"
        )
    }

    @Test
    fun AG_003_intent_B_linksurface_outbound_gate_default_on() {
        val site = site(ALLOW_ROBOTS)
        val runtime = purchaseRuntime(site.origin)
        val session = runtime.propose(INTENT_B)
        val purchase = session.groups.single { it.kind == ActivityKind.PURCHASE }
        assertTrue(purchase.options.any { it.retailer == "amazon" })
        assertTrue(purchase.options.any { it.outbound })
        assertTrue(purchase.options.any { it.previewLevel != null })
        writeAsset("scenario-b.png", SurfaceShot.session(session, "scenario B · cuffie < 100€"))
        File(assets(), "scenario-b.html").writeText(SurfaceShot.html(session))

        consent.set(false)
        val amazon = purchase.options.first { it.retailer == "amazon" }
        val denied = runtime.choose(session, amazon.id, ChoiceKind.OUTBOUND)
        assertTrue(denied.gated)
        assertFalse(denied.opened)
        assertTrue(opened.isEmpty(), "opened without consent: $opened")
        assertNotNull(denied.brokerLine)
        assertTrue(
            denied.brokerLine!!.contains("did not allow") || denied.brokerLine.contains("did not send"),
            denied.brokerLine
        )
        assertTrue(
            (denied.consentText ?: denied.brokerLine).contains("sto per aprire"),
            "${denied.consentText} ${denied.brokerLine}"
        )
        writeAsset(
            "gate-outbound.png",
            SurfaceShot.gate(denied.consentText ?: denied.brokerLine!!, opened = false)
        )

        val noAllow = runtime.gate.openWithoutAllow()
        assertFalse(noAllow.allowed)
        assertTrue(noAllow.brokerLine!!.contains("did not send"), noAllow.brokerLine)
        assertTrue(opened.isEmpty())

        consent.set(true)
        val allowed = runtime.choose(session, amazon.id, ChoiceKind.OUTBOUND)
        assertTrue(allowed.opened, allowed.brokerLine ?: "")
        assertEquals(listOf(amazon.url), opened)
    }

    @Test
    fun AG_004_opengraph_fixture_rich_preview() {
        val site = site(ALLOW_ROBOTS)
        val fetcher = fetcher()
        val preview = fetcher.fetch("${site.origin}/og", "shop", "cuffie")
        assertEquals(PreviewLevel.OPENGRAPH, preview.level)
        assertEquals("Sony WH-1000XM5", preview.title)
        assertEquals("Cuffie over-ear wireless", preview.description)
        assertTrue(preview.imageUrl!!.endsWith("/cover.png"))
        assertEquals("89.00", preview.price)
        val option = AgentSurfaces.optionFromPreview(preview, clock.get())
        assertEquals(PreviewLevel.OPENGRAPH, option.previewLevel)
        assertNotNull(option.imageUrl)
        assertEquals("89.00", option.price)
        writeAsset("preview-opengraph.png", SurfaceShot.option(option, "preview ricco · opengraph"))
        File(assets(), "preview-opengraph.html").writeText(
            "<p>level=${option.previewLevel?.wire()} title=${option.title} price=${option.price} image=${option.imageUrl}</p>"
        )
    }

    @Test
    fun AG_005_fallback_twitter_meta_minima_no_crash() {
        val site = site(ALLOW_ROBOTS)
        val fetcher = fetcher()
        val twitter = fetcher.fetch("${site.origin}/twitter", "shop", "cuffie")
        assertEquals(PreviewLevel.TWITTER, twitter.level)
        val meta = fetcher.fetch("${site.origin}/meta", "shop", "cuffie")
        assertEquals(PreviewLevel.META, meta.level)
        val bare = fetcher.fetch("${site.origin}/bare", "shop", "cuffie")
        assertEquals(PreviewLevel.MINIMA, bare.level)
        assertEquals("shop · cuffie", bare.title)
        val option = AgentSurfaces.optionFromPreview(twitter, clock.get())
        writeAsset("preview-fallback.png", SurfaceShot.option(option, "fallback · ${twitter.level.wire()}"))
        File(assets(), "preview-fallback.html").writeText(
            "<ul><li>twitter=${twitter.level.wire()}</li><li>meta=${meta.level.wire()}</li><li>bare=${bare.level.wire()}</li></ul>"
        )
    }

    @Test
    fun AG_006_robots_timeout_antibot_fallback_never_forced() {
        val deny = site("User-agent: *\nDisallow: /\n")
        val allow = site(ALLOW_ROBOTS, slowMs = 3_000)
        val fetcher = fetcher()
        val blocked = fetcher.fetch("${deny.origin}/product", "shop", "cuffie")
        assertEquals(PreviewLevel.MINIMA, blocked.level)
        assertEquals("robots-deny", blocked.fallbackReason)
        assertFalse(blocked.robotsAllowed)
        assertFalse(blocked.pageFetched)
        assertFalse(deny.paths.contains("/product"), "forced fetch after robots deny: ${deny.paths}")
        assertTrue(deny.paths.contains("/robots.txt"))

        val slow = OpenGraphPreviewFetcher(
            clock = { clock.get() },
            timeout = Duration.ofMillis(250),
            previewTtl = Duration.ofMinutes(15)
        )
        val timed = slow.fetch("${allow.origin}/slow", "shop", "cuffie")
        assertEquals(PreviewLevel.MINIMA, timed.level)
        assertEquals("timeout", timed.fallbackReason)
        assertFalse(timed.pageFetched)

        val amazon = fetcher.fetch("${allow.origin}/amazon", "amazon", "cuffie", amazonUnreliable = true)
        assertEquals(PreviewLevel.MINIMA, amazon.level)
        assertTrue(amazon.amazonUnreliable)
        assertEquals("anti-bot", amazon.fallbackReason)
        assertEquals(1, fetcher.pageGets["${allow.origin}/amazon"])
    }

    @Test
    fun AG_007_stale_ttl_and_unattributed_price_unknown() {
        val asOf = t0.plusSeconds(8_000)
        val stale = AgentSurfaces.unattributedPrice(asOf, stale = true)
        assertEquals(EpistemicFreshness.STALE, stale.axis.freshness)
        assertEquals(EpistemicSupport.UNKNOWN, stale.axis.support)
        assertTrue(flatten(stale.tree.nodes).any { it.axis?.freshness == EpistemicFreshness.STALE })
        assertTrue(flatten(stale.tree.nodes).any { it.axis?.support == EpistemicSupport.UNKNOWN })
        val site = site(ALLOW_ROBOTS)
        val fetcher = OpenGraphPreviewFetcher(
            clock = { clock.get() },
            timeout = Duration.ofSeconds(3),
            previewTtl = Duration.ofSeconds(60)
        )
        val preview = fetcher.fetch("${site.origin}/og", "shop", "cuffie")
        clock.set(t0.plusSeconds(120))
        val aged = AgentSurfaces.optionFromPreview(preview, clock.get())
        assertEquals(EpistemicFreshness.STALE, aged.axis.freshness)
        writeAsset("preview-stale.png", SurfaceShot.option(aged, "preview epistemico · STALE"))
    }

    @Test
    fun AG_008_open_library_real_api() {
        val now = Instant.now()
        val client = OpenLibraryClient({ now })
        val hits = client.search("the lord of the rings", limit = 5)
        val doc = hits.docs.first { !it.coverUrl.isNullOrBlank() }
        assertTrue(doc.title.isNotBlank(), doc.title)
        assertTrue(doc.coverUrl!!.contains("covers.openlibrary.org"), doc.coverUrl)
        val option = AgentSurfaces.optionFromLibrary(
            title = doc.title,
            coverUrl = doc.coverUrl,
            apiTimestamp = hits.apiTimestamp,
            asOf = hits.apiTimestamp,
            index = 0
        )
        assertEquals(SourceKind.PUBLIC_API, option.sourceKind)
        assertEquals(EpistemicSupport.HIGH, option.axis.support)
        assertEquals(EpistemicFreshness.FRESH, option.axis.freshness)
        assertEquals(hits.apiTimestamp, option.claims.first().observedAt)
        writeAsset("openlibrary.png", SurfaceShot.option(option, "Open Library API"))
        File(assets(), "openlibrary.html").writeText(
            "<p>${doc.title}</p><p>${doc.coverUrl}</p><p>apiTimestamp=${hits.apiTimestamp}</p>"
        )
        assertTrue(AdoptedApis.names.contains("Open Library"))
        assertTrue(AdoptedApis.ebaySearch("cuffie").contains("ebay.com"))
        assertTrue(AdoptedApis.openFoodFactsSearch("pasta").contains("openfoodfacts"))
        assertTrue(AdoptedApis.musicBrainzRecording("headphones").contains("musicbrainz"))
    }

    @Test
    fun AG_009_reversible_detail_direct_no_gate() {
        val runtime = travelRuntime()
        val session = runtime.propose(INTENT_A)
        val rail = session.groups.first { it.kind == ActivityKind.TRANSPORT }.options.first()
        assertTrue(rail.reversible)
        val choice = runtime.choose(session, rail.id, ChoiceKind.DETAIL)
        assertFalse(choice.gated)
        assertFalse(choice.opened)
        assertNotNull(choice.brokerLine)
        assertTrue(choice.brokerLine!!.contains("read: worked"), choice.brokerLine)
        assertFalse(choice.brokerLine.contains("did not send"))
        assertEquals(AgentOutcome.WORKED, choice.outcome)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun AG_010_action_state_rendered_with_epistemic_verb() {
        val runtime = travelRuntime()
        val session = runtime.propose(INTENT_A)
        val rail = session.groups.first { it.kind == ActivityKind.TRANSPORT }.options.first()
        val worked = runtime.choose(session, rail.id, ChoiceKind.DETAIL)
        assertEquals(ActionPhase.COMPLETED, worked.action.actionPhase)
        assertEquals(AgentOutcome.WORKED, worked.outcome)
        assertTrue(flatten(worked.tree.nodes).any { it.axis?.action == EpistemicAction.DONE })

        consent.set(false)
        val shop = purchaseRuntime(site(ALLOW_ROBOTS).origin)
        val buy = shop.propose(INTENT_B)
        val amazon = buy.groups.single().options.first { it.retailer == "amazon" }
        val denied = shop.choose(buy, amazon.id, ChoiceKind.OUTBOUND)
        assertEquals(PlanPhase.DENIED, denied.action.planPhase)
        assertEquals(AgentOutcome.DID_NOT_WORK, denied.outcome)
        assertTrue(flatten(denied.tree.nodes).any { it.axis?.action == EpistemicAction.UNKNOWN })

        val unknown = runtime.unknownTimeout(session)
        assertEquals(ActionPhase.UNKNOWN, unknown.action.actionPhase)
        assertEquals(AgentOutcome.UNKNOWN, unknown.outcome)
        assertTrue(flatten(unknown.tree.nodes).any { it.axis?.action == EpistemicAction.UNKNOWN })
        writeAsset("action-outcome.png", SurfaceShot.outcome(worked))
    }

    @Test
    fun AG_011_plan_decomposition_deterministic() {
        val a = PlanDecomposer.decompose(INTENT_A)
        val b = PlanDecomposer.decompose(INTENT_A)
        assertEquals(a, b)
        assertEquals(
            listOf(ActivityKind.TRANSPORT, ActivityKind.HOTEL, ActivityKind.CALENDAR),
            a.activities
        )
        assertEquals("Roma", a.fromCity)
        assertEquals("Milano", a.toCity)
        assertEquals("9:00", a.time)
        val buy = PlanDecomposer.decompose(INTENT_B)
        assertEquals(listOf(ActivityKind.PURCHASE), buy.activities)
        assertEquals("cuffie", buy.query)
        assertEquals(100, buy.budgetEur)
        assertEquals(buy, PlanDecomposer.decompose(INTENT_B))
        val src = File(repoRoot(), "agent/src/main/kotlin/a3/agent/plan/PlanDecomposer.kt").readText()
        assertFalse(src.contains("openai", ignoreCase = true))
        assertFalse(src.contains("anthropic", ignoreCase = true))
        assertFalse(src.contains("chat.completions", ignoreCase = true))
        assertFalse(Regex("openai|anthropic|chat\\.completions").containsMatchIn(src))
    }

    @Test
    fun AG_012_freeze_only_agent_module() {
        fun diff(vararg paths: String): String {
            val proc = ProcessBuilder("git", "diff", "--stat", "--", *paths)
                .directory(repoRoot())
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            assertEquals(0, proc.waitFor())
            return out
        }
        val frozen = diff("core/", "a3ui/", "renderers/", "broker/", "launcher/", "adapters/")
        assertTrue(frozen.isBlank(), frozen)
        val settings = File(repoRoot(), "settings.gradle.kts").readText()
        assertTrue(settings.contains(":agent"))
        assertTrue(File(repoRoot(), "agent/src/main/kotlin/a3/agent/AgentRuntime.kt").exists())
    }

    private fun travelRuntime(): AgentRuntime {
        val broker = broker()
        return AgentRuntime(
            clock = { clock.get() },
            broker = broker,
            preview = fetcher(),
            retailers = emptyList(),
            opener = UrlOpener { url -> opened += url; true }
        )
    }

    private fun purchaseRuntime(origin: String): AgentRuntime {
        val broker = broker()
        return AgentRuntime(
            clock = { clock.get() },
            broker = broker,
            preview = fetcher(),
            retailers = listOf(
                RetailerSpec("amazon", "Amazon", "$origin/amazon", amazonUnreliable = true),
                RetailerSpec("ebay", "eBay", "$origin/og"),
                RetailerSpec("shop", "Shop", "$origin/twitter")
            ),
            opener = UrlOpener { url -> opened += url; true }
        )
    }

    private fun broker(): Broker {
        val home = Files.createTempDirectory("a3-agent-broker")
        val b = Broker(
            home,
            MemorySecrets(HEX),
            { clock.get() },
            { consent.get() }
        )
        brokers += b
        return b
    }

    private fun fetcher() = OpenGraphPreviewFetcher(
        clock = { clock.get() },
        timeout = Duration.ofSeconds(3),
        previewTtl = Duration.ofMinutes(15)
    )

    private fun site(robots: String, slowMs: Long = 0): PreviewFixtureServer {
        val s = PreviewFixtureServer(robots, slowMs)
        s.start()
        sites += s
        return s
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

    private fun writeAsset(name: String, image: java.awt.image.BufferedImage) {
        SurfaceShot.write(image, File(assets(), name))
    }

    private fun assets(): File {
        val dir = File(repoRoot(), "review-assets/agent")
        dir.mkdirs()
        return dir
    }

    private fun repoRoot(): File {
        var here = File("").absoluteFile
        while (!File(here, "settings.gradle.kts").exists()) {
            here = here.parentFile ?: error("repo root")
        }
        return here
    }

    companion object {
        private const val INTENT_A = "Appuntamento a Milano alle 9:00 da Roma"
        private const val INTENT_B = "Compra cuffie sotto 100€"
        private const val ALLOW_ROBOTS = "User-agent: *\nDisallow:\n"
        private val HEX = (0 until 32).joinToString("") { "%02x".format(it) }
    }
}
