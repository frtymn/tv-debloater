package com.frtymn.tvdebloater.db

import com.frtymn.tvdebloater.core.Classifier
import com.frtymn.tvdebloater.core.PackageDatabase
import com.frtymn.tvdebloater.core.PackageEntry
import com.frtymn.tvdebloater.core.ReportLink
import com.frtymn.tvdebloater.core.SafetyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.net.UnknownHostException

class PackageDbTest {

    @get:Rule val tmp = TemporaryFolder()

    // ---------------------------------------------------------------- helpers

    private fun dbJson(
        dbVersion: Int = 2,
        schema: Int = 2,
        minApp: String = "1.2.0",
        entries: String = ENTRIES,
        extra: String = "",
    ) = """{"schemaVersion":$schema,"dbVersion":$dbVersion,"publishedAt":"2026-11-10T08:00:00Z","minAppVersion":"$minApp",
        "devices":["MiTV-AESP0"],$extra"packages":[$entries]}"""

    private fun manifestFor(bytes: ByteArray, dbVersion: Int = 2, schema: Int = 2, minApp: String = "1.2.0") =
        """{"schemaVersion":$schema,"dbVersion":$dbVersion,"publishedAt":"2026-11-10T08:00:00Z","minAppVersion":"$minApp",
        "file":"packages.json","size":${bytes.size},"sha256":"${Hashing.sha256Hex(bytes)}","counts":{"SAFE":1}}""".toByteArray()

    private class FakeHttp(val routes: MutableMap<String, () -> HttpResponse>) : HttpClient {
        val calls = mutableListOf<Pair<String, String?>>()
        override fun get(url: String, etag: String?, maxBytes: Int): HttpResponse {
            calls += url to etag
            assertTrue("not allowlisted: $url", HttpsAllowlist.isAllowed(url))
            val r = (routes[url.substringAfterLast('/')] ?: error("no route $url"))()
            if (r.body.size > maxBytes) throw BodyTooLargeException()
            return r
        }
    }

    private val bundled = PackageDatabase(
        schemaVersion = 2, dbVersion = 1, devices = listOf("MiTV-AESP0"),
        packages = listOf(PackageEntry("com.miui.tv.analytics", SafetyLevel.SAFE, "Old label", sources = listOf(S1, S2))),
    )

    private fun setup(packages: ByteArray, manifest: ByteArray = manifestFor(packages), etag: String = "\"e1\""): Triple<DbStore, FakeHttp, DbUpdater> {
        val store = DbStore(tmp.newFolder())
        val http = FakeHttp(mutableMapOf(
            "manifest.json" to { HttpResponse(200, manifest, etag) },
            "packages.json" to { HttpResponse(200, packages) },
        ))
        return Triple(store, http, DbUpdater(store, http, "1.2.0"))
    }

    // ---------------------------------------------------------------- validation

    @Test fun goodFileValidates() {
        val r = DbValidator.validate(dbJson().toByteArray()) as DbValidator.Result.Ok
        assertEquals(2, r.db.dbVersion)
        assertEquals(3, r.db.packages.size)
        assertEquals(0, r.rejectedEntries)
        assertEquals("MiTV-AESP0", r.db.packages.first { it.packageName == "com.dazn" }.verifiedOn.single().model)
    }

    @Test fun oldBundledSchema1StillReads() {
        val old = """{"version":1,"device":"Mi TV Stick","packages":[{"packageName":"com.dazn","level":"SAFE","sources":["$S1","$S2"]}]}"""
        val r = DbValidator.validate(old.toByteArray()) as DbValidator.Result.Ok
        assertEquals(1, r.db.schemaVersion)
        assertEquals(0, r.db.dbVersion)
    }

    @Test fun unknownFieldsAreIgnored() {
        val e = """{"packageName":"com.dazn","level":"SAFE","sources":["$S1"],"futureField":{"a":[1,2]},"x":true}"""
        val r = DbValidator.validate(dbJson(entries = e, extra = "\"newTopLevel\":42,").toByteArray())
        assertTrue(r is DbValidator.Result.Ok)
    }

