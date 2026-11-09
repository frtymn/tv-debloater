package com.frtymn.tvdebloater.core

/**
 * Builds every shell command the app may send. Nothing else in the app creates `pm` commands,
 * so the protected-package guard and the package-name validation cannot be bypassed.
 */
object CommandBuilder {

    /** Java package names only: letters, digits, underscores and dots. No spaces or shell characters. */
    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*$")

    const val LIST_SYSTEM = "pm list packages -s"
    const val LIST_DISABLED = "pm list packages -d"
    /** Includes packages removed for user 0 (`-u`), so they can be shown and restored. */
    const val LIST_SYSTEM_INCLUDING_UNINSTALLED = "pm list packages -s -u"
    const val MEMINFO = "dumpsys meminfo | grep RAM"
    const val MEMINFO_FALLBACK = "cat /proc/meminfo"
    const val STORAGE = "df /data"
    const val DEVICE_INFO = "getprop ro.product.model; getprop ro.build.version.release; getprop ro.product.device"

    fun isValidPackageName(name: String): Boolean = name.length in 1..255 && PACKAGE_NAME.matches(name)

    /** Returns the command for [action] on [packageName], or throws if it is invalid or protected. */
    fun build(action: PkgAction, packageName: String): String {
        if (!isValidPackageName(packageName)) throw InvalidPackageNameException(packageName)
        ProtectedGuard.check(action, packageName)
        return when (action) {
            PkgAction.DISABLE -> "pm disable-user --user 0 $packageName"
            PkgAction.UNINSTALL_USER -> "pm uninstall -k --user 0 $packageName"
            PkgAction.ENABLE -> "pm enable $packageName"
            PkgAction.INSTALL_EXISTING -> "cmd package install-existing $packageName"
        }
    }

    /** Safe variant for UI previews: null instead of an exception. */
    fun buildOrNull(action: PkgAction, packageName: String): String? =
        runCatching { build(action, packageName) }.getOrNull()

    /**
     * Actions that put a package back into [previousState] after [action] was applied.
     * Empty when nothing needs to be done.
     */
    fun undoActions(action: PkgAction, previousState: PkgState): List<PkgAction> = when (action) {
        PkgAction.DISABLE -> when (previousState) {
            PkgState.DISABLED -> emptyList()
            else -> listOf(PkgAction.ENABLE)
        }
        PkgAction.UNINSTALL_USER -> when (previousState) {
            PkgState.DISABLED -> listOf(PkgAction.INSTALL_EXISTING, PkgAction.DISABLE)
            PkgState.UNINSTALLED -> emptyList()
            else -> listOf(PkgAction.INSTALL_EXISTING, PkgAction.ENABLE)
        }
        PkgAction.ENABLE -> when (previousState) {
            PkgState.DISABLED -> listOf(PkgAction.DISABLE)
            else -> emptyList()
        }
        PkgAction.INSTALL_EXISTING -> when (previousState) {
            PkgState.UNINSTALLED -> listOf(PkgAction.UNINSTALL_USER)
            else -> emptyList()
        }
    }

    /** Actions the panic button uses to bring a package back to enabled, based on its current state. */
    fun panicActions(currentState: PkgState): List<PkgAction> = when (currentState) {
        PkgState.ENABLED -> emptyList()
        PkgState.DISABLED -> listOf(PkgAction.ENABLE)
        PkgState.UNINSTALLED, PkgState.UNKNOWN -> listOf(PkgAction.INSTALL_EXISTING, PkgAction.ENABLE)
    }

    /** The restore action for a package that is currently disabled or removed. */
    fun restoreAction(currentState: PkgState): PkgAction? = when (currentState) {
        PkgState.DISABLED -> PkgAction.ENABLE
        PkgState.UNINSTALLED -> PkgAction.INSTALL_EXISTING
        else -> null
    }

    private val FAILURE_MARKERS = listOf(
        "error", "exception", "failure", "failed", "unknown package", "not found",
        "is not installed", "doesn't exist", "permission denial", "securityexception",
    )

    /** Interprets the raw output of a `pm`/`cmd package` command. */
    fun isSuccess(action: PkgAction, exitCode: Int, output: String): Boolean {
        if (exitCode != 0) return false
        val o = output.lowercase()
        if (FAILURE_MARKERS.any { o.contains(it) }) return false
        return when (action) {
            PkgAction.DISABLE -> o.contains("new state") && o.contains("disabled")
            PkgAction.ENABLE -> o.contains("new state") && o.contains("enabled")
            PkgAction.UNINSTALL_USER -> o.contains("success")
            PkgAction.INSTALL_EXISTING -> o.contains("installed for user") || o.contains("success")
        }
    }
}
