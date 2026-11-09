package com.frtymn.tvdebloater.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "tv_debloater")

/** Settings, device history and the backup history, all in Jetpack DataStore. */
class AppStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val DRY_RUN = booleanPreferencesKey("dry_run")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val DEVICES = stringPreferencesKey("devices")
        val BATCHES = stringPreferencesKey("batches")
        val DB_AUTO_UPDATE = booleanPreferencesKey("db_auto_update")
        val DEVICE_NOTICE_DISMISSED = stringSetPreferencesKey("device_notice_dismissed")
        val AGGRESSIVE_ALLOWED = stringSetPreferencesKey("aggressive_allowed_models")
    }

    private val batchListSerializer = ListSerializer(Batch.serializer())
    private val deviceListSerializer = ListSerializer(SavedDevice.serializer())

    /** Dry run is ON until the user turns it off. */
    val dryRun: Flow<Boolean> = context.dataStore.data.map { it[Keys.DRY_RUN] ?: true }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }
    val devices: Flow<List<SavedDevice>> = context.dataStore.data.map { p ->
        p[Keys.DEVICES]?.let { runCatching { json.decodeFromString(deviceListSerializer, it) }.getOrNull() } ?: emptyList()
    }
    val batches: Flow<List<Batch>> = context.dataStore.data.map { p ->
        p[Keys.BATCHES]?.let { runCatching { json.decodeFromString(batchListSerializer, it) }.getOrNull() } ?: emptyList()
    }

    /** "Update package list automatically": ON until the user turns it off. */
    val dbAutoUpdate: Flow<Boolean> = context.dataStore.data.map { it[Keys.DB_AUTO_UPDATE] ?: true }
    /** Device models whose "unverified device" notice was dismissed. */
    val deviceNoticeDismissed: Flow<Set<String>> = context.dataStore.data.map { it[Keys.DEVICE_NOTICE_DISMISSED] ?: emptySet() }
    /** Unverified device models on which the user allowed the aggressive profile. */
    val aggressiveAllowed: Flow<Set<String>> = context.dataStore.data.map { it[Keys.AGGRESSIVE_ALLOWED] ?: emptySet() }

    suspend fun setDbAutoUpdate(value: Boolean) = context.dataStore.edit { it[Keys.DB_AUTO_UPDATE] = value }
    suspend fun dismissDeviceNotice(model: String) = context.dataStore.edit { it[Keys.DEVICE_NOTICE_DISMISSED] = (it[Keys.DEVICE_NOTICE_DISMISSED] ?: emptySet()) + model }
    suspend fun allowAggressive(model: String) = context.dataStore.edit { it[Keys.AGGRESSIVE_ALLOWED] = (it[Keys.AGGRESSIVE_ALLOWED] ?: emptySet()) + model }

    suspend fun setDryRun(value: Boolean) = context.dataStore.edit { it[Keys.DRY_RUN] = value }
    suspend fun setOnboardingDone(value: Boolean) = context.dataStore.edit { it[Keys.ONBOARDING_DONE] = value }

    suspend fun rememberDevice(device: SavedDevice) {
        val list = devices.first().filterNot { it.host == device.host && it.port == device.port }
        val updated = (listOf(device) + list).take(10)
        context.dataStore.edit { it[Keys.DEVICES] = json.encodeToString(deviceListSerializer, updated) }
    }

    suspend fun forgetDevice(device: SavedDevice) {
        val updated = devices.first().filterNot { it.host == device.host && it.port == device.port }
        context.dataStore.edit { it[Keys.DEVICES] = json.encodeToString(deviceListSerializer, updated) }
    }

    /** Inserts or replaces a batch (matched by id). */
    suspend fun saveBatch(batch: Batch) {
        context.dataStore.edit { p ->
            val list = p[Keys.BATCHES]?.let { runCatching { json.decodeFromString(batchListSerializer, it) }.getOrNull() }
                ?: emptyList()
            val updated = list.filterNot { it.id == batch.id } + batch
            p[Keys.BATCHES] = json.encodeToString(batchListSerializer, updated.sortedBy { it.timestamp })
        }
    }

    suspend fun updateBatches(transform: (List<Batch>) -> List<Batch>) {
        context.dataStore.edit { p ->
            val list = p[Keys.BATCHES]?.let { runCatching { json.decodeFromString(batchListSerializer, it) }.getOrNull() }
                ?: emptyList()
            p[Keys.BATCHES] = json.encodeToString(batchListSerializer, transform(list).sortedBy { it.timestamp })
        }
    }

    fun exportJson(batches: List<Batch>): String =
        Json { prettyPrint = true; encodeDefaults = true }.encodeToString(
            HistoryExport.serializer(), HistoryExport(exportedAt = System.currentTimeMillis(), batches = batches),
        )

    /** Merges an exported file into the history. Returns the number of new batches. */
    suspend fun importJson(text: String): Int {
        val imported = json.decodeFromString(HistoryExport.serializer(), text)
        require(imported.app == "tv-debloater") { "Not a TV Debloater history file" }
        var added = 0
        updateBatches { current ->
            val ids = current.map { it.id }.toSet()
            val fresh = imported.batches.filter { it.id !in ids }
            added = fresh.size
            current + fresh
        }
        return added
    }
}
