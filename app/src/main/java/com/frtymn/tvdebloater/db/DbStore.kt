package com.frtymn.tvdebloater.db

import com.frtymn.tvdebloater.core.PackageDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class DbSource { BUNDLED, DOWNLOADED }

data class ActiveDb(val db: PackageDatabase, val source: DbSource)

@Serializable
data class DbMeta(
    val etag: String? = null,
    val lastCheckedAt: Long = 0,
    /** True when the last check found a database that needs a newer app. */
    val needsAppUpdate: Boolean = false,
    /** Debug/log details of the last check. Never shown as a user message. */
    val lastResult: String = "",
    val lastRejectedEntries: Int = 0,
)

/**
 * Downloaded databases on disk: `current.json` and `previous.json` (the last two good versions).
 * The bundled asset is the permanent fallback and is never written. Every write goes to a temp
 * file first and is then renamed, so a crash or a cut download never leaves a half-written file.
 */
class DbStore(private val dir: File) {
    private val current get() = File(dir, "current.json")
    private val previous get() = File(dir, "previous.json")
    private val metaFile get() = File(dir, "meta.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The newest valid database: downloaded current, then previous, unless the bundled one is newer. */
    @Synchronized
    fun loadActive(bundled: PackageDatabase): ActiveDb {
        for (f in listOf(current, previous)) {
            val bytes = runCatching { if (f.isFile) f.readBytes() else null }.getOrNull() ?: continue
            val r = DbValidator.validate(bytes) as? DbValidator.Result.Ok ?: continue
            if (r.db.dbVersion > bundled.dbVersion) return ActiveDb(r.db, DbSource.DOWNLOADED)
            break // an older download than the bundled list is never used
        }
        return ActiveDb(bundled, DbSource.BUNDLED)
    }

    /** Installs an already verified and validated file. current becomes previous. */
    @Synchronized
    fun install(bytes: ByteArray) {
        dir.mkdirs()
        val tmp = writeTemp("incoming", bytes)
        if (current.isFile) move(current, previous)
        move(tmp, current)
    }

    /** Removes downloaded databases. The bundled list becomes active again. */
    @Synchronized
    fun reset(now: Long) {
        current.delete(); previous.delete()
        dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
        writeMeta(DbMeta(etag = null, lastCheckedAt = now, lastResult = "reset"))
    }

    @Synchronized
    fun hasDownloads(): Boolean = current.isFile || previous.isFile

    @Synchronized
    fun readMeta(): DbMeta = runCatching { json.decodeFromString(DbMeta.serializer(), metaFile.readText()) }.getOrDefault(DbMeta())

    @Synchronized
    fun writeMeta(meta: DbMeta) {
        dir.mkdirs()
        move(writeTemp("meta", json.encodeToString(DbMeta.serializer(), meta).toByteArray()), metaFile)
    }

    private fun writeTemp(prefix: String, bytes: ByteArray): File {
        val tmp = File(dir, "$prefix-${System.nanoTime()}.tmp")
        try {
            FileOutputStream(tmp).use { out -> out.write(bytes); out.fd.sync() }
        } catch (e: Exception) {
            tmp.delete(); throw e
        }
        return tmp
    }

    private fun move(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
