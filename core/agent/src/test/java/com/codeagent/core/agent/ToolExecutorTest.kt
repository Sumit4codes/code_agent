package com.codeagent.core.agent

import android.net.FakeUri
import com.codeagent.core.model.ChangeType
import com.codeagent.core.testing.FakeProjectFileSystem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ToolExecutorTest {

    private lateinit var fs: FakeProjectFileSystem
    private lateinit var executor: ToolExecutor
    private lateinit var tempDir: File
    private val rootUri = FakeUri("content://test/root")

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("tool-exec-test").toFile()
        fs = FakeProjectFileSystem("content://test/root")
        executor = ToolExecutor()
        executor.bind(fs, rootUri, tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `execute returns error when unbound`() = runTest {
        executor.unbind()
        val result = executor.execute("read_file", """{"path":"foo.txt"}""")
        assertFalse(result.success)
        assertTrue(result.output.contains("No project is open"))
    }

    @Test
    fun `execute rejects invalid json`() = runTest {
        val result = executor.execute("read_file", "invalid-json")
        assertFalse(result.success)
        assertTrue(result.output.contains("Invalid JSON"))
    }

    @Test
    fun `execute list_files lists directory entries`() = runTest {
        fs.putFile("Main.kt", "fun main() {}")
        fs.putFile("nested/Helper.kt", "class Helper")

        val result = executor.execute("list_files", """{"path":"."}""")
        assertTrue(result.success)
        assertTrue(result.output.contains("Main.kt"))
        assertTrue(result.output.contains("nested"))
    }

    @Test
    fun `execute list_files blocks path traversal`() = runTest {
        val result = executor.execute("list_files", """{"path":"../../etc"}""")
        assertFalse(result.success)
        assertTrue(result.output.contains("Invalid path"))
    }

    @Test
    fun `execute read_file reads whole content and slices lines`() = runTest {
        val lines = (1..10).joinToString("\n") { "line $it" }
        fs.putFile("test.txt", lines)

        val full = executor.execute("read_file", """{"path":"test.txt"}""")
        assertTrue(full.success)
        assertTrue(full.output.contains("line 1"))
        assertTrue(full.output.contains("line 10"))

        val sliced = executor.execute("read_file", """{"path":"test.txt","start_line":3,"end_line":5}""")
        assertTrue(sliced.success)
        assertTrue(sliced.output.contains("line 3"))
        assertTrue(sliced.output.contains("line 5"))
        assertFalse(sliced.output.contains("line 1\n"))
    }

    @Test
    fun `execute read_file caps at 800 lines by default and includes pagination notice`() = runTest {
        val lines = (1..1000).joinToString("\n") { "line $it" }
        fs.putFile("large.txt", lines)

        val result = executor.execute("read_file", """{"path":"large.txt"}""")
        assertTrue(result.success)
        assertTrue(result.output.contains("line 1"))
        assertTrue(result.output.contains("line 800"))
        assertFalse(result.output.contains("line 801"))
        assertTrue(result.output.contains("capped at 800 lines per read"))
        assertTrue(result.output.contains("start_line=801"))
    }

    @Test
    fun `execute read_file paginates with start_line and end_line`() = runTest {
        val lines = (1..1000).joinToString("\n") { "line $it" }
        fs.putFile("large.txt", lines)

        val result = executor.execute("read_file", """{"path":"large.txt","start_line":801,"end_line":900}""")
        assertTrue(result.success)
        assertFalse(result.output.contains("line 800\n"))
        assertTrue(result.output.contains("line 801"))
        assertTrue(result.output.contains("line 900"))
        assertFalse(result.output.contains("line 901"))
        assertTrue(result.output.contains("(100 lines shown of 1000 total)"))
    }

    @Test
    fun `execute read_file truncates at 45KB byte limit`() = runTest {
        val longLine = "A".repeat(1000)
        val lines = (1..100).joinToString("\n") { "$it: $longLine" }
        fs.putFile("huge_bytes.txt", lines)

        val result = executor.execute("read_file", """{"path":"huge_bytes.txt"}""")
        assertTrue(result.success)
        assertTrue(result.output.contains("Content truncated at 45 KB (46,080 bytes) limit"))
        assertTrue(result.output.toByteArray(Charsets.UTF_8).size <= 47_000)
    }

    @Test
    fun `execute read_file rejects files exceeding 100MB`() = runTest {
        val baseFs = FakeProjectFileSystem("content://test/root")
        baseFs.putFile("giant.iso", "fake content")
        val customFs = object : com.codeagent.core.files.ProjectFileSystem by baseFs {
            override suspend fun fileSize(uri: android.net.Uri): Long? = 150L * 1024L * 1024L
        }
        val customExecutor = ToolExecutor()
        customExecutor.bind(customFs, rootUri, tempDir)

        val result = customExecutor.execute("read_file", """{"path":"giant.iso"}""")
        assertFalse(result.success)
        assertTrue(result.output.contains("exceeds maximum allowed size"))
        assertTrue(result.output.contains("100 MB"))
    }

    @Test
    fun `execute read_file handles empty file cleanly`() = runTest {
        fs.putFile("empty.txt", "")
        val result = executor.execute("read_file", """{"path":"empty.txt"}""")
        assertTrue(result.success)
        assertTrue(result.output.contains("(0 lines)"))
        assertTrue(result.output.contains("(empty file)"))
    }

    @Test
    fun `execute search_code finds matching occurrences`() = runTest {
        fs.putFile("src/App.kt", "fun searchTarget() {\n    println(\"hit\")\n}")
        fs.putFile("src/Other.kt", "fun irrelevant() {}")

        val result = executor.execute("search_code", """{"query":"searchTarget"}""")
        assertTrue(result.success)
        assertTrue(result.output.contains("src/App.kt:1: fun searchTarget()"))
    }

    @Test
    fun `execute get_file_summary returns language and preview`() = runTest {
        fs.putFile("Main.kt", "fun main() {\n    println(1)\n}")

        val result = executor.execute("get_file_summary", """{"path":"Main.kt"}""")
        assertTrue(result.success)
        assertTrue(result.output.contains("Language: Kotlin"))
        assertTrue(result.output.contains("Lines: 3"))
    }

    @Test
    fun `execute propose_file_edit creates pending change`() = runTest {
        fs.putFile("file.kt", "val a = 1")

        val result = executor.execute(
            "propose_file_edit",
            """{"path":"file.kt","content":"val a = 2"}"""
        )
        assertTrue(result.success)
        assertNotNull(result.pendingChange)
        assertEquals("file.kt", result.pendingChange?.filePath)
        assertEquals(ChangeType.EDIT, result.pendingChange?.changeType)
        assertEquals("val a = 1", result.pendingChange?.originalContent)
        assertEquals("val a = 2", result.pendingChange?.proposedContent)
    }

    @Test
    fun `execute propose_file_edit with old_content replaces target section`() = runTest {
        val original = "fun foo() {\n    val a = 1\n    val b = 2\n}"
        fs.putFile("foo.kt", original)

        val result = executor.execute(
            "propose_file_edit",
            """{"path":"foo.kt","old_content":"val a = 1","content":"val a = 100"}"""
        )
        assertTrue(result.success)
        assertNotNull(result.pendingChange)
        assertEquals("fun foo() {\n    val a = 100\n    val b = 2\n}", result.pendingChange?.proposedContent)
    }

    @Test
    fun `execute propose_file_edit preserves code when given a snippet without old_content`() = runTest {
        val original = "fun run() {\n    var i = 0\n    while (i < 5) {\n        i++\n    }\n    return i\n}"
        fs.putFile("loop.kt", original)

        val snippet = "    while (i < 5) {\n        // comment\n        i++\n    }"
        val escapedSnippet = snippet.replace("\n", "\\n").replace("\"", "\\\"")

        val result = executor.execute(
            "propose_file_edit",
            """{"path":"loop.kt","content":"$escapedSnippet"}"""
        )
        assertTrue(result.success)
        val proposed = result.pendingChange?.proposedContent ?: ""
        assertTrue(proposed.contains("fun run() {"))
        assertTrue(proposed.contains("// comment"))
        assertTrue(proposed.contains("return i"))
    }

    @Test
    fun `execute create_file creates CREATE pending change`() = runTest {
        val result = executor.execute(
            "create_file",
            """{"path":"new_file.kt","content":"val newVar = true"}"""
        )
        assertTrue(result.success)
        assertNotNull(result.pendingChange)
        assertEquals(ChangeType.CREATE, result.pendingChange?.changeType)
        assertEquals("val newVar = true", result.pendingChange?.proposedContent)
    }

    @Test
    fun `execute delete_file creates DELETE pending change`() = runTest {
        fs.putFile("delete_me.txt", "bye")

        val result = executor.execute(
            "delete_file",
            """{"path":"delete_me.txt"}"""
        )
        assertTrue(result.success)
        assertNotNull(result.pendingChange)
        assertEquals(ChangeType.DELETE, result.pendingChange?.changeType)
        assertEquals("bye", result.pendingChange?.originalContent)
    }

    @Test
    fun `execute tool with Harmony channel commentary suffix normalizes and executes successfully`() = runTest {
        fs.putFile("keyboard_firmware.c.txt", "delay_ms(1); // allow signals to settle")

        val result = executor.execute(
            "propose_file_edit<|channel|>commentary",
            """{"path":"keyboard_firmware.c.txt","old_content":"delay_ms(1); // allow signals to settle","content":"delay_ms(10000); // allow signals to settle (10 s pause)"}"""
        )
        assertTrue(result.success)
        assertNotNull(result.pendingChange)
        assertEquals(
            "delay_ms(10000); // allow signals to settle (10 s pause)",
            result.pendingChange?.proposedContent
        )
    }

    @Test
    fun `execute execute_command runs real shell command`() = runTest {
        val file = File(tempDir, "README.md")
        file.writeText("# My Project\nLine 2")
        fs.putFile("README.md", "# My Project\nLine 2")
        val result = executor.execute("execute_command", """{"command":"cat README.md"}""")
        assertTrue(result.success)
        assertTrue(result.output.contains("# My Project"))
    }

    @Test
    fun `execute execute_command with ls flags`() = runTest {
        val file = File(tempDir, "main.py")
        file.writeText("print('hello')")
        fs.putFile("main.py", "print('hello')")
        val result = executor.execute("execute_command", """{"command":"ls -la"}""")
        assertTrue(result.success)
        assertTrue(result.output.contains("main.py"))
    }
}
