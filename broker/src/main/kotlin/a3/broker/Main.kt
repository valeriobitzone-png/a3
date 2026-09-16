// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.broker

import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val code = Cli().dispatch(args.toList())
    exitProcess(code)
}

class Cli(
    private val env: (String) -> String? = { System.getenv(it) },
    private val stdout: (String) -> Unit = {
        println(it)
        System.out.flush()
    },
    private val stderr: (String) -> Unit = {
        System.err.println(it)
        System.err.flush()
    },
    private val stdinYes: () -> Boolean = {
        System.console()?.let { it.readLine()?.trim()?.lowercase() == "y" } ?: false
    },
    private val secretsOverride: RootSecrets? = null
) {
    fun dispatch(args: List<String>): Int {
        if (args.isEmpty() || args[0] == "help" || args[0] == "--help") {
            stdout(QUICKSTART)
            return 0
        }
        val parsed = parseFlags(args, env)
        val home = Path.of(env("A3_BROKER_HOME") ?: (System.getProperty("user.home") + "/.a3-broker"))
        return try {
            val secrets = secretsOverride ?: secretsFor(home, parsed)
            val broker = Broker(
                home = home,
                secrets = secrets,
                clock = { Instant.now() },
                askConsent = { screen ->
                    stdout(screen)
                    if (parsed.yes) true else stdinYes()
                }
            )
            try {
                val text = command(broker, parsed.argv)
                if (text.isNotBlank()) stdout(text)
                0
            } finally {
                broker.stop()
            }
        } catch (e: KeystoreUnavailable) {
            stdout(e.message ?: "system keystore unavailable")
            1
        } catch (e: Exception) {
            stdout(e.message ?: "failed")
            1
        }
    }

    private fun secretsFor(home: Path, parsed: CliFlags): RootSecrets {
        if (parsed.dev) {
            val hex = env("A3_BROKER_DEV_ROOT").orEmpty()
            require(hex.isNotBlank()) {
                "--dev needs A3_BROKER_DEV_ROOT set"
            }
            return DevEnvSecrets(hex) { stdout(it) }
        }
        return KeychainSecrets(
            home = home,
            timeout = parsed.keystoreTimeout,
            announce = { stdout("contacting system keystore...") }
        )
    }

    private fun command(broker: Broker, argv: List<String>): String {
        return when {
            argv.size >= 2 && argv[0] == "broker" && argv[1] == "init" -> broker.init()
            argv[0] == "connect" -> broker.connect(argv.getOrElse(1) { "mcp://filesystem" })
            argv[0] == "flag" -> broker.flag(argv.drop(1).joinToString(" "))
            argv[0] == "allow" -> {
                val intent = argv.getOrNull(1)?.trim('"') ?: ""
                val ttlIdx = argv.indexOf("--for")
                val ttl = parseTtl(argv.getOrElse(ttlIdx + 1) { "5m" })
                broker.allow(intent, ttl)
            }
            argv[0] == "run" -> broker.run(argv.drop(1).joinToString(" "))
            argv[0] == "log" -> broker.log().ifBlank { "(empty)" }
            argv[0] == "revoke" -> broker.revoke(argv.getOrElse(1) { "" })
            else -> QUICKSTART
        }
    }
}

internal data class CliFlags(
    val argv: List<String>,
    val yes: Boolean,
    val dev: Boolean,
    val keystoreTimeout: Duration
)

internal fun parseFlags(args: List<String>, env: (String) -> String?): CliFlags {
    var yes = false
    var dev = false
    var timeout = env("A3_BROKER_KEYSTORE_TIMEOUT")?.toLongOrNull()
        ?.let { Duration.ofSeconds(it) }
        ?: Duration.ofSeconds(5)
    val argv = ArrayList<String>()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--yes" -> {
                yes = true
                i++
            }
            "--dev" -> {
                dev = true
                i++
            }
            "--keystore-timeout" -> {
                timeout = parseTtl(args.getOrElse(i + 1) { "5s" })
                i += 2
            }
            else -> {
                argv += args[i]
                i++
            }
        }
    }
    return CliFlags(argv, yes, dev, timeout)
}

internal val QUICKSTART = """
a3 broker init
a3 connect mcp://filesystem
a3 flag irreversible: reserve, send, delete
a3 run my-agent
a3 log
""".trimIndent()
