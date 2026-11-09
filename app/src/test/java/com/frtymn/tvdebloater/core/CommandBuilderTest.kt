package com.frtymn.tvdebloater.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandBuilderTest {

    @Test
    fun buildsExactCommands() {
        val p = "com.miui.tv.analytics"
        assertEquals("pm disable-user --user 0 $p", CommandBuilder.build(PkgAction.DISABLE, p))
        assertEquals("pm uninstall -k --user 0 $p", CommandBuilder.build(PkgAction.UNINSTALL_USER, p))
        assertEquals("pm enable $p", CommandBuilder.build(PkgAction.ENABLE, p))
        assertEquals("cmd package install-existing $p", CommandBuilder.build(PkgAction.INSTALL_EXISTING, p))
    }

    @Test(expected = ProtectedPackageException::class)
    fun refusesToDisableProtected() {
        CommandBuilder.build(PkgAction.DISABLE, "com.google.android.tvlauncher")
    }

    @Test(expected = ProtectedPackageException::class)
    fun refusesToUninstallProtected() {
        CommandBuilder.build(PkgAction.UNINSTALL_USER, "com.android.vending")
    }

    @Test
    fun restoreIsAllowedForProtected() {
        assertEquals("pm enable com.google.android.tvlauncher", CommandBuilder.build(PkgAction.ENABLE, "com.google.android.tvlauncher"))
        assertEquals(
            "cmd package install-existing com.google.android.gms",
            CommandBuilder.build(PkgAction.INSTALL_EXISTING, "com.google.android.gms"),
        )
    }

    @Test
    fun rejectsShellInjection() {
        val bad = listOf(
            "com.foo; reboot", "com.foo && rm -rf /sdcard", "com.foo|sh", "\$(reboot)", "com.foo`id`",
            "", " ", "com.foo bar", "-f", "com..foo", ".com.foo", "com.foo\nreboot", "1com.foo",
        )
        for (b in bad) {
            assertFalse("should reject '$b'", CommandBuilder.isValidPackageName(b))
            assertNull(CommandBuilder.buildOrNull(PkgAction.DISABLE, b))
            assertNull(CommandBuilder.buildOrNull(PkgAction.ENABLE, b))
        }
    }

    @Test(expected = InvalidPackageNameException::class)
    fun invalidNameThrows() {
        CommandBuilder.build(PkgAction.DISABLE, "com.foo; reboot")
    }

    @Test
    fun undoActionsRestorePreviousState() {
        assertEquals(listOf(PkgAction.ENABLE), CommandBuilder.undoActions(PkgAction.DISABLE, PkgState.ENABLED))
        assertEquals(emptyList<PkgAction>(), CommandBuilder.undoActions(PkgAction.DISABLE, PkgState.DISABLED))
        assertEquals(
            listOf(PkgAction.INSTALL_EXISTING, PkgAction.ENABLE),
            CommandBuilder.undoActions(PkgAction.UNINSTALL_USER, PkgState.ENABLED),
        )
        assertEquals(
            listOf(PkgAction.INSTALL_EXISTING, PkgAction.DISABLE),
            CommandBuilder.undoActions(PkgAction.UNINSTALL_USER, PkgState.DISABLED),
        )
        assertEquals(listOf(PkgAction.DISABLE), CommandBuilder.undoActions(PkgAction.ENABLE, PkgState.DISABLED))
        assertEquals(
            listOf(PkgAction.UNINSTALL_USER),
            CommandBuilder.undoActions(PkgAction.INSTALL_EXISTING, PkgState.UNINSTALLED),
        )
    }

    @Test
    fun panicAndRestoreActions() {
        assertEquals(emptyList<PkgAction>(), CommandBuilder.panicActions(PkgState.ENABLED))
        assertEquals(listOf(PkgAction.ENABLE), CommandBuilder.panicActions(PkgState.DISABLED))
        assertEquals(listOf(PkgAction.INSTALL_EXISTING, PkgAction.ENABLE), CommandBuilder.panicActions(PkgState.UNINSTALLED))
        assertEquals(PkgAction.ENABLE, CommandBuilder.restoreAction(PkgState.DISABLED))
        assertEquals(PkgAction.INSTALL_EXISTING, CommandBuilder.restoreAction(PkgState.UNINSTALLED))
        assertNull(CommandBuilder.restoreAction(PkgState.ENABLED))
    }

    @Test
    fun interpretsPmOutput() {
        assertTrue(CommandBuilder.isSuccess(PkgAction.DISABLE, 0, "Package com.foo new state: disabled-user\n"))
        assertTrue(CommandBuilder.isSuccess(PkgAction.ENABLE, 0, "Package com.foo new state: enabled"))
        assertTrue(CommandBuilder.isSuccess(PkgAction.UNINSTALL_USER, 0, "Success"))
        assertTrue(CommandBuilder.isSuccess(PkgAction.INSTALL_EXISTING, 0, "Package com.foo installed for user: 0"))
        assertFalse(CommandBuilder.isSuccess(PkgAction.DISABLE, 0, "Error: java.lang.SecurityException: Shell cannot change component state"))
        assertFalse(CommandBuilder.isSuccess(PkgAction.UNINSTALL_USER, 0, "Failure [DELETE_FAILED_INTERNAL_ERROR]"))
        assertFalse(CommandBuilder.isSuccess(PkgAction.INSTALL_EXISTING, 0, "Package com.foo doesn't exist"))
        assertFalse(CommandBuilder.isSuccess(PkgAction.ENABLE, 1, "Package com.foo new state: enabled"))
        assertFalse(CommandBuilder.isSuccess(PkgAction.ENABLE, 0, "Package com.foo new state: disabled-user"))
    }
}
