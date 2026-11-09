package com.frtymn.tvdebloater.db

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** db/manifest.json on the "db" branch. Describes the one packages.json next to it. */
@Serializable
data class DbManifest(
    val schemaVersion: Int = 0,
    val dbVersion: Int = 0,
    val publishedAt: String = "",
    val minAppVersion: String = "",
    val file: String = "",
    val size: Long = 0,
    val sha256: String = "",
    val counts: Map<String, Int> = emptyMap(),
) {
    companion object {
        const val MAX_BYTES = 16_384
        private val json = Json { ignoreUnknownKeys = true }
        private val FILE = Regex("^[a-z0-9][a-z0-9._-]{0,63}\\.json$")
        private val SHA = Regex("^[0-9a-f]{64}$")

        /** Parses and checks a manifest. Returns null for anything malformed. */
        fun parse(bytes: ByteArray): DbManifest? {
            if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
            val text = DbValidator.decodeUtf8(bytes) ?: return null
            val m = runCatching { json.decodeFromString(serializer(), text) }.getOrNull() ?: return null
            return m.takeIf { it.isWellFormed() }
        }
    }

    fun isWellFormed(): Boolean =
        schemaVersion >= 1 && dbVersion >= 1 && FILE.matches(file) && size in 1..DbValidator.MAX_BYTES.toLong() &&
            SHA.matches(sha256) && publishedAt.length <= 64 && minAppVersion.length <= 64 &&
            AppVersion.parse(minAppVersion.ifBlank { "0" }) != null
}

object Hashing {
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** True when [bytes] is exactly the file the manifest describes. */
    fun matches(bytes: ByteArray, manifest: DbManifest): Boolean =
        bytes.size.toLong() == manifest.size && sha256Hex(bytes).equals(manifest.sha256, ignoreCase = true)
}

/** Numeric major.minor.patch comparison. Suffixes like "-dev.12" are ignored. */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        private val RE = Regex("^v?(\\d{1,5})(?:\\.(\\d{1,5}))?(?:\\.(\\d{1,5}))?(?:[-+].*)?$")

        fun parse(s: String): AppVersion? {
            val m = RE.matchEntire(s.trim()) ?: return null
            return AppVersion(m.groupValues[1].toInt(), m.groupValues[2].toIntOrNull() ?: 0, m.groupValues[3].toIntOrNull() ?: 0)
        }
    }
}

/** Decides whether a remote database may be used by this app at all. */
object DbGate {
    enum class Verdict { OK, NOT_NEWER, NEEDS_APP_UPDATE }

    fun check(manifest: DbManifest, activeDbVersion: Int, appVersion: String): Verdict {
        if (manifest.schemaVersion > DbValidator.SUPPORTED_SCHEMA) return Verdict.NEEDS_APP_UPDATE
        val need = AppVersion.parse(manifest.minAppVersion.ifBlank { "0" })
        val have = AppVersion.parse(appVersion)
        if (need != null && have != null && need > have) return Verdict.NEEDS_APP_UPDATE
        if (need != null && have == null) return Verdict.NEEDS_APP_UPDATE
        if (manifest.dbVersion <= activeDbVersion) return Verdict.NOT_NEWER
        return Verdict.OK
    }
}
