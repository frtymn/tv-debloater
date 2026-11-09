package com.frtymn.tvdebloater.data

import com.frtymn.tvdebloater.core.PkgAction
import com.frtymn.tvdebloater.core.PkgState
import kotlinx.serialization.Serializable

@Serializable
enum class BatchKind { CHANGE, UNDO, PANIC }

@Serializable
data class BatchEntry(
    val packageName: String,
    val action: PkgAction,
    /** State before this command ran. Used for undo. */
    val previousState: PkgState,
    val command: String,
    /** null = not run yet (the batch is saved before execution). */
    val success: Boolean? = null,
    val output: String = "",
    val undone: Boolean = false,
    /** For UNDO/PANIC batches: "batchId#index" of the entry this step restores. */
    val restores: String? = null,
)

@Serializable
data class Batch(
    val id: String,
    val timestamp: Long,
    val device: String,
    val kind: BatchKind,
    val action: PkgAction,
    val entries: List<BatchEntry>,
)

@Serializable
data class HistoryExport(
    val app: String = "tv-debloater",
    val format: Int = 1,
    val exportedAt: Long,
    val batches: List<Batch>,
)

@Serializable
data class SavedDevice(val host: String, val port: Int, val name: String = "", val lastUsed: Long = 0)