    @Test fun malformedIsRejectedAsAWhole() {
        assertTrue(DbValidator.validate("{\"packages\":[".toByteArray()) is DbValidator.Result.Invalid)
        assertTrue(DbValidator.validate("[]".toByteArray()) is DbValidator.Result.Invalid)
        assertTrue(DbValidator.validate(ByteArray(0)) is DbValidator.Result.Invalid)
        assertTrue(DbValidator.validate(dbJson(entries = "").toByteArray()) is DbValidator.Result.Invalid)
    }

    @Test fun invalidUtf8IsRejected() {
        val bytes = dbJson().toByteArray() + byteArrayOf(0xC3.toByte(), 0x28)
        assertEquals(DbValidator.Result.Invalid("not valid UTF-8"), DbValidator.validate(bytes))
    }

    @Test fun oversizedIsRejected() {
        val pad = " ".repeat(DbValidator.MAX_BYTES)
        assertTrue(DbValidator.validate((dbJson() + pad).toByteArray()) is DbValidator.Result.Invalid)
    }

    @Test fun duplicatesAreRejected() {
        val e = """{"packageName":"com.dazn","level":"SAFE"},{"packageName":"com.dazn","level":"CAUTION"}"""
        assertTrue(DbValidator.validate(dbJson(entries = e).toByteArray()) is DbValidator.Result.Invalid)
    }

    @Test fun badLevelAndBadNamesAreRejected() {
        for (e in listOf(
            """{"packageName":"com.dazn","level":"HARMLESS"}""",
            """{"packageName":"com.dazn"}""",
            """{"packageName":"com.dazn; reboot","level":"SAFE"}""",
            """{"packageName":"com.dazn","level":"SAFE","sources":["http://insecure.example"]}""",
            """{"packageName":"com.dazn","level":"SAFE","label":"${"x".repeat(81)}"}""",
            """{"packageName":"com.dazn","level":"SAFE","descriptionTr":"${"x".repeat(601)}"}""",
        )) assertTrue(e, DbValidator.validate(dbJson(entries = e).toByteArray()) is DbValidator.Result.Invalid)
    }

    @Test fun tooManyEntriesAreRejected() {
        val many = (0..DbValidator.MAX_ENTRIES).joinToString(",") { """{"packageName":"a.p$it","level":"CAUTION"}""" }
        assertTrue(DbValidator.validate(dbJson(entries = many).toByteArray()) is DbValidator.Result.Invalid)
    }

    @Test fun guardConflictsAreDroppedAndCounted() {
        val e = """{"packageName":"com.google.android.tvlauncher","level":"SAFE"},
            {"packageName":"com.android.systemui","level":"CAUTION"},
            {"packageName":"com.android.tv.settings","level":"PROTECTED"},
            {"packageName":"com.dazn","level":"SAFE","sources":["$S1","$S2"]}"""
        val r = DbValidator.validate(dbJson(entries = e).toByteArray()) as DbValidator.Result.Ok
        assertEquals(2, r.rejectedEntries)
        assertEquals(listOf("com.android.tv.settings", "com.dazn"), r.db.packages.map { it.packageName })
        // Even a database that skipped validation cannot unprotect a guarded package.
        val c = Classifier(PackageDatabase(packages = listOf(PackageEntry("com.google.android.tvlauncher", SafetyLevel.SAFE))))
        assertEquals(SafetyLevel.PROTECTED, c.classify("com.google.android.tvlauncher").level)
    }

    @Test fun unknownPackagesStayCaution() {
        val r = DbValidator.validate(dbJson().toByteArray()) as DbValidator.Result.Ok
        val c = Classifier(r.db).classify("com.example.not.listed")
        assertEquals(SafetyLevel.CAUTION, c.level)
        assertTrue(c.unknown)
    }

    @Test fun newerSchemaIsNotMalformed() {
        assertEquals(DbValidator.Result.UnsupportedSchema(3), DbValidator.validate(dbJson(schema = 3).toByteArray()))
    }

    // ---------------------------------------------------------------- manifest, versions, gating

    @Test fun manifestParsing() {
        val bytes = dbJson().toByteArray()
        val m = DbManifest.parse(manifestFor(bytes))!!
        assertEquals(2, m.dbVersion)
        assertEquals(bytes.size.toLong(), m.size)
        assertNull(DbManifest.parse("{}".toByteArray()))
        assertNull(DbManifest.parse("not json".toByteArray()))
        assertNull(DbManifest.parse(String(manifestFor(bytes)).replace("packages.json", "../etc/passwd").toByteArray()))
        assertNull(DbManifest.parse(String(manifestFor(bytes)).replace(Hashing.sha256Hex(bytes), "abc").toByteArray()))
    }

