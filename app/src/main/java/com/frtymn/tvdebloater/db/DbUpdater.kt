package com.frtymn.tvdebloater.db

import com.frtymn.tvdebloater.core.PackageDatabase
import java.io.IOException

sealed interface DbCheckResult {
    data class Updated(val active: ActiveDb, val added: Int, val changed: Int, val rejectedEntries: Int) : DbCheckResult
    data object UpToDate : DbCheckResult
    data object NeedsAppUpdate : DbCheckResult
    data object Offline : DbCheckResult
    data object RateLimited : DbCheckResult
    data class Failed(val reason: String) : DbCheckResult
}

/**
 * Checks the "db" branch for a newer package database. Order: manifest (with ETag) -> version
 * and app gating -> download -> size + SHA-256 -> strict validation -> atomic install.
 * Any failure leaves the active database untouched. Nothing here ever runs an ADB command.
 */
class DbUpdater(
    private val store: DbStore,
    private val http: HttpClient,
    private val appVersion: String,
    private val baseUrl: String = BASE_URL,
) {
    companion object {
        const val BASE_URL = "https://raw.githubusercontent.com/frtymn/tv-debloater/db/db/"
        const val MANIFEST = "manifest.json"
        const val MIN_INTERVAL_MS = 6 * 60 * 60 * 1000L

        /** New = in [new] only. Changed = in both but with different content. */
        fun diff(old: PackageDatabase, new: PackageDatabase): Pair<Int, Int> {
            val before = old.packages.associateBy { it.packageName }
            var added = 0; var changed = 0
            for (e in new.packages) {
                val o = before[e.packageName]
                if (o == null) added++ else if (o != e) changed++
            }
            return added to changed
        }
    }

    /** True when an automatic check is due (at most every 6 hours). */
    fun isDue(now: Long): Boolean {
        val last = store.readMeta().lastCheckedAt
        return last <= 0 || now - last >= MIN_INTERVAL_MS || now < last
    }

    fun check(active: ActiveDb, now: Long): DbCheckResult {
        val meta = store.readMeta()
        val step = try {
            doCheck(active, meta)
        } catch (e: IOException) {
            Step(if (e.isOffline()) DbCheckResult.Offline else DbCheckResult.Failed(if (e.isTimeout()) "timeout" else e.message ?: "I/O error"))
        } catch (e: Exception) {
            Step(DbCheckResult.Failed(e.javaClass.simpleName))
        }
        val r = step.result
        val transient = r is DbCheckResult.Offline || r is DbCheckResult.RateLimited || r is DbCheckResult.Failed
        store.writeMeta(
            meta.copy(
                etag = step.saveEtag ?: meta.etag,
                lastCheckedAt = now,
                needsAppUpdate = if (transient || step.notModified) meta.needsAppUpdate else r is DbCheckResult.NeedsAppUpdate,
                lastResult = describe(r),
                lastRejectedEntries = (r as? DbCheckResult.Updated)?.rejectedEntries ?: meta.lastRejectedEntries,
            ),
        )
        return r
    }

    /** [saveEtag] is stored only when the manifest was fully handled, so a failed run is retried. */
    private data class Step(val result: DbCheckResult, val saveEtag: String? = null, val notModified: Boolean = false)

    private fun doCheck(active: ActiveDb, meta: DbMeta): Step {
        val m = http.get(baseUrl + MANIFEST, meta.etag, DbManifest.MAX_BYTES)
        when (m.code) {
            200 -> Unit
            304 -> return Step(DbCheckResult.UpToDate, notModified = true)
            403, 429 -> return Step(DbCheckResult.RateLimited)
            else -> return Step(DbCheckResult.Failed("manifest HTTP ${m.code}"))
        }
        val manifest = DbManifest.parse(m.body) ?: return Step(DbCheckResult.Failed("bad manifest"))
        when (DbGate.check(manifest, active.db.dbVersion, appVersion)) {
            // Not saving the ETag here, so the check is repeated after the app is updated.
            DbGate.Verdict.NEEDS_APP_UPDATE -> return Step(DbCheckResult.NeedsAppUpdate)
            DbGate.Verdict.NOT_NEWER -> return Step(DbCheckResult.UpToDate, saveEtag = m.etag)
            DbGate.Verdict.OK -> Unit
        }
        val p = http.get(baseUrl + manifest.file, null, manifest.size.toInt())
        when (p.code) {
            200 -> Unit
            403, 429 -> return Step(DbCheckResult.RateLimited)
            else -> return Step(DbCheckResult.Failed("database HTTP ${p.code}"))
        }
        if (!Hashing.matches(p.body, manifest)) return Step(DbCheckResult.Failed("size or SHA-256 mismatch"))
        val v = DbValidator.validate(p.body)
        val ok = when (v) {
            is DbValidator.Result.Ok -> v
            is DbValidator.Result.UnsupportedSchema -> return Step(DbCheckResult.NeedsAppUpdate)
            is DbValidator.Result.Invalid -> return Step(DbCheckResult.Failed("invalid: ${v.reason}"))
        }
        if (ok.db.dbVersion != manifest.dbVersion) return Step(DbCheckResult.Failed("dbVersion differs from manifest"))
        val needApp = AppVersion.parse(ok.db.minAppVersion.ifBlank { "0" })
        val have = AppVersion.parse(appVersion)
        if (needApp == null || have == null || needApp > have) return Step(DbCheckResult.NeedsAppUpdate)
        store.install(p.body)
        val (added, changed) = diff(active.db, ok.db)
        return Step(DbCheckResult.Updated(ActiveDb(ok.db, DbSource.DOWNLOADED), added, changed, ok.rejectedEntries), saveEtag = m.etag)
    }

    private fun describe(r: DbCheckResult): String = when (r) {
        is DbCheckResult.Updated -> "updated to ${r.active.db.dbVersion}: +${r.added} new, ${r.changed} changed, ${r.rejectedEntries} rejected"
        DbCheckResult.UpToDate -> "up to date"
        DbCheckResult.NeedsAppUpdate -> "needs a newer app"
        DbCheckResult.Offline -> "offline"
        DbCheckResult.RateLimited -> "rate limited"
        is DbCheckResult.Failed -> "failed: ${r.reason}"
    }
}
