// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.broker

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrokerKeystoreTest {
    private val hex = (0 until 32).joinToString("") { "%02x".format(it) }

    @Test
    fun BKR_013_init_bounded_when_keystore_blocked() {
        val home = Files.createTempDirectory("a3-broker-blocked")
        val lines = mutableListOf<Pair<Long, String>>()
        val start = System.nanoTime()
        fun emit(s: String) {
            lines += ((System.nanoTime() - start) / 1_000_000) to s
        }
        val hung = KeychainSecrets(
            home = home,
            timeout = Duration.ofSeconds(1),
            announce = { emit("contacting system keystore...") },
            runner = {
                Thread.sleep(30_000)
                ""
            }
        )
        val t0 = System.currentTimeMillis()
        val code = Cli(
            env = { if (it == "A3_BROKER_HOME") home.toString() else null },
            stdout = { emit(it) },
            secretsOverride = hung
        ).dispatch(listOf("broker", "init"))
        val elapsed = System.currentTimeMillis() - t0
        assertEquals(1, code)
        assertTrue(lines.isNotEmpty(), lines.toString())
        assertTrue(lines.first().second.contains("contacting system keystore"), lines.toString())
        // Prompt vs 1s keystore budget — not a 200ms wall clock (flakes under full-suite load).
        assertTrue(lines.first().first < 500, "progress too late: ${lines.first()}")
        assertTrue(lines.any { it.second.contains("did not respond") }, lines.toString())
        assertTrue(lines.any { it.second.contains("--dev") }, lines.toString())
        assertTrue(elapsed < 2500, "hung ${elapsed}ms")
        assertFalse(lines.any { it.second.contains(hex) }, lines.toString())
    }

    @Test
    fun BKR_014_no_silent_dev_fallback() {
        val home = Files.createTempDirectory("a3-broker-no-fallback")
        val hung = KeychainSecrets(
            home = home,
            timeout = Duration.ofSeconds(1),
            announce = {},
            runner = {
                Thread.sleep(30_000)
                ""
            }
        )
        val out = StringBuilder()
        val code = Cli(
            env = { k ->
                when (k) {
                    "A3_BROKER_HOME" -> home.toString()
                    "A3_BROKER_DEV_ROOT" -> hex
                    else -> null
                }
            },
            stdout = { out.append(it).append('\n') },
            secretsOverride = hung
        ).dispatch(listOf("broker", "init"))
        assertEquals(1, code, out.toString())
        assertFalse(out.contains("WARNING"), out.toString())
        assertFalse(out.contains("development key"), out.toString())
        assertTrue(out.contains("did not respond") || out.contains("keystore"), out.toString())
        val blob = if (Files.exists(home)) {
            Files.walk(home).use { walk ->
                walk.filter { Files.isRegularFile(it) }.map { Files.readString(it) }.toList()
            }.joinToString("\n")
        } else ""
        assertFalse(blob.contains(hex), blob)
        val parsed = parseFlags(listOf("broker", "init"), { if (it == "A3_BROKER_DEV_ROOT") hex else null })
        assertFalse(parsed.dev)

        val liveHome = Files.createTempDirectory("a3-broker-env-ignored")
        val liveOut = StringBuilder()
        val live = Cli(
            env = { k ->
                when (k) {
                    "A3_BROKER_HOME" -> liveHome.toString()
                    "A3_BROKER_DEV_ROOT" -> hex
                    else -> null
                }
            },
            stdout = { liveOut.append(it).append('\n') }
        ).dispatch(listOf("broker", "init"))
        assertEquals(0, live, liveOut.toString())
        assertTrue(liveOut.contains("contacting system keystore"), liveOut.toString())
        assertTrue(liveOut.contains("broker ready"), liveOut.toString())
        assertFalse(liveOut.contains("WARNING"), liveOut.toString())
        val liveBlob = Files.walk(liveHome).use { walk ->
            walk.filter { Files.isRegularFile(it) }.map { Files.readString(it) }.toList()
        }.joinToString("\n")
        assertFalse(liveBlob.contains(hex), liveBlob)
    }

    @Test
    fun BKR_015_ops_bounded_when_keystore_blocked() {
        val home = Files.createTempDirectory("a3-broker-ops")
        val hung = KeychainSecrets(
            home = home,
            timeout = Duration.ofSeconds(1),
            announce = {},
            runner = {
                Thread.sleep(30_000)
                ""
            }
        )
        fun timed(block: () -> Unit) {
            val t0 = System.currentTimeMillis()
            assertFails { block() }
            val elapsed = System.currentTimeMillis() - t0
            assertTrue(elapsed < 2500, "hung ${elapsed}ms")
        }
        timed {
            val b = Broker(home, hung, { Instant.now() }, { true })
            b.connect("mcp://filesystem")
        }
        timed {
            val b = Broker(home, hung, { Instant.now() }, { true })
            b.allow("send invoices", Duration.ofMinutes(5))
        }
        timed {
            val b = Broker(home, hung, { Instant.now() }, { true })
            b.run("send")
        }
        timed {
            val b = Broker(home, hung, { Instant.now() }, { true })
            b.revoke("g1")
        }
    }

    @Test
    fun BKR_016_regression_dev_stranger_file_and_freeze() {
        val home = Files.createTempDirectory("a3-broker-dev-stranger")
        val bin = Path.of("build/install/a3/bin/a3")
        assertTrue(Files.isExecutable(bin), "installDist a3 missing")
        fun run(vararg args: String): String {
            val pb = ProcessBuilder(listOf(bin.toAbsolutePath().toString()) + args)
                .directory(Path.of(".").toFile())
                .redirectErrorStream(true)
            pb.environment()["A3_BROKER_HOME"] = home.toString()
            pb.environment()["A3_BROKER_DEV_ROOT"] = hex
            pb.environment()["JAVA_HOME"] = System.getProperty("java.home")
            val proc = pb.start()
            val out = proc.inputStream.readBytes().toString(Charsets.UTF_8)
            assertEquals(0, proc.waitFor(), out)
            return out
        }
        val init = run("broker", "init", "--dev")
        val connect = run("connect", "mcp://filesystem", "--dev")
        val flag = run("flag", "irreversible:", "reserve,", "send,", "delete", "--dev")
        val ran = run("run", "my-agent", "--dev")
        val log = run("log", "--dev")
        val all = init + connect + flag + ran + log
        assertTrue(init.contains("WARNING"), init)
        assertTrue(init.contains("broker ready"), init)
        assertFalse(init.contains("contacting system keystore"), init)
        assertTrue(connect.contains("connected"), connect)
        assertTrue(ran.contains("list:"), ran)
        assertTrue(ran.contains("did not send") || ran.contains("send:"), ran)
        assertTrue(log.contains("op:") && log.contains("call:"), log)
        assertFalse(Regex("belief|admission|fold|digest|claim", RegexOption.IGNORE_CASE).containsMatchIn(all), all)
        assertTrue(QUICKSTART.lines().size <= 20, QUICKSTART)
        assertTrue(all.lines().size < 80, all)
        val blob = Files.walk(home).use { walk ->
            walk.filter { Files.isRegularFile(it) }.map { Files.readString(it) }.toList()
        }.joinToString("\n")
        assertFalse(blob.contains(hex), blob)

        val root = Path.of("").toAbsolutePath().let { if (Files.exists(it.resolve("settings.gradle.kts"))) it else it.parent }
        // Same retirement of 7a60b52 pin as BKR_011 — CORE evolved after broker-v0.1.
        val proc = ProcessBuilder(
            "git", "diff", "--stat", "--",
            "core/", "prediction/", "projection/", "a3ui/", "adapters/", "intent-model/", "renderers/", "launcher/"
        ).directory(root.toFile()).start()
        val diff = proc.inputStream.readBytes().toString(Charsets.UTF_8)
        proc.waitFor()
        assertEquals("", diff.trim(), diff)
    }
}