    @Test fun shaAndSizeVerification() {
        val bytes = dbJson().toByteArray()
        val m = DbManifest.parse(manifestFor(bytes))!!
        assertTrue(Hashing.matches(bytes, m))
        assertFalse(Hashing.matches(bytes.copyOf(bytes.size - 1), m))
        val tampered = bytes.copyOf().also { it[10] = (it[10] + 1).toByte() }
        assertFalse(Hashing.matches(tampered, m))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Hashing.sha256Hex(ByteArray(0)))
    }

    @Test fun versionCompare() {
        assertTrue(AppVersion.parse("1.2.0")!! < AppVersion.parse("1.10.0")!!)
        assertEquals(AppVersion.parse("v1.2.0"), AppVersion.parse("1.2"))
        assertTrue(AppVersion.parse("1.2.1-dev.40")!! > AppVersion.parse("1.2.0")!!)
        assertNull(AppVersion.parse("one.two"))
    }

    @Test fun gating() {
        val m = DbManifest.parse(manifestFor(dbJson().toByteArray()))!!
        assertEquals(DbGate.Verdict.OK, DbGate.check(m, 1, "1.2.0"))
        assertEquals(DbGate.Verdict.NOT_NEWER, DbGate.check(m, 2, "1.2.0"))
        assertEquals(DbGate.Verdict.NEEDS_APP_UPDATE, DbGate.check(m.copy(minAppVersion = "1.3.0"), 1, "1.2.0"))
        assertEquals(DbGate.Verdict.NEEDS_APP_UPDATE, DbGate.check(m.copy(schemaVersion = 3), 1, "1.2.0"))
    }

    @Test fun allowlistIsHttpsGithubRawOnly() {
        assertTrue(HttpsAllowlist.isAllowed(DbUpdater.BASE_URL + DbUpdater.MANIFEST))
        assertFalse(HttpsAllowlist.isAllowed("http://raw.githubusercontent.com/frtymn/tv-debloater/db/db/manifest.json"))
        assertFalse(HttpsAllowlist.isAllowed("https://raw.githubusercontent.com.evil.example/x.json"))
        assertFalse(HttpsAllowlist.isAllowed("https://evil.example/raw.githubusercontent.com/x.json"))
        assertFalse(HttpsAllowlist.isAllowed("https://user@raw.githubusercontent.com/x.json"))
        assertFalse(HttpsAllowlist.isAllowed("https://raw.githubusercontent.com:8443/x.json"))
        assertFalse(HttpsAllowlist.isAllowed("file:///data/x.json"))
        assertFalse(HttpsAllowlist.isAllowed("not a url"))
        try { UrlConnectionClient().get("http://example.com/", null, 10); error("not blocked") } catch (_: HttpBlockedException) {}
    }

    // ---------------------------------------------------------------- updater, store

    @Test fun updateInstallsAndCountsChanges() {
        val bytes = dbJson().toByteArray()
        val (store, _, up) = setup(bytes)
        val r = up.check(ActiveDb(bundled, DbSource.BUNDLED), 1000) as DbCheckResult.Updated
        assertEquals(2, r.added)    // com.dazn, com.mitv.dream
        assertEquals(1, r.changed)  // com.miui.tv.analytics label
        assertEquals(DbSource.DOWNLOADED, store.loadActive(bundled).source)
        assertEquals(2, store.loadActive(bundled).db.dbVersion)
        assertEquals("\"e1\"", store.readMeta().etag)
        assertEquals(1000, store.readMeta().lastCheckedAt)
    }

    @Test fun etagIsSentAnd304MeansUpToDate() {
        val bytes = dbJson().toByteArray()
        val (store, http, up) = setup(bytes)
        up.check(ActiveDb(bundled, DbSource.BUNDLED), 1)
        http.routes["manifest.json"] = { HttpResponse(304) }
        val active = store.loadActive(bundled)
        assertEquals(DbCheckResult.UpToDate, up.check(active, 2))
        assertEquals("\"e1\"", http.calls.last().second)
        assertEquals(1, http.calls.count { it.first.endsWith("packages.json") })
    }

    @Test fun notNewerSkipsDownload() {
        val bytes = dbJson(dbVersion = 1).toByteArray()
        val (_, http, up) = setup(bytes, manifestFor(bytes, dbVersion = 1))
        assertEquals(DbCheckResult.UpToDate, up.check(ActiveDb(bundled, DbSource.BUNDLED), 1))
        assertTrue(http.calls.none { it.first.endsWith("packages.json") })
    }

    @Test fun minAppVersionAndSchemaGating() {
        val bytes = dbJson(minApp = "9.0.0").toByteArray()
        val (store, http, up) = setup(bytes, manifestFor(bytes, minApp = "9.0.0"))
        assertEquals(DbCheckResult.NeedsAppUpdate, up.check(ActiveDb(bundled, DbSource.BUNDLED), 1))
        assertTrue(store.readMeta().needsAppUpdate)
        assertNull(store.readMeta().etag) // retried after the app is updated
        assertTrue(http.calls.none { it.first.endsWith("packages.json") })
        assertEquals(DbSource.BUNDLED, store.loadActive(bundled).source)

        val b3 = dbJson(schema = 3).toByteArray()
        val (store3, _, up3) = setup(b3, manifestFor(b3, schema = 3))
        assertEquals(DbCheckResult.NeedsAppUpdate, up3.check(ActiveDb(bundled, DbSource.BUNDLED), 1))
        assertEquals(DbSource.BUNDLED, store3.loadActive(bundled).source)
    }

    @Test fun badHashOrInvalidFileKeepsPreviousDatabase() {
        val good = dbJson().toByteArray()
        val (store, http, up) = setup(good)
        up.check(ActiveDb(bundled, DbSource.BUNDLED), 1)
        // v3: the manifest says one thing, the download is another (cut or tampered).
        val v3 = dbJson(dbVersion = 3).toByteArray()
        http.routes["manifest.json"] = { HttpResponse(200, manifestFor(v3, dbVersion = 3), "\"e3\"") }
        http.routes["packages.json"] = { HttpResponse(200, v3.copyOf(v3.size - 5)) }
        assertTrue(up.check(store.loadActive(bundled), 2) is DbCheckResult.Failed)
        assertEquals("\"e1\"", store.readMeta().etag) // not saved, so the next check retries
        // v3 with a valid hash but malformed content.
        val bad = """{"schemaVersion":2,"dbVersion":3,"packages":[{"packageName":"com.dazn","level":"NOPE"}]}""".toByteArray()
        http.routes["manifest.json"] = { HttpResponse(200, manifestFor(bad, dbVersion = 3), "\"e3\"") }
        http.routes["packages.json"] = { HttpResponse(200, bad) }
        assertTrue(up.check(store.loadActive(bundled), 3) is DbCheckResult.Failed)
        assertEquals(2, store.loadActive(bundled).db.dbVersion)
    }

    @Test fun offlineRateLimitAndTimeoutNeverThrow() {
        val (store, http, up) = setup(dbJson().toByteArray())
        http.routes["manifest.json"] = { throw UnknownHostException("raw.githubusercontent.com") }
        assertEquals(DbCheckResult.Offline, up.check(ActiveDb(bundled, DbSource.BUNDLED), 1))
        http.routes["manifest.json"] = { HttpResponse(429) }
        assertEquals(DbCheckResult.RateLimited, up.check(ActiveDb(bundled, DbSource.BUNDLED), 2))
        http.routes["manifest.json"] = { throw java.net.SocketTimeoutException("read timed out") }
        assertEquals(DbCheckResult.Failed("timeout"), up.check(ActiveDb(bundled, DbSource.BUNDLED), 3))
        http.routes["manifest.json"] = { throw IOException("Incomplete download") }
        assertTrue(up.check(ActiveDb(bundled, DbSource.BUNDLED), 4) is DbCheckResult.Failed)
        assertEquals(DbSource.BUNDLED, store.loadActive(bundled).source)
        assertEquals(4, store.readMeta().lastCheckedAt)
    }

    @Test fun atomicSwapKeepsTwoVersionsAndFallsBack() {
        val dir = tmp.newFolder()
        val store = DbStore(dir)
        store.install(dbJson(dbVersion = 2).toByteArray())
        store.install(dbJson(dbVersion = 3).toByteArray())
        assertEquals(3, store.loadActive(bundled).db.dbVersion)
        assertTrue(File(dir, "previous.json").isFile)
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
        // A corrupted current file falls back to the previous good one, then to the bundled list.
        File(dir, "current.json").writeText("{ half written")
        assertEquals(2, store.loadActive(bundled).db.dbVersion)
        File(dir, "previous.json").writeBytes(ByteArray(3))
        assertEquals(DbSource.BUNDLED, store.loadActive(bundled).source)
    }

    @Test fun bundledWinsWhenNewerThanDownload() {
        val store = DbStore(tmp.newFolder())
        store.install(dbJson(dbVersion = 2).toByteArray())
        val newerBundled = bundled.copy(dbVersion = 5)
        assertEquals(ActiveDb(newerBundled, DbSource.BUNDLED), store.loadActive(newerBundled))
    }

    @Test fun resetToBundled() {
        val (store, _, up) = setup(dbJson().toByteArray())
        up.check(ActiveDb(bundled, DbSource.BUNDLED), 1)
        assertTrue(store.hasDownloads())
        store.reset(now = 50)
        assertFalse(store.hasDownloads())
        assertEquals(DbSource.BUNDLED, store.loadActive(bundled).source)
        assertNull(store.readMeta().etag)
        assertFalse(up.isDue(51))
        assertTrue(up.isDue(50 + DbUpdater.MIN_INTERVAL_MS))
    }

    @Test fun reportLinkIsPrefilled() {
        val url = ReportLink.build("com.dazn", SafetyLevel.SAFE, 3, "1.2.0", "MiTV-AESP0", "9")
        assertTrue(url.startsWith("https://github.com/frtymn/tv-debloater/issues/new?template=package_result.yml&"))
        val q = url.substringAfter('?').split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        assertEquals("com.dazn", q["package"]); assertEquals("SAFE", q["current"]); assertEquals("3", q["db_version"])
        assertEquals("1.2.0", q["app_version"]); assertEquals("MiTV-AESP0", q["device"]); assertEquals("9", q["android"])
        assertTrue(q.keys.none { it == "what_did" || it == "what_happened" })
    }

    @Test fun reportTemplateHasTheLinkFieldIds() {
        val f = listOf("../.github/ISSUE_TEMPLATE/package_result.yml", ".github/ISSUE_TEMPLATE/package_result.yml").map(::File).first { it.exists() }
        val ids = Regex("""^\s*id:\s*(\S+)""", RegexOption.MULTILINE).findAll(f.readText()).map { it.groupValues[1] }.toSet()
        assertTrue(ids.containsAll(listOf("package", "current", "db_version", "app_version", "device", "android", "what_did", "what_happened")))
    }

    @Test fun bundledDatabaseIsSchema2AndValid() {
        val f = listOf("../db/packages.json", "db/packages.json").map(::File).first { it.exists() }
        val r = DbValidator.validate(f.readBytes()) as DbValidator.Result.Ok
        assertEquals(0, r.rejectedEntries)
        assertEquals(2, r.db.schemaVersion)
        assertTrue(r.db.dbVersion >= 1)
        assertTrue("MiTV-AESP0" in r.db.devices)
    }

    companion object {
        const val S1 = "https://github.com/Gathin23/cleanyourtv/blob/main/examples/xiaomi-mitv-aesp0/debloat.txt"
        const val S2 = "https://github.com/yositapjani/ExtremeSlimTV/blob/main/profiles/mitv-aesp0-mi-tv-stick-fhd.txt"
        val ENTRIES = """
            {"packageName":"com.miui.tv.analytics","level":"SAFE","label":"Xiaomi Analytics","sources":["$S1","$S2"]},
            {"packageName":"com.dazn","level":"SAFE","sources":["$S1"],"verifiedOn":[{"model":"MiTV-AESP0","android":"9","date":"2026-11-09"}]},
            {"packageName":"com.mitv.dream","level":"CAUTION","aggressive":true,"devices":["MiTV-AESP0"],"notes":"Screensaver."}
        """.trimIndent()
    }
}
