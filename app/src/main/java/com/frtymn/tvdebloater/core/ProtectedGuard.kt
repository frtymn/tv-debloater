package com.frtymn.tvdebloater.core

/**
 * Hard-coded safety net. This list lives in code on purpose: even if assets/packages.json is
 * edited, a package matched here can never be disabled or removed by this app.
 * Restore actions (enable / install-existing) are always allowed, because they only help recovery.
 */
object ProtectedGuard {

    /** Exact package names that must never be disabled or removed. */
    val EXACT: Set<String> = setOf(
        // Android core needed to boot
        "android",
        "com.android.systemui",
        "com.android.shell",                // ADB shell itself
        "com.android.providers.settings",
        "com.android.settings",
        "com.android.tv.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.defcontainer",
        "com.android.externalstorage",
        "com.android.providers.media",
        "com.android.providers.downloads",
        "com.android.providers.tv",
        "com.android.keychain",
        "com.android.certinstaller",
        "com.android.location.fused",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.webview",
        "com.google.android.webview",
        "com.google.android.ext.services",
        "com.google.android.ext.shared",
        "com.android.inputdevices",
        "com.android.server.telecom",
        "com.android.phone",
        // Network / Wi-Fi
        "com.android.networkstack",
        "com.google.android.networkstack",
        "com.android.captiveportallogin",
        "com.android.proxyhandler",
        "com.android.wifi.resources",
        // Launcher / home
        "com.google.android.tvlauncher",
        "com.google.android.leanbacklauncher",
        "com.google.android.apps.tv.launcherx",
        // Google core
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.gsf.login",
        "com.google.android.sss.authbridge",
        // Setup wizard
        "com.google.android.tungsten.setupwraith",
        "com.google.android.setupwizard",
        "com.android.provision",
        // Bluetooth / remote / HID / input methods
        "com.android.bluetooth",
        "com.android.companiondevicemanager",
        "com.google.android.tv.remote.service",
        "com.google.android.inputmethod.latin",
        "com.google.android.leanback.ime",
        "com.android.inputmethod.latin",
        // Xiaomi remote pairing / setup / update / resources
        "com.xiaomi.android.tvsetup.partnercustomizer",
        "com.xiaomi.mitv.updateservice",
        "com.xiaomi.mitv.res",
    )

    /** Package name prefixes that are always protected. */
    val PREFIXES: List<String> = listOf(
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.droidlogic",       // Amlogic platform services (incl. BLE remote)
        "com.amlogic",
        "com.android.bluetooth",
        "com.android.systemui",
        "com.android.providers.settings",
        "com.android.networkstack",
        "com.android.wifi",
    )

    /** Substrings that mark launcher, setup, remote, input, network or update components. */
    val KEYWORDS: List<String> = listOf(
        "launcher",
        "setupwizard",
        "setupwraith",
        "tvsetup",
        "packageinstaller",
        "systemui",
        "bluetooth",
        "inputmethod",
        ".ime",
        "keyboard",
        "remote",
        "rcu",
        "pairing",
        "wifi",
        "networkstack",
        "updateservice",
        "otaupdate",
        "systemupdate",
        "tv.settings",
    )

    fun isProtected(packageName: String): Boolean {
        val p = packageName.trim().lowercase()
        if (p.isEmpty()) return true
        if (p in EXACT) return true
        if (PREFIXES.any { p == it || p.startsWith("$it.") || p.startsWith(it) }) return true
        if (KEYWORDS.any { p.contains(it) }) return true
        return false
    }

    /** Throws if [action] would change a protected package. */
    fun check(action: PkgAction, packageName: String) {
        if (action.isDestructive && isProtected(packageName)) {
            throw ProtectedPackageException(packageName)
        }
    }
}

class ProtectedPackageException(val packageName: String) :
    IllegalArgumentException("Blocked: $packageName is protected and can never be disabled or removed")

class InvalidPackageNameException(val packageName: String) :
    IllegalArgumentException("Invalid package name: '$packageName'")
