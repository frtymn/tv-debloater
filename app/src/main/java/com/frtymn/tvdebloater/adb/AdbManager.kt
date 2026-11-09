package com.frtymn.tvdebloater.adb

import dadb.AdbAuthException
import dadb.AdbConnectionClosedException
import dadb.AdbKeyPair
import dadb.AdbTimeoutException
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.File
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class AdbErrorKind { TIMEOUT, AUTH_TIMEOUT, REFUSED, UNREACHABLE, UNAUTHORIZED, DISCONNECTED, NOT_CONNECTED, OTHER }

class AdbError(val kind: AdbErrorKind, val detail: String, cause: Throwable? = null) : Exception(detail, cause)

data class ShellResult(val exitCode: Int, val output: String)

data class DeviceInfo(val host: String, val port: Int, val model: String, val androidVersion: String, val codename: String)

/**
 * Thin wrapper around dadb (dev.mobile:dadb). One connection at a time, one command at a time.
 * The RSA key pair is generated once and kept in app-private storage (filesDir/adb).
 */
class AdbManager(filesDir: File) {

    private val keyDir = File(filesDir, "adb")
    private val privateKey = File(keyDir, "adbkey")
    private val publicKey = File(keyDir, "adbkey.pub")
    private val mutex = Mutex()
    @Volatile private var dadb: Dadb? = null

    val isConnected: Boolean get() = dadb != null

    private fun keyPair(): AdbKeyPair {
        if (!privateKey.exists() || !publicKey.exists()) {
            keyDir.mkdirs()
            AdbKeyPair.generate(privateKey, publicKey)
        }
        return try {
            AdbKeyPair.read(privateKey, publicKey)
        } catch (e: Exception) {
            // Corrupt key: create a new one (the TV will ask to allow it again).
            privateKey.delete(); publicKey.delete()
            AdbKeyPair.generate(privateKey, publicKey)
            AdbKeyPair.read(privateKey, publicKey)
        }
    }

    /**
     * Connects and runs a first command. While the TV shows "Allow USB debugging?" the
     * handshake blocks, so the socket timeout is long enough for the user to press Allow.
     */
    suspend fun connect(host: String, port: Int): DeviceInfo = mutex.withLock {
        withContext(Dispatchers.IO) {
            closeQuietly()
            val keys = try { keyPair() } catch (e: Exception) { throw AdbError(AdbErrorKind.OTHER, "Key error: ${e.message}", e) }
            val d = Dadb.create(host, port, keys, CONNECT_TIMEOUT_MS, AUTH_SOCKET_TIMEOUT_MS)
            try {
                val r = d.shell(com.frtymn.tvdebloater.core.CommandBuilder.DEVICE_INFO)
                val lines = r.output.lines().map { it.trim() }
                dadb = d
                DeviceInfo(host, port, lines.getOrElse(0) { "" }, lines.getOrElse(1) { "" }, lines.getOrElse(2) { "" })
            } catch (e: Throwable) {
                runCatching { d.close() }
                throw map(e, duringConnect = true)
            }
        }
    }

    suspend fun shell(command: String): ShellResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val d = dadb ?: throw AdbError(AdbErrorKind.NOT_CONNECTED, "Not connected")
            try {
                val r = d.shell(command)
                ShellResult(r.exitCode, r.allOutput.trim())
            } catch (e: Throwable) {
                val err = map(e, duringConnect = false)
                if (err.kind != AdbErrorKind.OTHER) closeQuietly()
                throw err
            }
        }
    }

    suspend fun disconnect() = mutex.withLock { withContext(Dispatchers.IO) { closeQuietly() } }

    private fun closeQuietly() {
        runCatching { dadb?.close() }
        dadb = null
    }

    private fun map(e: Throwable, duringConnect: Boolean): AdbError {
        if (e is AdbError) return e
        val chain = generateSequence(e) { it.cause }.take(8).toList()
        val msg = chain.mapNotNull { it.message }.joinToString(" | ").ifBlank { e.javaClass.simpleName }
        fun has(type: Class<*>) = chain.any { type.isInstance(it) }
        val lower = msg.lowercase()
        val kind = when {
            has(AdbAuthException::class.java) -> AdbErrorKind.UNAUTHORIZED
            has(UnknownHostException::class.java) || has(NoRouteToHostException::class.java) -> AdbErrorKind.UNREACHABLE
            has(ConnectException::class.java) && (lower.contains("refused") || lower.contains("econnrefused")) -> AdbErrorKind.REFUSED
            has(ConnectException::class.java) && (lower.contains("unreachable") || lower.contains("ehostunreach") || lower.contains("enetunreach")) -> AdbErrorKind.UNREACHABLE
            has(SocketTimeoutException::class.java) || has(AdbTimeoutException::class.java) ->
                if (duringConnect && !lower.contains("failed to connect") && !lower.contains("connect timed out")) AdbErrorKind.AUTH_TIMEOUT else AdbErrorKind.TIMEOUT
            has(ConnectException::class.java) -> AdbErrorKind.TIMEOUT
            has(AdbConnectionClosedException::class.java) || has(EOFException::class.java) || has(SocketException::class.java) ->
                if (duringConnect) AdbErrorKind.UNAUTHORIZED else AdbErrorKind.DISCONNECTED
            lower.contains("unauthorized") || lower.contains("rejected") -> AdbErrorKind.UNAUTHORIZED
            else -> AdbErrorKind.OTHER
        }
        return AdbError(kind, msg, e)
    }

    companion object {
        const val DEFAULT_PORT = 5555
        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val AUTH_SOCKET_TIMEOUT_MS = 90_000
    }
}
