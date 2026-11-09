package com.frtymn.tvdebloater.data

import android.content.Context
import com.frtymn.tvdebloater.core.PackageDatabase
import com.frtymn.tvdebloater.db.DbValidator

/** Loads the bundled assets/packages.json (copied from db/packages.json at build time), the permanent fallback. */
object PackageDbLoader {
    fun load(context: Context): PackageDatabase = runCatching {
        val bytes = context.assets.open("packages.json").use { it.readBytes() }
        (DbValidator.validate(bytes) as? DbValidator.Result.Ok)?.db
    }.getOrNull() ?: PackageDatabase() // the code guard still protects everything critical
}
