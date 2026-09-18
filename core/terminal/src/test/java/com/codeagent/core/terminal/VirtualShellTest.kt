package com.codeagent.core.terminal

import android.net.FakeUri
import com.codeagent.core.git.JGitOperations
import com.codeagent.core.testing.FakeProjectFileSystem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VirtualShellTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fileSystem: FakeProjectFileSystem
    private val rootUri = FakeUri("content://test/root")
    private lateinit var shell: VirtualShell

    @Before
    fun setup() {
        fileSystem = FakeProjectFileSystem("content://test/root")
        fileSystem.putFile("README.md", "# Hello World\nWelcome to testing.")
        fileSystem.putFile("src/main.kt", "fun main() {\n    println(\"hi\")\n}")
        fileSystem.putFile("build.gradle.kts", "plugins { kotlin(\"jvm\") }")
        shell = VirtualShell(fileSystem, rootUri, "test_proj")
    }

    @Test
    fun testCommandParserQuotesAndFlags() {
        val cmd1 = CommandParser.parse("ls -la src")
        assertNotNull(cmd1)
        assertEquals("ls", cmd1!!.executable)
        assertTrue(cmd1.flags.contains('l'))
        assertTrue(cmd1.flags.contains('a'))
        assertEquals(listOf("src"), cmd1.args)

        val cmd2 = CommandParser.parse("git commit -m \"Initial commit message\"")
        assertNotNull(cmd2)
        assertEquals("git", cmd2!!.executable)
        assertEquals(listOf("commit", "Initial commit message"), cmd2.args)

        val cmd3 = CommandParser.parse("grep -rn 'fun main' src")
        assertNotNull(cmd3)
        assertEquals("grep", cmd3!!.executable)
        assertTrue(cmd3.flags.contains('r'))
        assertTrue(cmd3.flags.contains('n'))
        assertEquals(listOf("fun main", "src"), cmd3.args)
    }

    @Test
    fun testLsCommand() = runTest {
        val res = shell.execute(CommandParser.parse("ls")!!)
        assertTrue(res is TerminalResult.Success)
        val out = (res as TerminalResult.Success).output
        assertTrue(out.contains("README.md"))
        assertTrue(out.contains("src"))
    }

    @Test
    fun testCatCommand() = runTest {
        val res = shell.execute(CommandParser.parse("cat README.md")!!)
        assertTrue(res is TerminalResult.Success)
        val out = (res as TerminalResult.Success).output
        assertTrue(out.contains("# Hello World"))

        val resLines = shell.execute(CommandParser.parse("cat -n README.md")!!)
        assertTrue(resLines is TerminalResult.Success)
        val outLines = (resLines as TerminalResult.Success).output
        assertTrue(outLines.contains("1  # Hello World"))
    }

    @Test
    fun testHeadAndTail() = runTest {
        fileSystem.putFile("lines.txt", "1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12")
        val headRes = shell.execute(CommandParser.parse("head 3 lines.txt")!!)
        assertTrue(headRes is TerminalResult.Success)
        assertEquals("1\n2\n3", (headRes as TerminalResult.Success).output)

        val tailRes = shell.execute(CommandParser.parse("tail 2 lines.txt")!!)
        assertTrue(tailRes is TerminalResult.Success)
        assertEquals("11\n12", (tailRes as TerminalResult.Success).output)
    }

    @Test
    fun testWcCommand() = runTest {
        val res = shell.execute(CommandParser.parse("wc -l README.md")!!)
        assertTrue(res is TerminalResult.Success)
        assertEquals("2 README.md", (res as TerminalResult.Success).output)
    }

    @Test
    fun testGrepCommand() = runTest {
        val res = shell.execute(CommandParser.parse("grep -rn 'println' src")!!)
        assertTrue(res is TerminalResult.Success)
        val out = (res as TerminalResult.Success).output
        assertTrue(out.contains("println(\"hi\")"))
    }

    @Test
    fun testFindCommand() = runTest {
        val res = shell.execute(CommandParser.parse("find . -name *.kt")!!)
        assertTrue(res is TerminalResult.Success)
        val out = (res as TerminalResult.Success).output
        assertTrue(out.contains("src/main.kt"))
    }

    @Test
    fun testPwdAndEcho() = runTest {
        val pwdRes = shell.execute(CommandParser.parse("pwd")!!)
        assertTrue(pwdRes is TerminalResult.Success)
        assertEquals("/test_proj", (pwdRes as TerminalResult.Success).output)

        val echoRes = shell.execute(CommandParser.parse("echo Hello Android")!!)
        assertTrue(echoRes is TerminalResult.Success)
        assertEquals("Hello Android", (echoRes as TerminalResult.Success).output)
    }

    @Test
    fun testMkdirAndTouch() = runTest {
        val mkRes = shell.execute(CommandParser.parse("mkdir testdir")!!)
        assertEquals(0, (mkRes as TerminalResult.Success).exitCode)

        val touchRes = shell.execute(CommandParser.parse("touch testdir/file.txt")!!)
        assertEquals(0, (touchRes as TerminalResult.Success).exitCode)

        val lsRes = shell.execute(CommandParser.parse("ls testdir")!!)
        assertTrue((lsRes as TerminalResult.Success).output.contains("file.txt"))
    }

    @Test
    fun testDefaultTerminalExecutorWithGit() = runTest {
        val localDir = tempFolder.newFolder("local_repo")
        val gitOps = JGitOperations()
        gitOps.initRepo(localDir)
        File(localDir, "app.kt").writeText("val x = 1")

        val executor = DefaultTerminalExecutor(gitOps)
        executor.bind(fileSystem, rootUri, localDir)

        val gitStatus = executor.execute("git status")
        assertTrue(gitStatus is TerminalResult.Success)
        assertTrue((gitStatus as TerminalResult.Success).output.contains("app.kt"))

        val gitAdd = executor.execute("git add .")
        assertEquals(0, (gitAdd as TerminalResult.Success).exitCode)

        val gitCommit = executor.execute("git commit -m 'Initial commit'")
        assertTrue(gitCommit is TerminalResult.Success)
        assertTrue((gitCommit as TerminalResult.Success).output.contains("Initial commit"))

        val vfsLs = executor.execute("ls")
        assertTrue(vfsLs is TerminalResult.Success)
        assertTrue((vfsLs as TerminalResult.Success).output.contains("README.md"))
    }

    @Test
    fun testDefaultTerminalExecutorSafFallbackWithGit() = runTest {
        val gitOps = JGitOperations()
        val executor = DefaultTerminalExecutor(gitOps)
        // Bind without explicit localDir, only SAF content Uri
        val safUri = FakeUri("content://com.android.externalstorage.documents/tree/primary%3AProjects%2FSampleProject")
        executor.bind(fileSystem, safUri, null)

        val gitInit = executor.execute("git init")
        assertTrue(gitInit is TerminalResult.Success)
        assertTrue((gitInit as TerminalResult.Success).output.contains("Initialized empty Git repository"))

        val gitStatus = executor.execute("git status")
        assertTrue(gitStatus is TerminalResult.Success)
        assertTrue((gitStatus as TerminalResult.Success).output.contains("nothing to commit"))
    }
}
