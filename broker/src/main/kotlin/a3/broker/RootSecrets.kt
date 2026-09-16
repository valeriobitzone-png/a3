// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.broker

import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class KeystoreUnavailable(message: String) : RuntimeException(message)

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
    private val timeout: Duration = Duration.ofSeconds(5),
    private val announce: () -> Unit = {},
    private val runner: (List<String>) -> String = { args -> defaultSecurity(args, timeout) }
) : RootSecrets {
    private val service = "a3-broker.root"
    private val account: String = sha256Hex(home.toAbsolutePath().toString().toByteArray()).take(16)

    override fun loadOrCreate(): String {
        announce()
        val existing = bounded { runner(listOf("find-generic-password", "-s", service, "-a", account, "-w")) }.trim()
        if (existing.isNotBlank()) return existing
        val created = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { b -> "%02x".format(b) }
        bounded {
            runner(listOf("add-generic-password", "-s", service, "-a", account, "-w", created, "-U"))
        }
        return created
    }

    private fun <T> bounded(block: () -> T): T {
        val exec = Executors.newSingleThreadExecutor()
        return try {
            exec.submit(block).get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            throw KeystoreUnavailable(keystoreTimeoutMessage(timeout))
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is KeystoreUnavailable -> throw cause
                else -> throw KeystoreUnavailable(keystoreRefusedMessage())
            }
        } finally {
            exec.shutdownNow()
        }
    }
}

internal fun keystoreTimeoutMessage(timeout: Duration): String =
    "system keystore did not respond in ${timeout.seconds}s\n" +
        "unlock the keychain, grant access, or retry with --dev"

internal fun keystoreRefusedMessage(): String =
    "system keystore refused access\n" +
        "unlock the keychain, grant access, or retry with --dev"

internal fun defaultSecurity(args: List<String>, timeout: Duration): String {
    val call = executeSecurity(args, timeout)
    val find = args.firstOrNull() == "find-generic-password"
    if (find) return if (call.exit == 0) call.out else ""
    if (call.exit != 0) throw KeystoreUnavailable(keystoreRefusedMessage())
    return call.out
}

internal data class SecurityCall(val exit: Int, val out: String)

internal fun executeSecurity(args: List<String>, timeout: Duration): SecurityCall {
    val proc = ProcessBuilder(listOf("/usr/bin/security") + args)
        .redirectErrorStream(true)
        .start()
    val buffer = ByteArrayOutputStream()
    val gobbler = Thread {
        runCatching { proc.inputStream.copyTo(buffer) }
    }
    gobbler.isDaemon = true
    gobbler.start()
    if (!proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        proc.destroyForcibly()
        gobbler.join(400)
        throw KeystoreUnavailable(keystoreTimeoutMessage(timeout))
    }
    gobbler.join(400)
    return SecurityCall(proc.exitValue(), buffer.toString(Charsets.UTF_8))
}
