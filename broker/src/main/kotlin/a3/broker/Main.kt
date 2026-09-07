package a3.broker

import java.nio.file.Path
import java.time.Instant
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val code = Cli().dispatch(args.toList())
    exitProcess(code)
}

class Cli(
    private val env: (String) -> String? = { System.getenv(it) },
    private val stdout: (String) -> Unit = { println(it) },
    private val stderr: (String) -> Unit = { System.err.println(it) },
    private val stdinYes: () -> Boolean = {
        System.console()?.let { it.readLine()?.trim()?.lowercase() == "y" } ?: false
    }
) {
    fun dispatch(args: List<String>): Int {
        if (args.isEmpty() || args[0] == "help" || args[0] == "--help") {
            stdout(QUICKSTART)
            return 0
        }
        val yes = "--yes" in args
        val argv = args.filter { it != "--yes" }
        val home = Path.of(env("A3_BROKER_HOME") ?: (System.getProperty("user.home") + "/.a3-broker"))
        val secrets = secretsFor(home)
        val broker = Broker(
            home = home,
            secrets = secrets,
            clock = { Instant.now() },
            askConsent = { screen ->
                stdout(screen)
                if (yes) true else stdinYes()
            }
        )
        return try {
            val text = command(broker, argv)
            if (text.isNotBlank()) stdout(text)
            0
        } catch (e: Exception) {
            stderr(e.message ?: "failed")
            1
        } finally {
            broker.stop()
        }
    }

    private fun secretsFor(home: Path): RootSecrets {
        val dev = env("A3_BROKER_DEV_ROOT")
        return if (!dev.isNullOrBlank()) {
            DevEnvSecrets(dev) { stderr(it) }
        } else {
            KeychainSecrets(home)
        }
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

internal val QUICKSTART = """
a3 broker init
a3 connect mcp://filesystem
a3 flag irreversible: reserve, send, delete
a3 run my-agent
a3 log
""".trimIndent()
