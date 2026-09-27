package com.codeagent.core.git

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CliGitOperationsTest {

    private lateinit var gitOps: CliGitOperations
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        gitOps = CliGitOperations()
        tempDir = Files.createTempDirectory("cli-git-test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `isGitRepo returns false on non-git directory`() = runTest {
        assertFalse(gitOps.isGitRepo(tempDir))
    }

    @Test
    fun `initRepo, add, commit, status, branch, and log lifecycle`() = runTest {
        // 1. Init
        val initResult = gitOps.initRepo(tempDir)
        assertEquals(0, initResult.exitCode)
        assertTrue(gitOps.isGitRepo(tempDir))

        // 2. Add file
        val testFile = File(tempDir, "hello.txt")
        testFile.writeText("Hello, CodeAgent!\n")

        val addResult = gitOps.add(tempDir, "hello.txt")
        assertEquals(0, addResult.exitCode)

        // 3. Commit
        val commitResult = gitOps.commit(
            workDir = tempDir,
            message = "feat: add hello file",
            authorName = "Tester",
            authorEmail = "test@example.com"
        )
        assertEquals(0, commitResult.exitCode)

        // 4. Status should be clean
        val statusResult = gitOps.status(tempDir)
        assertEquals(0, statusResult.exitCode)
        assertTrue(statusResult.output.contains("working tree clean"))

        // 5. Log
        val logResult = gitOps.log(tempDir)
        assertEquals(0, logResult.exitCode)
        assertTrue(logResult.output.contains("feat: add hello file"))

        // 6. Branch creation
        val checkoutResult = gitOps.checkout(tempDir, "dev-branch", createNewBranch = true)
        assertEquals(0, checkoutResult.exitCode)

        val branchResult = gitOps.branch(tempDir)
        assertEquals(0, branchResult.exitCode)
        assertTrue(branchResult.output.contains("dev-branch"))
    }
}
