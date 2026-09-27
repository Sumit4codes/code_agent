package com.codeagent.core.terminal

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class NativeBinaryManagerTest {

    private lateinit var binaryManager: NativeBinaryManager
    private lateinit var executor: PosixTerminalExecutor

    @Before
    fun setUp() {
        binaryManager = NativeBinaryManager()
        executor = PosixTerminalExecutor()
    }

    @Test
    fun `inspectEnvironment returns non-null shell and architecture`() = runTest {
        val info = binaryManager.inspectEnvironment(executor)
        assertNotNull(info.shellPath)
        assertTrue(info.shellPath.isNotEmpty())
        assertNotNull(info.architecture)
        assertTrue(info.architecture.isNotEmpty())
    }

    @Test
    fun `resolveGitExecutable checks system paths if unbundled`() {
        val gitPath = binaryManager.resolveGitExecutable()
        // If git is installed on host system (e.g. Linux development machine), it resolves
        if (File("/usr/bin/git").exists()) {
            assertNotNull(gitPath)
        }
    }
}
