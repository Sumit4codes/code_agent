package com.codeagent.core.git

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JGitOperationsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var gitOps: JGitOperations
    private lateinit var projectDir: File

    @Before
    fun setup() {
        gitOps = JGitOperations()
        projectDir = tempFolder.newFolder("test_repo")
    }

    @Test
    fun testInitRepoAndIsGitRepo() = runTest {
        assertFalse(gitOps.isGitRepo(projectDir))
        val initResult = gitOps.initRepo(projectDir)
        assertEquals(0, initResult.exitCode)
        assertTrue(initResult.output.contains("Initialized empty Git repository"))
        assertTrue(gitOps.isGitRepo(projectDir))
    }

    @Test
    fun testStatusOnCleanAndModifiedRepo() = runTest {
        gitOps.initRepo(projectDir)
        val initialStatus = gitOps.status(projectDir)
        assertEquals(0, initialStatus.exitCode)
        assertTrue(initialStatus.output.contains("nothing to commit"))

        val file = File(projectDir, "hello.txt")
        file.writeText("hello world")

        val modifiedStatus = gitOps.status(projectDir)
        assertEquals(0, modifiedStatus.exitCode)
        assertTrue(modifiedStatus.output.contains("hello.txt"))
    }

    @Test
    fun testAddCommitAndLog() = runTest {
        gitOps.initRepo(projectDir)

        val file = File(projectDir, "test.txt")
        file.writeText("first commit text")

        val addResult = gitOps.add(projectDir, "test.txt")
        assertEquals(0, addResult.exitCode)

        val commitResult = gitOps.commit(projectDir, "Initial commit message")
        assertEquals(0, commitResult.exitCode)
        assertTrue(commitResult.output.contains("Initial commit message"))

        val logResult = gitOps.log(projectDir, 5)
        assertEquals(0, logResult.exitCode)
        assertTrue(logResult.output.contains("Initial commit message"))
        assertTrue(logResult.output.contains("Author: CodeAgent"))
    }

    @Test
    fun testBranchAndCheckout() = runTest {
        gitOps.initRepo(projectDir)
        File(projectDir, "a.txt").writeText("a")
        gitOps.add(projectDir, ".")
        gitOps.commit(projectDir, "c1")

        val branchResult = gitOps.checkout(projectDir, "feature-branch", createNewBranch = true)
        assertEquals(0, branchResult.exitCode)
        assertTrue(branchResult.output.contains("feature-branch"))

        val branchList = gitOps.branch(projectDir)
        assertEquals(0, branchList.exitCode)
        assertTrue(branchList.output.contains("* feature-branch"))
    }

    @Test
    fun testExecuteGitDispatcher() = runTest {
        gitOps.initRepo(projectDir)
        File(projectDir, "main.kt").writeText("fun main() {}")

        val statusCmd = gitOps.executeGit(projectDir, listOf("status"))
        assertEquals(0, statusCmd.exitCode)
        assertTrue(statusCmd.output.contains("main.kt"))

        val addCmd = gitOps.executeGit(projectDir, listOf("add", "."))
        assertEquals(0, addCmd.exitCode)

        val commitCmd = gitOps.executeGit(projectDir, listOf("commit", "-m", "Add main.kt"))
        assertEquals(0, commitCmd.exitCode)
        assertTrue(commitCmd.output.contains("Add main.kt"))

        val logCmd = gitOps.executeGit(projectDir, listOf("log", "-n", "1"))
        assertEquals(0, logCmd.exitCode)
        assertTrue(logCmd.output.contains("Add main.kt"))

        val versionCmd = gitOps.executeGit(projectDir, listOf("version"))
        assertEquals(0, versionCmd.exitCode)
        assertTrue(versionCmd.output.contains("JGit"))
    }

    @Test
    fun testCloneRepoAndGitCloneCommand() = runTest {
        // Prepare an origin repo
        val originDir = tempFolder.newFolder("origin_repo")
        gitOps.initRepo(originDir)
        val file = File(originDir, "origin.txt")
        file.writeText("hello from origin")
        gitOps.add(originDir, ".")
        gitOps.commit(originDir, "Origin commit")

        // 1. Test cloneRepo API
        val cloneDest = tempFolder.newFolder("cloned_dest")
        val cloneRes = gitOps.cloneRepo(
            workDir = cloneDest,
            repoUrl = originDir.toURI().toString(),
            targetDirName = "sub_clone"
        )
        assertEquals(0, cloneRes.exitCode)
        assertTrue(cloneRes.output.contains("Cloned repository successfully"))
        val clonedFile = File(cloneDest, "sub_clone/origin.txt")
        assertTrue(clonedFile.exists())
        assertEquals("hello from origin", clonedFile.readText())

        // 2. Test executeGit("clone", ...)
        val cliCloneDest = tempFolder.newFolder("cli_clone_dest")
        val cliRes = gitOps.executeGit(
            cliCloneDest,
            listOf("clone", originDir.toURI().toString(), "repo_copy")
        )
        assertEquals(0, cliRes.exitCode)
        assertTrue(File(cliCloneDest, "repo_copy/origin.txt").exists())
    }

    @Test
    fun testRemoteAndReset() = runTest {
        gitOps.initRepo(projectDir)
        File(projectDir, "f1.txt").writeText("v1")
        gitOps.add(projectDir, ".")
        gitOps.commit(projectDir, "v1 commit")

        val remoteAdd = gitOps.executeGit(projectDir, listOf("remote", "add", "origin", "https://github.com/test/repo.git"))
        assertEquals(0, remoteAdd.exitCode)

        val remoteList = gitOps.executeGit(projectDir, listOf("remote", "-v"))
        assertEquals(0, remoteList.exitCode)
        assertTrue(remoteList.output.contains("origin"))
        assertTrue(remoteList.output.contains("https://github.com/test/repo.git"))

        // Test Reset
        File(projectDir, "f1.txt").writeText("dirty change")
        val resetRes = gitOps.executeGit(projectDir, listOf("reset", "--hard", "HEAD"))
        assertEquals(0, resetRes.exitCode)
        assertEquals("v1", File(projectDir, "f1.txt").readText())
    }
}
