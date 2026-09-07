package a3.broker

import java.nio.file.Path
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

fun interface RootSecrets {
    fun loadOrCreate(): String
}

class MemorySecrets(private val hex: String) : RootSecrets {
    override fun loadOrCreate(): String = hex
}

class DevEnvSecrets(private val hex: String, private val warn: (String) -> Unit) : RootSecrets {
    override fun loadOrCreate(): String {
        warn("WARNING: development key from the environment. Not for anything but dev.")
        return hex
    }
}

class KeychainSecrets(
    private val home: Path,
    private val runner: (List<String>) -> String = ::runSecurity
) : RootSecrets {
    private val service = "a3-broker.root"
    private val account: String = sha256Hex(home.toAbsolutePath().toString().toByteArray()).take(16)

    override fun loadOrCreate(): String {
        val existing = runCatching { runner(listOf("find-generic-password", "-s", service, "-a", account, "-w")) }
            .getOrNull()
            ?.trim()
            .orEmpty()
        if (existing.isNotBlank()) return existing
        val created = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { b -> "%02x".format(b) }
        runner(listOf("add-generic-password", "-s", service, "-a", account, "-w", created, "-U"))
        return created
    }
}

private fun runSecurity(args: List<String>): String {
    val proc = ProcessBuilder(listOf("/usr/bin/security") + args)
        .redirectErrorStream(true)
        .start()
    val out = proc.inputStream.readBytes().toString(Charsets.UTF_8)
    if (!proc.waitFor(8, TimeUnit.SECONDS)) {
        proc.destroyForcibly()
        throw IllegalStateException("keychain timed out")
    }
    if (proc.exitValue() != 0) {
        throw IllegalStateException("keychain unavailable")
    }
    return out
}
