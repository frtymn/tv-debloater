package com.frtymn.tvdebloater.db

import com.frtymn.tvdebloater.core.CommandBuilder
import com.frtymn.tvdebloater.core.PackageDatabase
import com.frtymn.tvdebloater.core.PackageEntry
import com.frtymn.tvdebloater.core.ProtectedGuard
import com.frtymn.tvdebloater.core.SafetyLevel
import com.frtymn.tvdebloater.core.VerifiedOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Strict validation of a package database file. Downloaded files are untrusted input:
 * anything malformed rejects the whole file, so the previous good database stays active.
 * Entries that conflict with the hard-coded [ProtectedGuard] are dropped one by one and counted.
 */
object DbValidator {

    const val SUPPORTED_SCHEMA = 2
    const val MAX_BYTES = 1_048_576
    const val MAX_ENTRIES = 2_000
    const val MAX_LABEL = 80
    const val MAX_DESCRIPTION = 600
    const val MAX_NOTES = 600
    const val MAX_SOURCES = 10
    const val MAX_URL = 300
    const val MAX_DEVICES = 20
    const val MAX_SHORT = 64
    const val MAX_VERIFIED = 10

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }
    private val LEVELS = setOf("SAFE", "CAUTION", "PROTECTED")

    sealed interface Result {
        data class Ok(val db: PackageDatabase, val rejectedEntries: Int, val rejectedNames: List<String>) : Result
        /** Valid file from a newer schema this app cannot read. Not an error: the app needs an update. */
        data class UnsupportedSchema(val schemaVersion: Int) : Result
        data class Invalid(val reason: String) : Result
    }

    /** Raw shapes with plain strings, so bad values give clear reasons instead of parser errors. */
    @Serializable
    private data class RawEntry(
        val packageName: String? = null,
        val level: String? = null,
        val label: String = "",
        val descriptionEn: String = "",
        val descriptionTr: String = "",
        val sources: List<String> = emptyList(),
        val aggressive: Boolean = false,
        val verifiedOn: List<VerifiedOn> = emptyList(),
        val devices: List<String> = emptyList(),
        val notes: String = "",
    )

    @Serializable
    private data class RawDb(
        val version: Int = 1,
        val schemaVersion: Int? = null,
        val dbVersion: Int = 0,
        val publishedAt: String = "",
        val minAppVersion: String = "",
        val device: String = "",
        val devices: List<String> = emptyList(),
        val packages: List<RawEntry>? = null,
    )

    @Serializable
    private data class SchemaProbe(val version: Int = 1, val schemaVersion: Int? = null)

    /** Strict UTF-8: invalid byte sequences are an error, never silently replaced. */
    fun decodeUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        null
    }

    fun validate(bytes: ByteArray): Result {
        if (bytes.isEmpty()) return Result.Invalid("empty file")
        if (bytes.size > MAX_BYTES) return Result.Invalid("file too large (${bytes.size} bytes)")
        val text = decodeUtf8(bytes) ?: return Result.Invalid("not valid UTF-8")
        return validate(text)
    }

    fun validate(text: String): Result {
        if (text.length > MAX_BYTES) return Result.Invalid("file too large")
        // Read only the schema number first, so a newer schema is reported as such and not as "malformed".
        val probe = try { json.decodeFromString(SchemaProbe.serializer(), text) } catch (e: Exception) {
            return Result.Invalid("not a JSON object: ${e.message?.take(120)}")
        }
        val schema = probe.schemaVersion ?: probe.version
        if (schema < 1) return Result.Invalid("bad schemaVersion $schema")
        if (schema > SUPPORTED_SCHEMA) return Result.UnsupportedSchema(schema)

        val raw = try { json.decodeFromString(RawDb.serializer(), text) } catch (e: Exception) {
            return Result.Invalid("malformed: ${e.message?.take(120)}")
        }
        val list = raw.packages ?: return Result.Invalid("missing packages")
        if (list.isEmpty()) return Result.Invalid("no entries")
        if (list.size > MAX_ENTRIES) return Result.Invalid("too many entries (${list.size})")
        if (raw.dbVersion < 0) return Result.Invalid("bad dbVersion")
        if (raw.publishedAt.length > MAX_SHORT || raw.minAppVersion.length > MAX_SHORT || raw.device.length > 200) return Result.Invalid("header field too long")
        if (raw.devices.size > MAX_DEVICES || raw.devices.any { it.isBlank() || it.length > MAX_SHORT }) return Result.Invalid("bad devices")

        val seen = HashSet<String>()
        val accepted = ArrayList<PackageEntry>(list.size)
        val rejected = ArrayList<String>()
        for ((i, e) in list.withIndex()) {
            val name = e.packageName ?: return Result.Invalid("entry $i: missing packageName")
            if (!CommandBuilder.isValidPackageName(name)) return Result.Invalid("entry $i: bad package name")
            if (!seen.add(name)) return Result.Invalid("duplicate package $name")
            val levelName = e.level ?: return Result.Invalid("$name: missing level")
            if (levelName !in LEVELS) return Result.Invalid("$name: bad level")
            if (e.label.length > MAX_LABEL) return Result.Invalid("$name: label too long")
            if (e.descriptionEn.length > MAX_DESCRIPTION || e.descriptionTr.length > MAX_DESCRIPTION) return Result.Invalid("$name: description too long")
            if (e.notes.length > MAX_NOTES) return Result.Invalid("$name: notes too long")
            if (e.sources.size > MAX_SOURCES) return Result.Invalid("$name: too many sources")
            if (e.sources.any { it.length > MAX_URL || !it.startsWith("https://") }) return Result.Invalid("$name: bad source URL")
            if (e.devices.size > MAX_DEVICES || e.devices.any { it.isBlank() || it.length > MAX_SHORT }) return Result.Invalid("$name: bad devices")
            if (e.verifiedOn.size > MAX_VERIFIED || e.verifiedOn.any { it.model.length > MAX_SHORT || it.android.length > MAX_SHORT || it.date.length > MAX_SHORT }) {
                return Result.Invalid("$name: bad verifiedOn")
            }
            val level = SafetyLevel.valueOf(levelName)
            // The code guard always wins: a guarded package can only ever be PROTECTED.
            if (ProtectedGuard.isProtected(name) && level != SafetyLevel.PROTECTED) {
                rejected += name
                continue
            }
            accepted += PackageEntry(
                packageName = name, level = level, label = e.label, descriptionEn = e.descriptionEn, descriptionTr = e.descriptionTr,
                sources = e.sources, aggressive = e.aggressive && level == SafetyLevel.CAUTION,
                verifiedOn = e.verifiedOn, devices = e.devices, notes = e.notes,
            )
        }
        val db = PackageDatabase(
            version = raw.version, schemaVersion = schema, dbVersion = raw.dbVersion, publishedAt = raw.publishedAt,
            minAppVersion = raw.minAppVersion, device = raw.device, devices = raw.devices, packages = accepted,
        )
        return Result.Ok(db, rejected.size, rejected)
    }
}
