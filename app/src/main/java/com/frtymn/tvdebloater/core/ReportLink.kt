package com.frtymn.tvdebloater.core

import java.net.URLEncoder

/**
 * Builds a prefilled "Package result report" GitHub issue URL. The app only opens this URL in
 * the browser; it never sends anything itself. Field ids match .github/ISSUE_TEMPLATE/package_result.yml.
 */
object ReportLink {
    const val NEW_ISSUE = "https://github.com/frtymn/tv-debloater/issues/new"
    const val TEMPLATE = "package_result.yml"

    fun build(packageName: String, level: SafetyLevel, dbVersion: Int, appVersion: String, deviceModel: String, androidVersion: String): String {
        fun enc(s: String) = URLEncoder.encode(s.take(200), "UTF-8")
        val params = linkedMapOf(
            "template" to TEMPLATE,
            "title" to "[Result] $packageName",
            "package" to packageName,
            "current" to level.name,
            "db_version" to dbVersion.toString(),
            "app_version" to appVersion,
            "device" to deviceModel,
            "android" to androidVersion,
        )
        return NEW_ISSUE + "?" + params.entries.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
    }
}
