package com.frtymn.tvdebloater.adb

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/** Finds hosts on the phone's own /24 Wi-Fi subnet that accept TCP connections on a port. */
class SubnetScanner(private val context: Context) {

    /** Returns e.g. "192.168.1.23" for the active Wi-Fi/Ethernet network, or null. */
    fun localIpv4(): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val net = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(net)
        if (caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) return null
        val lp = cm.getLinkProperties(net) ?: return null
        return lp.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }?.hostAddress
    }

    suspend fun scan(port: Int, onProgress: (done: Int, total: Int) -> Unit): List<String> = withContext(Dispatchers.IO) {
        val ip = localIpv4() ?: return@withContext emptyList()
        val base = ip.substringBeforeLast('.')
        val own = ip.substringAfterLast('.').toIntOrNull()
        val targets = (1..254).filter { it != own }.map { "$base.$it" }
        val done = AtomicInteger(0)
        val sem = Semaphore(48)
        coroutineScope {
            targets.map { host ->
                async {
                    sem.withPermit {
                        val open = try {
                            Socket().use { s -> s.connect(InetSocketAddress(host, port), 350); true }
                        } catch (_: Exception) { false }
                        onProgress(done.incrementAndGet(), targets.size)
                        if (open) host else null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }
}
