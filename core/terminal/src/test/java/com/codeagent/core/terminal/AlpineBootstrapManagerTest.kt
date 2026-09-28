package com.codeagent.core.terminal

import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

class AlpineBootstrapManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var manager: AlpineBootstrapManager

    @Before
    fun setup() {
        rootDir = tempFolder.newFolder("terminal_root")
        manager = AlpineBootstrapManager(rootDir)
    }

    @Test
    fun isInstalled_returnsFalse_whenNotConfigured() {
        assertFalse(manager.isInstalled())
    }

    @Test
    fun isInstalled_returnsTrue_whenMarkerAndShExist() {
        val alpineDir = File(rootDir, "alpine")
        val binDir = File(alpineDir, "bin")
        binDir.mkdirs()
        File(binDir, "sh").createNewFile()
        File(rootDir, ".configured").createNewFile()

        assertTrue(manager.isInstalled())
    }

    @Test
    fun architectureResolution_resolvesKnownAbis() {
        val arch = manager.resolveArchitecture()
        assertTrue(arch.detectedAbi.isNotBlank())
        assertTrue(arch.assetDir.isNotBlank())
        assertTrue(arch.alpineArch.isNotBlank())
        assertTrue(arch.filename.endsWith(".tar.gz"))
    }

    @Test
    fun extractTarGz_extractsFilesAndDirectoriesCorrectly() = runTest {
        // Create an in-memory tar.gz stream
        val byteOut = ByteArrayOutputStream()
        GZIPOutputStream(byteOut).use { gzOut ->
            TarArchiveOutputStream(gzOut).use { tarOut ->
                // Add directory
                val dirEntry = TarArchiveEntry("etc/")
                tarOut.putArchiveEntry(dirEntry)
                tarOut.closeArchiveEntry()

                // Add a text file
                val fileContent = "hello alpine linux".toByteArray()
                val fileEntry = TarArchiveEntry("etc/hello.txt").apply {
                    size = fileContent.size.toLong()
                }
                tarOut.putArchiveEntry(fileEntry)
                tarOut.write(fileContent)
                tarOut.closeArchiveEntry()

                // Add an executable file
                val execContent = "#!/bin/sh\necho hi".toByteArray()
                val execEntry = TarArchiveEntry("bin/mycmd").apply {
                    size = execContent.size.toLong()
                    mode = 0b111_101_101 // rwxr-xr-x
                }
                tarOut.putArchiveEntry(execEntry)
                tarOut.write(execContent)
                tarOut.closeArchiveEntry()
            }
        }

        val targetDir = File(rootDir, "alpine_test")
        targetDir.mkdirs()

        val inStream = ByteArrayInputStream(byteOut.toByteArray())
        manager.extractTarGz(inStream, targetDir)

        val helloFile = File(targetDir, "etc/hello.txt")
        assertTrue(helloFile.exists())
        assertEquals("hello alpine linux", helloFile.readText())

        val cmdFile = File(targetDir, "bin/mycmd")
        assertTrue(cmdFile.exists())
        assertTrue(cmdFile.canExecute())
    }

    @Test
    fun ensureGitConfigured_createsSystemAndUserGitConfigs() {
        manager.ensureGitConfigured()

        val sysConfig = File(manager.alpineDir, "etc/gitconfig")
        assertTrue(sysConfig.exists())
        val sysText = sysConfig.readText()
        assertTrue(sysText.contains("createObject = rename"))
        assertTrue(sysText.contains("filemode = false"))
        assertTrue(sysText.contains("symlinks = false"))
        assertTrue(sysText.contains("directory = *"))

        val userConfig = File(manager.publicDir, ".gitconfig")
        assertTrue(userConfig.exists())
        val userText = userConfig.readText()
        assertTrue(userText.contains("createObject = rename"))
        assertTrue(userText.contains("filemode = false"))
        assertTrue(userText.contains("symlinks = false"))
        assertTrue(userText.contains("directory = *"))
    }

    @Test
    fun ensureGitConfigured_isIdempotent() {
        manager.ensureGitConfigured()
        val sysConfig = File(manager.alpineDir, "etc/gitconfig")
        val initialContent = sysConfig.readText()

        // Call again
        manager.ensureGitConfigured()
        assertEquals(initialContent, sysConfig.readText())
    }

    @Test
    fun setupSupportingLibraries_createsBothTallocLinks() {
        val fakeNativeDir = tempFolder.newFolder("nativeLibDir")
        val tallocSource = File(fakeNativeDir, "libtalloc.so")
        tallocSource.writeText("fake talloc library content")

        manager.customNativeLibraryDir = fakeNativeDir
        manager.setupSupportingLibraries()

        val tallocLink2 = File(rootDir, "libtalloc.so.2")
        val tallocLink = File(rootDir, "libtalloc.so")
        assertTrue(tallocLink2.exists())
        assertTrue(tallocLink.exists())
    }
}
