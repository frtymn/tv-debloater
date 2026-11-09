package com.frtymn.tvdebloater.core

import kotlinx.serialization.Serializable

/** Safety level of a package. PROTECTED packages can never be selected or passed to a command. */
@Serializable
enum class SafetyLevel { SAFE, CAUTION, PROTECTED }

/** The only four package-state actions this app is allowed to perform. */
@Serializable
enum class PkgAction {
    /** `pm disable-user --user 0 <pkg>` (default, reversible). */
    DISABLE,
    /** `pm uninstall -k --user 0 <pkg>` (removes for user 0 only, the APK stays on /system). */
    UNINSTALL_USER,
    /** `pm enable <pkg>` (restore). */
    ENABLE,
    /** `cmd package install-existing <pkg>` (restore after UNINSTALL_USER). */
    INSTALL_EXISTING;

    val isDestructive: Boolean get() = this == DISABLE || this == UNINSTALL_USER
}

/** State of a package for user 0, as seen on the device. */
@Serializable
enum class PkgState { ENABLED, DISABLED, UNINSTALLED, UNKNOWN }

data class MemInfo(val totalKb: Long, val freeKb: Long, val usedKb: Long?)

data class StorageInfo(val totalKb: Long, val usedKb: Long, val availableKb: Long)
