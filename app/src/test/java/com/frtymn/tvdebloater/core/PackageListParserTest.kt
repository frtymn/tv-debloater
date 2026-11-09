package com.frtymn.tvdebloater.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageListParserTest {

    @Test
    fun parsesPlainList() {
        val out = "package:android\r\npackage:com.miui.tv.analytics\r\n\r\npackage:com.google.android.gms\n"
        assertEquals(setOf("android", "com.miui.tv.analytics", "com.google.android.gms"), PackageListParser.parsePackages(out))
    }

    @Test
    fun parsesPathForm() {
        val out = "package:/system/priv-app/MiTVAnalytics/MiTVAnalytics.apk=com.miui.tv.analytics\n" +
            "package:/data/app/com.a.b-xyz==/base.apk=com.a.b"
        assertEquals(setOf("com.miui.tv.analytics", "com.a.b"), PackageListParser.parsePackages(out))
    }

    @Test
    fun ignoresJunkAndInvalidNames() {
        val out = "WARNING: linker: something\npackage:\npackage:bad name\npackage:com.ok\nError: foo"
        assertEquals(setOf("com.ok"), PackageListParser.parsePackages(out))
        assertTrue(PackageListParser.parsePackages("").isEmpty())
    }

    @Test
    fun buildsStates() {
        val states = PackageListParser.buildStates(
            systemOutput = "package:a.one\npackage:a.two\n",
            disabledOutput = "package:a.two\npackage:user.app\n",
            includingUninstalledOutput = "package:a.one\npackage:a.two\npackage:a.three\n",
        )
        assertEquals(PkgState.ENABLED, states["a.one"])
        assertEquals(PkgState.DISABLED, states["a.two"])
        assertEquals(PkgState.UNINSTALLED, states["a.three"])
        assertNull(states["user.app"]) // not a system package
    }

    @Test
    fun parsesDumpsysMeminfo() {
        val out = """
            Total RAM: 1,945,132K (status normal)
             Free RAM:   936,412K (  210,644K cached pss +   612,524K cached kernel +   113,244K free)
             Used RAM:   896,353K (  712,309K used pss +   184,044K kernel)
             Lost RAM:   112,367K
        """.trimIndent()
        val m = PackageListParser.parseMemInfo(out)!!
        assertEquals(1_945_132L, m.totalKb)
        assertEquals(936_412L, m.freeKb)
        assertEquals(896_353L, m.usedKb)
    }

    @Test
    fun parsesProcMeminfoFallback() {
        val out = "MemTotal:         887124 kB\nMemFree:           61424 kB\nMemAvailable:     301100 kB\n"
        val m = PackageListParser.parseMemInfo(out)!!
        assertEquals(887_124L, m.totalKb)
        assertEquals(301_100L, m.freeKb)
        assertNull(PackageListParser.parseMemInfo("nothing here"))
    }

    @Test
    fun parsesToyboxDf() {
        val out = "Filesystem     1K-blocks    Used Available Use% Mounted on\n/dev/block/dm-1   5079888 2207264   2856240  44% /data\n"
        val s = PackageListParser.parseDf(out)!!
        assertEquals(5_079_888L, s.totalKb)
        assertEquals(2_207_264L, s.usedKb)
        assertEquals(2_856_240L, s.availableKb)
    }

    @Test
    fun parsesWrappedAndHumanDf() {
        val wrapped = "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/block/by-name/userdata\n 5079888 2207264 2856240 44% /data"
        assertEquals(2_856_240L, PackageListParser.parseDf(wrapped)!!.availableKb)
        val human = "Filesystem Size Used Avail Use% Mounted on\n/dev/block/dm-1 4.8G 2.1G 2.7G 44% /data"
        val h = PackageListParser.parseDf(human)
        assertNotNull(h)
        assertEquals((2.7 * 1024 * 1024).toLong(), h!!.availableKb)
        assertNull(PackageListParser.parseDf(""))
    }
}
