package com.frtymn.tvdebloater.data

import android.content.Context
import com.frtymn.tvdebloater.core.PackageDatabase
import kotlinx.serialization.json.Json

object PackageDbLoader {
    fun load(context: Context): PackageDatabase = runCatching {
        val text = context.assets.open("packages.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        Json { ignoreUnknownKeys = true }.decodeFromString(PackageDatabase.serializer(), text)
    }.getOrElse { PackageDatabase() } // the code guard still protects everything critical
}
