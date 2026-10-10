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

class UbuntuBootstrapManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var manager: UbuntuBootstrapManager

    @Before
    fun setup() {
        rootDir = tempFolder.newFolder("terminal_root")
        manager = UbuntuBootstrapManager(rootDir)
    }

    @Test
    fun isInstalled_returnsFalse_whenNotConfigured() {
        assertFalse(manager.isInstalled())
    }

    @Test
    fun isInstalled_returnsTrue_whenMarkerAndBashExist() {
        val ubuntuDir = File(rootDir, "ubuntu")
        val binDir = File(ubuntuDir, "bin")
        binDir.mkdirs()
        File(binDir, "bash").createNewFile()
        File(rootDir, ".ubuntu_configured").createNewFile()

        assertTrue(manager.isInstalled())
    }

    @Test
    fun architectureResolution_resolvesKnownAbis() {
        val arch = manager.resolveArchitecture()
        assertTrue(arch.detectedAbi.isNotBlank())
        assertTrue(arch.ubuntuArch.isNotBlank())
        assertTrue(arch.filename.endsWith(".tar.gz"))
        assertTrue(arch.repoUrl.startsWith("http"))
    }

    @Test
    fun extractTarGz_extractsFilesAndDirectoriesCorrectly() = runTest {
        val byteOut = ByteArrayOutputStream()
        GZIPOutputStream(byteOut).use { gzOut ->
            TarArchiveOutputStream(gzOut).use { tarOut ->
                // Add directory
                val dirEntry = TarArchiveEntry("etc/")
                tarOut.putArchiveEntry(dirEntry)
                tarOut.closeArchiveEntry()

                // Add a text file
                val fileContent = "hello ubuntu linux".toByteArray()
                val fileEntry = TarArchiveEntry("etc/issue").apply {
                    size = fileContent.size.toLong()
                }
                tarOut.putArchiveEntry(fileEntry)
                tarOut.write(fileContent)
                tarOut.closeArchiveEntry()

                // Add an executable file
                val execContent = "#!/bin/bash\necho ubuntu".toByteArray()
                val execEntry = TarArchiveEntry("bin/bash").apply {
                    size = execContent.size.toLong()
                    mode = 0b111_101_101 // rwxr-xr-x
                }
                tarOut.putArchiveEntry(execEntry)
                tarOut.write(execContent)
                tarOut.closeArchiveEntry()
            }
        }

        val targetDir = File(rootDir, "ubuntu_test")
        targetDir.mkdirs()

        val inStream = ByteArrayInputStream(byteOut.toByteArray())
        manager.extractTarGz(inStream, targetDir)

        val issueFile = File(targetDir, "etc/issue")
        assertTrue(issueFile.exists())
        assertEquals("hello ubuntu linux", issueFile.readText())

        val bashFile = File(targetDir, "bin/bash")
        assertTrue(bashFile.exists())
        assertTrue(bashFile.canExecute())
    }

    @Test
    fun configureUbuntuEnvironment_configuresResolvPolicyAndApt() {
        val ubuntuDir = File(rootDir, "ubuntu_config_test")
        ubuntuDir.mkdirs()
        val arch = manager.resolveArchitecture()

        manager.configureUbuntuEnvironment(ubuntuDir, arch)

        // 1. resolv.conf
        val resolv = File(ubuntuDir, "etc/resolv.conf")
        assertTrue(resolv.exists())
        assertTrue(resolv.readText().contains("nameserver 8.8.8.8"))

        // 2. hosts
        val hosts = File(ubuntuDir, "etc/hosts")
        assertTrue(hosts.exists())
        assertTrue(hosts.readText().contains("127.0.0.1 localhost"))

        // 3. policy-rc.d
        val policyRc = File(ubuntuDir, "usr/sbin/policy-rc.d")
        assertTrue(policyRc.exists())
        assertTrue(policyRc.readText().contains("exit 101"))
        assertTrue(policyRc.canExecute())

        // 4. apt.conf.d/99codeagent
        val aptConf = File(ubuntuDir, "etc/apt/apt.conf.d/99codeagent")
        assertTrue(aptConf.exists())
        assertTrue(aptConf.readText().contains("Install-Recommends \"false\""))

        // 5. sources.list
        val sourcesList = File(ubuntuDir, "etc/apt/sources.list")
        assertTrue(sourcesList.exists())
        assertTrue(sourcesList.readText().contains("noble"))

        // 6. .bashrc
        val bashrc = File(ubuntuDir, "root/.bashrc")
        assertTrue(bashrc.exists())
        assertTrue(bashrc.readText().contains("codeagent@ubuntu"))
    }

    @Test
    fun ensureGitConfigured_createsRootGitConfig() {
        manager.ensureGitConfigured()

        val userConfig = File(manager.ubuntuDir, "root/.gitconfig")
        assertTrue(userConfig.exists())
        val userText = userConfig.readText()
        assertTrue(userText.contains("directory = *"))
        assertTrue(userText.contains("filemode = false"))
    }
}
