package com.codeagent.core.terminal

import android.net.FakeUri
import com.codeagent.core.testing.FakeProjectFileSystem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PosixTerminalExecutorTest {

    private lateinit var executor: PosixTerminalExecutor
    private lateinit var tempDir: File
    private lateinit var fileSystem: FakeProjectFileSystem

    @Before
    fun setUp() {
        executor = PosixTerminalExecutor()
        tempDir = Files.createTempDirectory("posix-term-test").toFile()
        fileSystem = FakeProjectFileSystem()
        executor.bind(fileSystem, FakeUri("file://${tempDir.absolutePath}"), tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `execute simple echo command succeeds`() = runTest {
        val result = executor.execute("echo 'Hello CodeAgent'")
        assertTrue(result is TerminalResult.Success)
        val success = result as TerminalResult.Success
        assertEquals(0, success.exitCode)
        assertEquals("Hello CodeAgent", success.output.trim())
    }

    @Test
    fun `execute compound pipeline command succeeds`() = runTest {
        val cmd = "printf 'alpha\\nbeta\\ngamma\\n' | grep 'beta'"
        val result = executor.execute(cmd)
        assertTrue(result is TerminalResult.Success)
        val success = result as TerminalResult.Success
        assertEquals(0, success.exitCode)
        assertEquals("beta", success.output.trim())
    }

    @Test
    fun `execute file redirection and chaining works in working directory`() = runTest {
        val cmd = "echo 'created by posix' > output.txt && cat output.txt"
        val result = executor.execute(cmd)
        assertTrue(result is TerminalResult.Success)
        val success = result as TerminalResult.Success
        assertEquals(0, success.exitCode)
        assertEquals("created by posix", success.output.trim())
        assertTrue(File(tempDir, "output.txt").exists())
    }

    @Test
    fun `cd command updates persistent working directory`() = runTest {
        val subDir = File(tempDir, "subdir").apply { mkdir() }
        File(subDir, "inner.txt").writeText("inner content")

        // Change directory
        val cdResult = executor.execute("cd subdir")
        assertTrue(cdResult is TerminalResult.Success)
        assertEquals(subDir.canonicalPath, executor.currentWorkingDir?.canonicalPath)

        // Run pwd or ls in new working directory
        val lsResult = executor.execute("ls")
        assertTrue(lsResult is TerminalResult.Success)
        assertTrue((lsResult as TerminalResult.Success).output.contains("inner.txt"))

        // cd back
        val cdBackResult = executor.execute("cd ..")
        assertTrue(cdBackResult is TerminalResult.Success)
        assertEquals(tempDir.canonicalPath, executor.currentWorkingDir?.canonicalPath)
    }

    @Test
    fun `invalid command returns non-zero exit code`() = runTest {
        val result = executor.execute("non_existent_command_xyz_123")
        assertTrue(result is TerminalResult.Error)
        val error = result as TerminalResult.Error
        assertNotNull(error.exitCode)
        assertNotEquals(0, error.exitCode)
    }

    @Test
    fun `streaming callback receives output line by line`() = runTest {
        val lines = mutableListOf<String>()
        val result = executor.execute("echo 'line 1' && echo 'line 2'") { line ->
            lines.add(line)
        }
        assertTrue(result is TerminalResult.Success)
        assertTrue(lines.any { it.contains("line 1") })
        assertTrue(lines.any { it.contains("line 2") })
    }

    @Test
    fun `disabled executor returns Disabled result`() = runTest {
        executor.isEnabled = false
        val result = executor.execute("echo test")
        assertTrue(result is TerminalResult.Disabled)
    }

    @Test
    fun `isAlpineActive returns false when PRoot or Alpine rootfs not ready`() {
        assertFalse(executor.isAlpineActive())
    }

    @Test
    fun `setup-alpine command handles bootstrap execution`() = runTest {
        val root = File(tempDir, "root").apply { mkdirs() }
        val bootstrapManager = AlpineBootstrapManager(root)
        val alpineExecutor = PosixTerminalExecutor(
            context = null,
            nativeBinaryManager = NativeBinaryManager(null, bootstrapManager),
            alpineBootstrapManager = bootstrapManager
        )
        // Without rootfs asset or network, bootstrap returns handled error without crash
        val result = alpineExecutor.execute("setup-alpine")
        assertNotNull(result)
    }

    @Test
    fun `native process receives git config parameters environment`() = runTest {
        val result = executor.execute("echo \"\$GIT_CONFIG_PARAMETERS\"")
        assertTrue(result is TerminalResult.Success)
        val output = (result as TerminalResult.Success).output
        assertTrue(output.contains("core.createObject=rename"))
        assertTrue(output.contains("core.filemode=false"))
        assertTrue(output.contains("core.symlinks=false"))
        assertTrue(output.contains("safe.directory=*"))
    }
}
