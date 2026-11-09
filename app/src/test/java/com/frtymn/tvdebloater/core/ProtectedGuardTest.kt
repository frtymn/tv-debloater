package com.frtymn.tvdebloater.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProtectedGuardTest {

    private val required = listOf(
        "android",
        "com.google.android.tvlauncher",          // launcher
        "com.google.android.apps.tv.launcherx",
        "com.android.vending",                    // Play Store
        "com.google.android.gms",                 // Play Services
        "com.google.android.gms.policy_sidecar_aps",
        "com.google.android.gsf",
        "com.google.android.tungsten.setupwraith",// setup wizard
        "com.google.android.packageinstaller",
        "com.android.systemui",
        "com.android.tv.settings",
        "com.android.providers.settings",
        "com.android.bluetooth",
        "com.droidlogic.ble",                     // BLE remote
        "com.google.android.tv.remote.service",
        "com.google.android.inputmethod.latin",
        "com.google.android.leanback.ime",
        "com.android.shell",                      // ADB
        "com.android.captiveportallogin",
        "com.xiaomi.android.tvsetup.partnercustomizer",
        "com.xiaomi.mitv.updateservice",
        "com.example.someremotecontrol",
        "com.vendor.wifi.helper",
        "com.vendor.bluetooth.midi",
    )

    @Test
    fun requiredPackagesAreProtected() {
        for (p in required) assertTrue("$p must be protected", ProtectedGuard.isProtected(p))
    }

    @Test
    fun guardIsCaseAndWhitespaceInsensitive() {
        assertTrue(ProtectedGuard.isProtected("  COM.ANDROID.VENDING "))
        assertTrue(ProtectedGuard.isProtected(""))
    }

    @Test
    fun ordinaryPackagesAreNotProtected() {
        assertFalse(ProtectedGuard.isProtected("com.miui.tv.analytics"))
        assertFalse(ProtectedGuard.isProtected("tv.alphonso.alphonso_eula"))
        assertFalse(ProtectedGuard.isProtected("com.dazn"))
    }

    @Test
    fun checkBlocksOnlyDestructiveActions() {
        try {
            ProtectedGuard.check(PkgAction.DISABLE, "com.android.vending"); throw AssertionError("not blocked")
        } catch (_: ProtectedPackageException) { }
        try {
            ProtectedGuard.check(PkgAction.UNINSTALL_USER, "com.google.android.gms"); throw AssertionError("not blocked")
        } catch (_: ProtectedPackageException) { }
        ProtectedGuard.check(PkgAction.ENABLE, "com.android.vending")
        ProtectedGuard.check(PkgAction.INSTALL_EXISTING, "com.android.vending")
    }

    /** Even if packages.json is edited to say SAFE, the code guard wins. */
    @Test
    fun guardOverridesEditedJson() {
        val db = PackageDatabase(packages = listOf(PackageEntry("com.google.android.tvlauncher", SafetyLevel.SAFE)))
        assertEquals(SafetyLevel.PROTECTED, Classifier(db).classify("com.google.android.tvlauncher").level)
    }

    @Test
    fun unknownPackagesAreCautionOrProtected() {
        val c = Classifier(PackageDatabase())
        assertEquals(SafetyLevel.CAUTION, c.classify("com.vendor.something").level)
        assertTrue(c.classify("com.vendor.something").unknown)
        assertEquals(SafetyLevel.PROTECTED, c.classify("com.android.unknowncore").level)
    }

    /** The bundled database must agree with the code guard. */
    @Test
    fun bundledDatabaseIsConsistent() {
        val file = listOf("../db/packages.json", "db/packages.json").map(::File).first { it.exists() }
        val db = Json { ignoreUnknownKeys = true }.decodeFromString(PackageDatabase.serializer(), file.readText())
        assertTrue(db.packages.isNotEmpty())
        assertEquals(db.packages.size, db.packages.map { it.packageName }.toSet().size)
        for (e in db.packages) {
            assertTrue("invalid name ${e.packageName}", CommandBuilder.isValidPackageName(e.packageName))
            if (e.level == SafetyLevel.SAFE) {
                assertFalse("${e.packageName} is SAFE but guarded", ProtectedGuard.isProtected(e.packageName))
                assertTrue("${e.packageName} is SAFE without a source", e.sources.isNotEmpty())
            }
            if (ProtectedGuard.isProtected(e.packageName)) {
                assertEquals("${e.packageName} should be PROTECTED in JSON", SafetyLevel.PROTECTED, e.level)
            }
            if (e.aggressive) assertEquals(SafetyLevel.CAUTION, e.level)
            assertTrue(e.descriptionEn.isNotBlank() && e.descriptionTr.isNotBlank())
        }
        for (p in required.filter { it.startsWith("com.google.android.tvlauncher") || it == "com.android.vending" }) {
            assertEquals(SafetyLevel.PROTECTED, db.packages.first { it.packageName == p }.level)
        }
    }
}
