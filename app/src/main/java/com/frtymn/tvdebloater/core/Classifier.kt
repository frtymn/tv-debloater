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
)

@Serializable
data class PackageDatabase(
    val version: Int = 1,
    val device: String = "",
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
