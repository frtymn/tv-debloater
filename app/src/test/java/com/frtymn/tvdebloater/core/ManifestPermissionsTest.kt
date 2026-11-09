package com.frtymn.tvdebloater.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The permission set is part of the privacy promise in the READMEs ("Privacy and permissions").
 * If this test fails, update both READMEs in the same change.
 */
class ManifestPermissionsTest {

    @Test
    fun manifestDeclaresOnlyTheDocumentedPermissions() {
        val manifest = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .map(::File).first { it.exists() }.readText()
        val permissions = Regex("""uses-permission[^>]*android:name="android\.permission\.([A-Z_]+)"""")
            .findAll(manifest).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("INTERNET", "ACCESS_NETWORK_STATE"), permissions)
    }
}
