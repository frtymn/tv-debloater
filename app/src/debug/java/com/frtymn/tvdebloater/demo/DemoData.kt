package com.frtymn.tvdebloater.demo

import com.frtymn.tvdebloater.adb.DeviceInfo
import com.frtymn.tvdebloater.core.MemInfo
import com.frtymn.tvdebloater.core.PkgAction
import com.frtymn.tvdebloater.core.PkgState
import com.frtymn.tvdebloater.core.StorageInfo
import com.frtymn.tvdebloater.data.Batch
import com.frtymn.tvdebloater.data.BatchEntry
import com.frtymn.tvdebloater.data.BatchKind
import com.frtymn.tvdebloater.data.SavedDevice

/**
 * Debug-only sample data. Package names are real names from the bundled database and from a
 * factory Mi TV Stick inventory; the device address, RAM and storage values are made up.
 */
object DemoData {
    val available: Boolean get() = true

    // Fixed times so screenshots are reproducible: 2026-11-08 20:14 and 2026-11-09 10:32 UTC.
    private const val T1 = 1_794_255_240_000L
    private const val T2 = 1_794_306_720_000L

    fun snapshot(): DemoSnapshot {
        val enabled = PkgState.ENABLED
        val packages = listOf(
            "com.miui.tv.analytics" to PkgState.DISABLED,
            "tv.alphonso.alphonso_eula" to PkgState.DISABLED,
            "com.google.android.feedback" to enabled,
            "com.google.android.tv.bugreportsender" to enabled,
            "com.mitv.tvhome.michannel" to enabled,
            "com.xm.webcontent" to enabled,
            "android.autoinstalls.config.xiaomi.mibox3" to enabled,
            "com.mitv.download.service" to enabled,
            "com.mitv.dream" to enabled,
            "com.android.printspooler" to enabled,
            "com.google.android.play.games" to enabled,
            "com.google.android.videos" to enabled,
            "com.google.android.tvrecommendations" to enabled,
            "com.mitv.tvhome.atv" to enabled,
            "com.mitv.milinkservice" to enabled,
            "com.google.android.katniss" to enabled,
            "com.google.android.apps.mediashell" to enabled,
            "com.google.android.youtube.tv" to enabled,
            "com.netflix.ninja" to enabled,
            "com.android.dreams.basic" to enabled,
            "com.google.android.tungsten.overscan" to enabled,
            "com.google.android.configupdater" to enabled,
            "com.google.android.tvlauncher" to enabled,
            "com.android.vending" to enabled,
            "com.google.android.gms" to enabled,
            "com.android.systemui" to enabled,
            "com.android.tv.settings" to enabled,
            "com.android.bluetooth" to enabled,
            "com.xiaomi.mitv.updateservice" to enabled,
            "com.xiaomi.android.tvsetup.partnercustomizer" to enabled,
        )
        val device = DeviceInfo(host = "192.168.1.50", port = 5555, model = "MiTV-AESP0", androidVersion = "9", codename = "aquaman")
        val first = Batch(
            id = "demo-1", timestamp = T1, device = "MiTV-AESP0 (192.168.1.50)", kind = BatchKind.CHANGE, action = PkgAction.DISABLE,
            entries = listOf(
                BatchEntry("com.miui.tv.analytics", PkgAction.DISABLE, PkgState.ENABLED, "pm disable-user --user 0 com.miui.tv.analytics", true, "Package com.miui.tv.analytics new state: disabled-user"),
                BatchEntry("tv.alphonso.alphonso_eula", PkgAction.DISABLE, PkgState.ENABLED, "pm disable-user --user 0 tv.alphonso.alphonso_eula", true, "Package tv.alphonso.alphonso_eula new state: disabled-user"),
                BatchEntry("com.mitv.videoplayer", PkgAction.DISABLE, PkgState.ENABLED, "pm disable-user --user 0 com.mitv.videoplayer", true, "Package com.mitv.videoplayer new state: disabled-user", undone = true),
            ),
        )
        val undo = Batch(
            id = "demo-2", timestamp = T2, device = "MiTV-AESP0 (192.168.1.50)", kind = BatchKind.UNDO, action = PkgAction.ENABLE,
            entries = listOf(
                BatchEntry("com.mitv.videoplayer", PkgAction.ENABLE, PkgState.DISABLED, "pm enable com.mitv.videoplayer", true, "Package com.mitv.videoplayer new state: enabled", restores = "demo-1#2"),
            ),
        )
        return DemoSnapshot(
            device = device,
            savedDevices = listOf(
                SavedDevice("192.168.1.50", 5555, "MiTV-AESP0", T2),
                SavedDevice("192.168.1.73", 5555, "Living room TV", T1),
            ),
            packages = packages,
            memBefore = MemInfo(totalKb = 872_000, freeKb = 198_000, usedKb = 674_000),
            memAfter = MemInfo(totalKb = 872_000, freeKb = 271_000, usedKb = 601_000),
            storageBefore = StorageInfo(totalKb = 4_683_000, usedKb = 3_310_000, availableKb = 1_373_000),
            storageAfter = StorageInfo(totalKb = 4_683_000, usedKb = 3_302_000, availableKb = 1_381_000),
            batches = listOf(undo, first),
        )
    }
}
