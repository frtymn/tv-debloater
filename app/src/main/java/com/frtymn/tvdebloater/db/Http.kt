package com.frtymn.tvdebloater.db

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException

/** The only hosts the app ever downloads from. HTTPS only, no other ports, no credentials in URLs. */
object HttpsAllowlist {
    val HOSTS: Set<String> = setOf("raw.githubusercontent.com")

    fun isAllowed(url: String): Boolean {
        val u = runCatching { URI(url) }.getOrNull() ?: return false
        return u.scheme == "https" && u.rawUserInfo == null && (u.port == -1 || u.port == 443) &&
            u.host?.lowercase() in HOSTS
    }
}

data class HttpResponse(val code: Int, val body: ByteArray = ByteArray(0), val etag: String? = null)

class HttpBlockedException(url: String) : IOException("Blocked URL: $url")
class BodyTooLargeException : IOException("Response too large")

/** Tiny HTTP layer, so the updater can be tested without a network. */
fun interface HttpClient {
    /** Returns the response or throws IOException (offline, timeout, cut connection). */
    fun get(url: String, etag: String?, maxBytes: Int): HttpResponse
}

/** HttpURLConnection-based client. No extra libraries, no cookies, no identifying headers. */
class UrlConnectionClient(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) : HttpClient {
    override fun get(url: String, etag: String?, maxBytes: Int): HttpResponse {
        if (!HttpsAllowlist.isAllowed(url)) throw HttpBlockedException(url)
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = false
            c.connectTimeout = connectTimeoutMs
            c.readTimeout = readTimeoutMs
            c.useCaches = false
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", "tv-debloater")
            if (etag != null) c.setRequestProperty("If-None-Match", etag)
            val code = c.responseCode
            if (code != 200) return HttpResponse(code, etag = c.getHeaderField("ETag"))
            val declared = c.contentLengthLong
            if (declared > maxBytes) throw BodyTooLargeException()
            val out = ByteArrayOutputStream()
            c.inputStream.use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw BodyTooLargeException()
                }
            }
            val body = out.toByteArray()
            // A cut connection can look like a short, "successful" body.
            if (declared >= 0 && body.size.toLong() != declared) throw IOException("Incomplete download")
            return HttpResponse(code, body, c.getHeaderField("ETag"))
        } finally {
            c.disconnect()
        }
    }
}

fun IOException.isOffline(): Boolean = this is UnknownHostException || cause is UnknownHostException
fun IOException.isTimeout(): Boolean = this is SocketTimeoutException || cause is SocketTimeoutException
