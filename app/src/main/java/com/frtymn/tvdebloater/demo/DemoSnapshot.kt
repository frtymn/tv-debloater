package com.frtymn.tvdebloater.demo

import com.frtymn.tvdebloater.adb.DeviceInfo
import com.frtymn.tvdebloater.core.MemInfo
import com.frtymn.tvdebloater.core.PkgState
import com.frtymn.tvdebloater.core.StorageInfo
import com.frtymn.tvdebloater.data.Batch
import com.frtymn.tvdebloater.data.SavedDevice

/**
 * Sample data for the debug-only demo mode (screenshots, trying the UI without a TV).
 * Release builds ship a stub [DemoData] that returns null, so demo mode does not exist there.
 */
data class DemoSnapshot(
    val device: DeviceInfo,
    val savedDevices: List<SavedDevice>,
    val packages: List<Pair<String, PkgState>>,
    val memBefore: MemInfo,
    val memAfter: MemInfo,
    val storageBefore: StorageInfo,
    val storageAfter: StorageInfo,
    val batches: List<Batch>,
)
