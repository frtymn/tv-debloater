package com.frtymn.tvdebloater.e2e

import com.frtymn.tvdebloater.adb.AdbError
import com.frtymn.tvdebloater.adb.AdbErrorKind
import com.frtymn.tvdebloater.adb.AdbManager
import com.frtymn.tvdebloater.core.CommandBuilder
import com.frtymn.tvdebloater.core.PackageListParser
import com.frtymn.tvdebloater.core.PkgAction
import com.frtymn.tvdebloater.core.PkgState
import com.frtymn.tvdebloater.core.ProtectedPackageException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Real end-to-end test against an Android emulator's adbd (localhost:5555), run by
 * .github/workflows/e2e.yml. Skipped in normal unit-test runs (E2E_ADB not set).
 */
class EmulatorE2ETest {

    private val enabled = System.getenv("E2E_ADB") == "1"
    private val host = System.getenv("E2E_ADB_HOST") ?: "localhost"
    private val port = System.getenv("E2E_ADB_PORT")?.toIntOrNull() ?: 5555

    /** A files dir that holds the host adb key, which the emulator already trusts. */
    private fun trustedFilesDir(): File {
        val dir = Files.createTempDirectory("tvd-e2e").toFile()
        val adbDir = File(dir, "adb").apply { mkdirs() }
        val userHome = System.getProperty("user.home") ?: ""
        val candidates = listOfNotNull(
            System.getenv("E2E_ADB_KEY_DIR"),
            System.getenv("ANDROID_USER_HOME"),
            System.getenv("ANDROID_SDK_HOME")?.let { "$it/.android" },
            System.getenv("ANDROID_PREFS_ROOT")?.let { "$it/.android" },
            "$userHome/.android",
        ).map(::File)
        val home = candidates.firstOrNull { File(it, "adbkey").exists() }
            ?: throw AssertionError("host adb key not found in $candidates")
        File(home, "adbkey").copyTo(File(adbDir, "adbkey"))
        File(home, "adbkey.pub").takeIf { it.exists() }?.copyTo(File(adbDir, "adbkey.pub"))
        return dir
    }

    private suspend fun states(adb: AdbManager): Map<String, PkgState> = PackageListParser.buildStates(
        adb.shell(CommandBuilder.LIST_SYSTEM).output,
        adb.shell(CommandBuilder.LIST_DISABLED).output,
        adb.shell(CommandBuilder.LIST_SYSTEM_INCLUDING_UNINSTALLED).output,
    )

    private suspend fun run(adb: AdbManager, action: PkgAction, pkg: String) {
        val cmd = CommandBuilder.build(action, pkg)
        val r = adb.shell(cmd)
        println("E2E $ $cmd -> exit=${r.exitCode} out='${r.output}'")
        assertTrue("$cmd failed: ${r.output}", CommandBuilder.isSuccess(action, r.exitCode, r.output))
    }

    @Test
    fun fullFlowOnEmulator() = runBlocking {
        assumeTrue("E2E_ADB not set", enabled)
        val adb = AdbManager(trustedFilesDir())
        val info = adb.connect(host, port)
        println("E2E connected: $info")
        assertTrue(info.androidVersion.isNotBlank())

        val before = states(adb)
        println("E2E packages: ${before.size} system, ${before.count { it.value == PkgState.DISABLED }} disabled")
        assertTrue(before.size > 20)
        assertEquals(PkgState.ENABLED, before["android"])

        val memRaw = adb.shell(CommandBuilder.MEMINFO).output
        println("E2E meminfo: $memRaw")
        val mem = PackageListParser.parseMemInfo(memRaw) ?: PackageListParser.parseMemInfo(adb.shell(CommandBuilder.MEMINFO_FALLBACK).output)
        assertNotNull("meminfo not parsed", mem)
        val dfRaw = adb.shell(CommandBuilder.STORAGE).output
        println("E2E df: $dfRaw")
        assertNotNull("df not parsed", PackageListParser.parseDf(dfRaw))

        // Guard: protected packages never reach the device.
        try { CommandBuilder.build(PkgAction.DISABLE, "com.android.systemui"); throw AssertionError("guard failed") } catch (_: ProtectedPackageException) { }

        val candidates = listOf(
            "com.android.printspooler", "com.android.htmlviewer", "com.android.dreams.basic",
            "com.android.wallpaperbackup", "com.android.providers.userdictionary", "com.android.backupconfirm",
        )
        val target = candidates.firstOrNull { before[it] == PkgState.ENABLED }
        assertNotNull("no candidate package on this image", target)
        target!!
        println("E2E target: $target")

        run(adb, PkgAction.DISABLE, target)
        assertEquals(PkgState.DISABLED, states(adb)[target])
        run(adb, PkgAction.ENABLE, target)
        assertEquals(PkgState.ENABLED, states(adb)[target])

        run(adb, PkgAction.UNINSTALL_USER, target)
        assertEquals(PkgState.UNINSTALLED, states(adb)[target])
        run(adb, PkgAction.INSTALL_EXISTING, target)
        assertEquals(PkgState.ENABLED, states(adb)[target])

        // Undo plan for an uninstall of an enabled package brings it back exactly.
        assertEquals(listOf(PkgAction.INSTALL_EXISTING, PkgAction.ENABLE), CommandBuilder.undoActions(PkgAction.UNINSTALL_USER, PkgState.ENABLED))

        // Failure output must be detected, not reported as success.
        val bad = adb.shell(CommandBuilder.build(PkgAction.DISABLE, "com.example.doesnotexist"))
        println("E2E nonexistent -> exit=${bad.exitCode} out='${bad.output}'")
        assertTrue(!CommandBuilder.isSuccess(PkgAction.DISABLE, bad.exitCode, bad.output))
        val badRestore = adb.shell(CommandBuilder.build(PkgAction.INSTALL_EXISTING, "com.example.doesnotexist"))
        println("E2E nonexistent install-existing -> exit=${badRestore.exitCode} out='${badRestore.output}'")
        assertTrue(!CommandBuilder.isSuccess(PkgAction.INSTALL_EXISTING, badRestore.exitCode, badRestore.output))

        val secure = adb.shell("getprop ro.adb.secure").output.trim()
        adb.disconnect()

        // Closed port -> REFUSED.
        try {
            AdbManager(trustedFilesDir()).connect(host, 5599); throw AssertionError("connected to a closed port")
        } catch (e: AdbError) {
            println("E2E closed port -> ${e.kind}: ${e.detail}")
            assertEquals(AdbErrorKind.REFUSED, e.kind)
        }

        // Fresh (untrusted) key: the device must ask for authorisation; nobody accepts it here.
        val fresh = Files.createTempDirectory("tvd-e2e-fresh").toFile()
        try {
            val i = AdbManager(fresh, authTimeoutMs = 8_000).connect(host, port)
            println("E2E fresh key connected (ro.adb.secure=$secure): $i")
            assertTrue("untrusted key accepted although adb is secure", secure != "1")
        } catch (e: AdbError) {
            println("E2E fresh key -> ${e.kind}: ${e.detail} (ro.adb.secure=$secure)")
            assertTrue(e.kind == AdbErrorKind.AUTH_TIMEOUT || e.kind == AdbErrorKind.UNAUTHORIZED)
        }
    }
}
