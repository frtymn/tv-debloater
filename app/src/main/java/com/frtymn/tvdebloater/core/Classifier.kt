package com.frtymn.tvdebloater.core

import kotlinx.serialization.Serializable

@Serializable
data class PackageEntry(
    val packageName: String,
    val level: SafetyLevel,
    val label: String = "",
    val descriptionEn: String = "",
    val descriptionTr: String = "",
    val sources: List<String> = emptyList(),
    /** Preselected by the "Aggressive" profile (CAUTION only). */
    val aggressive: Boolean = false,
    /** Schema 2, optional: where and when someone checked this entry on a real device. */
    val verifiedOn: List<VerifiedOn> = emptyList(),
    /** Schema 2, optional: device models (ro.product.model) this entry applies to. Empty = the database default. */
    val devices: List<String> = emptyList(),
    /** Schema 2, optional: maintainer notes. */
    val notes: String = "",
)

@Serializable
data class VerifiedOn(val model: String = "", val android: String = "", val date: String = "")

/**
 * The package safety database. Schema 1 (the original bundled format) only had `version`, `device`
 * and `packages`; schema 2 adds the fields below. Unknown fields are always ignored.
 */
@Serializable
data class PackageDatabase(
    /** Schema 1 format number. Kept so old files still parse. */
    val version: Int = 1,
    val schemaVersion: Int = 1,
    /** Monotonic database version. 0 = unversioned (schema 1). */
    val dbVersion: Int = 0,
    /** UTC ISO-8601 time the database was published, or "". */
    val publishedAt: String = "",
    val minAppVersion: String = "",
    val device: String = "",
    /** Device models (ro.product.model) the list was verified on. */
    val devices: List<String> = emptyList(),
    val packages: List<PackageEntry> = emptyList(),
)

data class Classification(val level: SafetyLevel, val entry: PackageEntry?, val unknown: Boolean)

/** Final level = the hard-coded guard first, then the JSON database, then conservative defaults. */
class Classifier(database: PackageDatabase) {
    private val byName = database.packages.associateBy { it.packageName }

    fun classify(packageName: String): Classification {
        val entry = byName[packageName]
        if (ProtectedGuard.isProtected(packageName)) return Classification(SafetyLevel.PROTECTED, entry, entry == null)
        if (entry != null) return Classification(entry.level, entry, false)
        // Unknown core Android packages are treated as protected; everything else unknown = CAUTION.
        if (packageName == "android" || packageName.startsWith("com.android.") || packageName.startsWith("android.")) {
            return Classification(SafetyLevel.PROTECTED, null, true)
        }
        return Classification(SafetyLevel.CAUTION, null, true)
    }
}
