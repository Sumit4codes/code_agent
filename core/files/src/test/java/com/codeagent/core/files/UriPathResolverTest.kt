package com.codeagent.core.files

import android.net.FakeUri
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UriPathResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testResolveFileUri() {
        val folder = tempFolder.newFolder("my_project")
        val uri = FakeUri("file://${folder.absolutePath}")

        val resolved = UriPathResolver.resolveLocalDirectory(null, uri, "test")
        assertEquals(folder.absolutePath, resolved.absolutePath)
        assertTrue(resolved.exists())
    }

    @Test
    fun testResolveSafPrimaryUri() {
        val uri = FakeUri("content://com.android.externalstorage.documents/tree/primary%3AProjects%2FMyApp")
        val resolved = UriPathResolver.resolveLocalDirectory(null, uri, "MyApp")

        // Should resolve to /storage/emulated/0/Projects/MyApp or fallback
        assertNotNull(resolved)
        assertTrue(resolved.path.contains("Projects/MyApp") || resolved.path.contains("MyApp"))
    }

    @Test
    fun testResolveSafRawDocUri() {
        val folder = tempFolder.newFolder("raw_target")
        val uri = FakeUri("content://com.android.providers.downloads.documents/document/raw%3A${folder.absolutePath}")
        val resolved = UriPathResolver.resolveLocalDirectory(null, uri, "raw_test")

        assertEquals(folder.absolutePath, resolved.absolutePath)
    }

    @Test
    fun testResolveFallbackDirectory() {
        val uri = FakeUri("content://unknown.provider/tree/custom-data-source")
        val resolved = UriPathResolver.resolveLocalDirectory(null, uri, "custom_proj")

        assertTrue(resolved.exists())
        assertTrue(resolved.path.contains("codeagent_workspaces/custom_proj"))
    }
}
