package a3.broker

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BrokerProductTest {
    private val junk = mutableListOf<Broker>()

    @AfterTest
    fun stop() {
        junk.forEach { it.stop() }
        junk.clear()
    }

    @Test
    fun BKR_001_stranger_file_quickstart_from_fresh_shell() {
        val home = Files.createTempDirectory("a3-broker-stranger")
        val bin = Path.of("build/install/a3/bin/a3")
        assertTrue(Files.isExecutable(bin), "installDist a3 missing")
        fun run(vararg args: String): String {
            val pb = ProcessBuilder(listOf(bin.toAbsolutePath().toString()) + args)
                .directory(Path.of(".").toFile())
                .redirectErrorStream(true)
            pb.environment()["A3_BROKER_HOME"] = home.toString()
            pb.environment().remove("A3_BROKER_DEV_ROOT")
            pb.environment()["JAVA_HOME"] = System.getProperty("java.home")
            val proc = pb.start()
            val out = proc.inputStream.readBytes().toString(Charsets.UTF_8)
            assertEquals(0, proc.waitFor(), out)
            return out
        }
        val init = run("broker", "init")
        val connect = run("connect", "mcp://filesystem")
        val flag = run("flag", "irreversible:", "reserve,", "send,", "delete")
        val ran = run("run", "my-agent")
        val log = run("log")
        val all = init + connect + flag + ran + log
        assertTrue(init.contains("contacting system keystore..."), init)
        assertTrue(init.contains("broker ready"), init)
        assertTrue(connect.contains("connected"), connect)
        assertTrue(flag.contains("reserve") && flag.contains("send"), flag)
        assertTrue(ran.contains("list:"), ran)
        assertTrue(ran.contains("did not send") || ran.contains("send:"), ran)
        assertTrue(log.contains("op:") && log.contains("call:"), log)
        assertFalse(Regex("belief|admission|fold|digest|claim", RegexOption.IGNORE_CASE).containsMatchIn(all), all)
        assertTrue(all.lines().size < 80, all)
    }

    @Test
    fun BKR_002_flagged_without_consent_does_not_send_with_consent_signs_screen() {
        val b = broker()
        b.init()
        b.connect("mcp://filesystem")
        b.flag("irreversible: reserve, send, delete")
        val denied = b.run("send")
        assertTrue(denied.contains("did not send"), denied)
        assertTrue(denied.contains("no permission"), denied)
        assertTrue(b.log().contains("did not send"), b.log())
        val allowed = b.allow("send invoices", Duration.ofMinutes(5))
        assertTrue(allowed.contains("allowed"), allowed)
        val sent = b.run("send")
        assertTrue(sent.contains("send: worked"), sent)
        val hash = b.lastGrant()!!.shownAs
        assertNotNull(b.lastBoundPrint)
        assertTrue(b.lastBoundPrint!!.contains(hash), b.lastBoundPrint!!)
        assertTrue(b.log().contains("shown-as ${hash.take(12)}"), b.log())
    }

    @Test
    fun BKR_003_unflagged_tools_go_direct() {
        val b = broker()
        b.init()
        b.connect("mcp://filesystem")
        b.flag("irreversible: reserve, send, delete")
        val out = b.run("list")
        assertTrue(out.contains("list: worked"), out)
        assertFalse(out.contains("did not send"), out)
        assertFalse(b.log().contains("allowed"), b.log())
        assertTrue(b.log().contains("call: ran list"), b.log())
        assertEquals(1, b.local()!!.calls.count { it == "list" })
    }

    @Test
    fun BKR_004_scope_at_two_points_broker_and_server() {
        val hex = HEX
        val b = broker(hex = hex)
        b.init()
        b.connect("mcp://filesystem")
        b.flag("irreversible: reserve, send, delete")
        b.allow("read notes", Duration.ofMinutes(5))
        val blocked = b.run("send")
        assertTrue(blocked.contains("did not send"), blocked)
        assertTrue(blocked.contains("permission does not cover this"), blocked)
        b.run("list")
        val access = AttenuatedAccess(hex)
        val token = access.bind(
            toolScope = listOf("read"),
            until = Instant.now().plusSeconds(120),
            consentPresentationHash = "aa".repeat(32),
            restingRevision = "bb".repeat(32)
        )
        val reply = ToolsClient(Duration.ofSeconds(2))
            .call(b.local()!!.address(), "send", token.token, "forced")
        assertFalse(reply.accepted, reply.body)
        assertTrue(reply.body.contains("refused"), reply.body)
    }

    @Test
    fun BKR_005_resting_fact_expiry_blocks_new_use_inflight_unknown() {
        val b = broker(timeout = Duration.ofSeconds(3))
        b.init()
        b.connect("mcp://filesystem")
        b.flag("irreversible: send")
        b.allow("send invoices", Duration.ofMinutes(5))
        assertTrue(b.run("send").contains("worked"))
        b.expireRestingFact()
        val blocked = b.run("send")
        assertTrue(blocked.contains("did not send"), blocked)
        assertTrue(blocked.contains("the fact it rested on expired"), blocked)
        val b2 = broker(timeout = Duration.ofSeconds(3))
        b2.init()
        b2.connect("mcp://filesystem")
        b2.flag("irreversible: send")
        b2.allow("send invoices", Duration.ofMinutes(5))
        b2.run("list")
        b2.local()!!.delayMs = 800
        val future = java.util.concurrent.Executors.newSingleThreadExecutor().submit<String> { b2.run("send") }
        Thread.sleep(150)
        b2.expireRestingFact()
        val inflight = future.get()
        assertTrue(inflight.contains("unknown"), inflight)
        val second = b2.run("send")
        assertTrue(second.contains("did not send"), second)
    }

    @Test
    fun BKR_006_live_revoke_stops_later_calls_inflight_unknown() {
        val b = broker(timeout = Duration.ofSeconds(3))
        b.init()
        b.connect("mcp://filesystem")
        b.flag("irreversible: send")
        b.allow("send invoices", Duration.ofMinutes(5))
        b.run("list")
        b.local()!!.delayMs = 800
        val future = java.util.concurrent.Executors.newSingleThreadExecutor().submit<String> { b.run("send") }
        Thread.sleep(150)
        val id = b.lastGrant()!!.id
        b.revoke(id)
        val inflight = future.get()
        assertTrue(inflight.contains("unknown"), inflight)
        assertTrue(inflight.contains("permission pulled") || b.log().contains("permission pulled"), inflight + b.log())
        val second = b.run("send")
        assertTrue(second.contains("did not send"), second)
        assertTrue(b.log().contains("op: ended permission"), b.log())
    }

    @Test
    fun BKR_007_unknown_always_has_reason() {
        val b = broker(timeout = Duration.ofMillis(200))
        b.init()
        b.connect("mcp://filesystem")
        b.flag("irreversible: send")
        b.allow("send invoices", Duration.ofMinutes(5))
        b.run("list")
        b.local()!!.delayMs = 800
        val timeout = b.run("send")
        assertTrue(timeout.contains("unknown"), timeout)
        assertTrue(timeout.contains("time ran out"), timeout)
        val bMute = broker(timeout = Duration.ofMillis(200))
        bMute.init()
        bMute.connect("mcp://filesystem")
        bMute.flag("irreversible: send")
        bMute.allow("send invoices", Duration.ofMinutes(5))
        bMute.run("list")
        bMute.local()!!.stop()
        val mute = bMute.run("send")
        assertTrue(mute.contains("unknown"), mute)
        assertTrue(mute.contains("server silent"), mute)
        val log = b.log() + bMute.log()
        for (line in log.lines().filter { it.contains("result: unknown") }) {
            assertTrue(line.contains("reason:"), line)
        }
    }

    @Test
    fun BKR_008_register_language_and_prefixes() {
        val b = broker()
        b.init()
        b.connect("mcp://filesystem")
        b.flag("irreversible: send")
        b.allow("send invoices", Duration.ofMinutes(5))
        b.run("my-agent")
        b.revoke(b.lastGrant()!!.id)
        val log = b.log()
        assertEquals(emptyList(), b.registerHits(log))
        assertTrue(log.contains("op: allowed"), log)
        assertTrue(log.contains("shown-as"), log)
        assertTrue(log.contains("call: ran"), log)
        assertTrue(log.contains("op: ended permission"), log)
        assertFalse(log.lines().any { it.contains("result: unknown") && !it.contains("reason:") }, log)
    }

    @Test
    fun BKR_009_no_cleartext_credentials_keystore_or_dev_warning() {
        val home = Files.createTempDirectory("a3-broker-keys")
        val b = Broker(home, KeychainSecrets(home), { Instant.now() }, { true })
        junk += b
        b.init()
        b.connect("mcp://filesystem")
        b.flag("irreversible: send")
        b.allow("send invoices", Duration.ofMinutes(5))
        b.run("send")
        val files = b.homeFiles()
        val blob = files.joinToString("\n") { Files.readString(it) }
        assertFalse(blob.contains("-----BEGIN"), blob)
        assertFalse(Regex("\"token\"").containsMatchIn(blob), blob)
        val ready = Files.readString(home.resolve("ready"))
        assertEquals("broker\n", ready)
        val warned = AtomicReference("")
        val devHome = Files.createTempDirectory("a3-broker-dev")
        val dev = Broker(devHome, DevEnvSecrets(HEX) { warned.set(it) }, { Instant.now() }, { true })
        junk += dev
        dev.init()
        assertTrue(warned.get().contains("WARNING"), warned.get())
        assertTrue(warned.get().contains("dev"), warned.get())
        val devBlob = Files.walk(devHome).use { it.filter { p -> Files.isRegularFile(p) }.map { Files.readString(it) }.toList() }
            .joinToString("\n")
        assertFalse(devBlob.contains(HEX), devBlob)
    }

    @Test
    fun BKR_010_no_spike_import_or_path() {
        val root = repoRoot()
        val main = root.resolve("broker/src/main")
        val hits = Files.walk(main).use { walk ->
            walk.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .flatMap { Files.readAllLines(it).stream() }
                .filter { line ->
                    val lower = line.lowercase()
                    "a3-broker-spike" in lower || "spike.broker" in lower ||
                        "/desktop/a3-broker-spike" in lower
                }
                .toList()
        }
        assertEquals(emptyList(), hits)
        val gradle = Files.readString(root.resolve("broker/build.gradle.kts"))
        assertFalse(gradle.contains("spike"), gradle)
    }

    @Test
    fun BKR_011_core_frozen() {
        val root = repoRoot()
        val proc = ProcessBuilder("git", "diff", "7a60b52", "--", "core/", "core/admission/", "core/action/", "core/json/")
            .directory(root.toFile())
            .start()
        val out = proc.inputStream.readBytes().toString(Charsets.UTF_8)
        proc.waitFor()
        assertEquals("", out.trim(), out)
    }

    @Test
    fun architecture_broker_consumes_core_and_not_frozen_layers() {
        val production = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("a3.broker")
        noClasses().that().resideInAPackage("a3.broker..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "a3.a3ui..",
                "a3.prediction..",
                "a3.projection..",
                "a3.renderers..",
                "a3.intent..",
                "a3.core.runtime..",
                "a3.core.world.."
            )
            .check(production)
    }

    private fun Broker.registerHits(log: String): List<String> {
        val forbidden = listOf("belief", "admission", "fold", "digest", "claim")
        return forbidden.filter { it in log.lowercase() }
    }

    private fun broker(
        hex: String = HEX,
        timeout: Duration = Duration.ofSeconds(2),
        consent: Boolean = true
    ): Broker {
        val home = Files.createTempDirectory("a3-broker-test")
        val b = Broker(home, MemorySecrets(hex), { Instant.now() }, { consent }, timeout)
        junk += b
        return b
    }

    private fun repoRoot(): Path {
        val here = Path.of("").toAbsolutePath()
        if (Files.exists(here.resolve("settings.gradle.kts"))) return here
        return here.parent
    }

    companion object {
        private val HEX = (0 until 32).joinToString("") { "%02x".format(it) }
    }
}
