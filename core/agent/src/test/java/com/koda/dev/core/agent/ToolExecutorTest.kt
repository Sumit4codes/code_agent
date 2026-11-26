package com.koda.dev.core.agent

import android.net.FakeUri
import com.koda.dev.core.model.ChangeType
import com.koda.dev.core.testing.FakeProjectFileSystem
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
    fun `execute read_file rejects files exceeding 10MB`() = runTest {
        val baseFs = FakeProjectFileSystem("content://test/root")
        baseFs.putFile("giant.iso", "fake content")
        val customFs = object : com.koda.dev.core.files.ProjectFileSystem by baseFs {
            override suspend fun fileSize(uri: android.net.Uri): Long? = 150L * 1024L * 1024L
        }
        val customExecutor = ToolExecutor()
        customExecutor.bind(customFs, rootUri, tempDir)

        val result = customExecutor.execute("read_file", """{"path":"giant.iso"}""")
        assertFalse(result.success)
        assertTrue(result.output.contains("exceeds maximum allowed size"))
        assertTrue(result.output.contains("10 MB"))
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
    fun `execute propose_file_edit creates pending change and writes to workspace`() = runTest {
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

        // Verifies the file is written immediately so terminal commands can run
        val fileUri = fs.resolveRelativeUri(rootUri, "file.kt")!!
        assertEquals("val a = 2", fs.readTextFile(fileUri))
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

        val fileUri = fs.resolveRelativeUri(rootUri, "foo.kt")!!
        assertEquals("fun foo() {\n    val a = 100\n    val b = 2\n}", fs.readTextFile(fileUri))
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
    fun `execute create_file creates CREATE pending change and writes to workspace`() = runTest {
        val result = executor.execute(
            "create_file",
            """{"path":"new_file.kt","content":"val newVar = true"}"""
        )
        assertTrue(result.success)
        assertNotNull(result.pendingChange)
        assertEquals(ChangeType.CREATE, result.pendingChange?.changeType)
        assertEquals("val newVar = true", result.pendingChange?.proposedContent)

        // Verifies the file is written immediately so terminal commands can run
        val fileUri = fs.resolveRelativeUri(rootUri, "new_file.kt")!!
        assertEquals("val newVar = true", fs.readTextFile(fileUri))
    }

    @Test
    fun `execute create_file refuses to overwrite existing file`() = runTest {
        fs.putFile("existing.txt", "keep original")

        val result = executor.execute(
            "create_file",
            """{"path":"existing.txt","content":"replacement"}"""
        )

        assertFalse(result.success)
        assertTrue(result.output.contains("already exists"))
        val fileUri = fs.resolveRelativeUri(rootUri, "existing.txt")!!
        assertEquals("keep original", fs.readTextFile(fileUri))
        assertEquals(null, result.pendingChange)
    }

    @Test
    fun `execute create_file succeeds on filesystem that resolves non-existent paths`() = runTest {
        val resolvingFs = object : com.koda.dev.core.files.ProjectFileSystem by fs {
            override suspend fun resolveRelativeUri(rootUri: android.net.Uri, relPath: String): android.net.Uri =
                FakeUri("$rootUri/$relPath")
        }
        val customExecutor = ToolExecutor()
        customExecutor.bind(resolvingFs, rootUri, tempDir)

        val result = customExecutor.execute(
            "create_file",
            """{"path":"brand_new.kt","content":"println(\"hi\")"}"""
        )
        assertTrue(result.success)
        assertNotNull(result.pendingChange)
    }

    @Test
    fun `execute read_file returns not found when file does not exist even if URI resolves`() = runTest {
        val resolvingFs = object : com.koda.dev.core.files.ProjectFileSystem by fs {
            override suspend fun resolveRelativeUri(rootUri: android.net.Uri, relPath: String): android.net.Uri =
                FakeUri("$rootUri/$relPath")
        }
        val customExecutor = ToolExecutor()
        customExecutor.bind(resolvingFs, rootUri, tempDir)

        val result = customExecutor.execute("read_file", """{"path":"does_not_exist.txt"}""")
        assertFalse(result.success)
        assertTrue(result.output.contains("File not found"))
    }

    @Test
    fun `execute delete_file refuses unreadable content so rejection can restore it`() = runTest {
        fs.putFile("binary.bin", "opaque bytes")
        val unreadableFs = object : com.koda.dev.core.files.ProjectFileSystem by fs {
            override suspend fun readTextFile(uri: android.net.Uri): String? = null
        }
        val safeExecutor = ToolExecutor()
        safeExecutor.bind(unreadableFs, rootUri, tempDir)

        val result = safeExecutor.execute("delete_file", """{"path":"binary.bin"}""")

        assertFalse(result.success)
        assertTrue(result.output.contains("could not be restored"))
        assertNotNull(fs.resolveRelativeUri(rootUri, "binary.bin"))
    }

    @Test
    fun `execute delete_file refuses text-decoded binary content containing control bytes`() = runTest {
        fs.putFile("binary.bin", "header\u0000\u0001payload")

        val result = executor.execute("delete_file", """{"path":"binary.bin"}""")

        assertFalse(result.success)
        assertTrue(result.output.contains("appears binary"))
        assertNotNull(fs.resolveRelativeUri(rootUri, "binary.bin"))
    }

    @Test
    fun `execute delete_file reports filesystem delete failure`() = runTest {
        fs.putFile("undeletable.txt", "still here")
        val failingFs = object : com.koda.dev.core.files.ProjectFileSystem by fs {
            override suspend fun deleteFile(uri: android.net.Uri): Boolean = false
        }
        val safeExecutor = ToolExecutor()
        safeExecutor.bind(failingFs, rootUri, tempDir)

        val result = safeExecutor.execute("delete_file", """{"path":"undeletable.txt"}""")

        assertFalse(result.success)
        assertTrue(result.output.contains("Failed to delete"))
        assertEquals(null, result.pendingChange)
        assertNotNull(fs.resolveRelativeUri(rootUri, "undeletable.txt"))
    }

    @Test
    fun `read_file and get_file_summary reject large files before reading contents`() = runTest {
        fs.putFile("too-large.txt", "small fake body")
        var readCount = 0
        val largeFs = object : com.koda.dev.core.files.ProjectFileSystem by fs {
            override suspend fun fileSize(uri: android.net.Uri): Long = 11L * 1024L * 1024L
            override suspend fun readTextFile(uri: android.net.Uri): String? {
                readCount++
                return fs.readTextFile(uri)
            }
        }
        val boundedExecutor = ToolExecutor()
        boundedExecutor.bind(largeFs, rootUri, tempDir)

        val read = boundedExecutor.execute("read_file", """{"path":"too-large.txt"}""")
        val summary = boundedExecutor.execute("get_file_summary", """{"path":"too-large.txt"}""")

        assertFalse(read.success)
        assertFalse(summary.success)
        assertEquals("Oversized file contents must not be materialized", 0, readCount)
    }

    @Test
    fun `read_file refuses files with unknown size before reading`() = runTest {
        fs.putFile("unknown-size.txt", "content")
        var readCount = 0
        val unknownSizeFs = object : com.koda.dev.core.files.ProjectFileSystem by fs {
            override suspend fun fileSize(uri: android.net.Uri): Long? = null
            override suspend fun readTextFile(uri: android.net.Uri): String? {
                readCount++
                return fs.readTextFile(uri)
            }
        }
        val boundedExecutor = ToolExecutor()
        boundedExecutor.bind(unknownSizeFs, rootUri, tempDir)

        val result = boundedExecutor.execute("read_file", """{"path":"unknown-size.txt"}""")

        assertFalse(result.success)
        assertEquals(0, readCount)
    }

    @Test
    fun `execute delete_file creates DELETE pending change and removes from workspace`() = runTest {
        fs.putFile("delete_me.txt", "bye")

        val result = executor.execute(
            "delete_file",
            """{"path":"delete_me.txt"}"""
        )
        assertTrue(result.success)
        assertNotNull(result.pendingChange)
        assertEquals(ChangeType.DELETE, result.pendingChange?.changeType)
        assertEquals("bye", result.pendingChange?.originalContent)

        val fileUri = fs.resolveRelativeUri(rootUri, "delete_me.txt")
        org.junit.Assert.assertNull(fileUri)
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

    @Test
    fun `execute web_search executes successfully even when project is unbound`() = runTest {
        val fakeSearchClient = object : com.koda.dev.core.data.web.WebSearchClient {
            override suspend fun search(query: String, limit: Int): Result<List<com.koda.dev.core.model.WebSearchResult>> {
                return Result.success(listOf(
                    com.koda.dev.core.model.WebSearchResult("Kotlin Flows", "https://kotlinlang.org/flow", "Flow guide")
                ))
            }
            override fun formatResultsForAgent(query: String, results: List<com.koda.dev.core.model.WebSearchResult>): String {
                return "Results for $query: ${results.first().title}"
            }
        }
        val customExecutor = ToolExecutor(
            terminalExecutor = com.koda.dev.core.terminal.DefaultTerminalExecutor(),
            webSearchClient = fakeSearchClient,
            webPageFetcher = com.koda.dev.core.data.web.DefaultWebPageFetcher()
        )
        // No bind call on customExecutor
        val result = customExecutor.execute("web_search", """{"query":"kotlin flow"}""")
        assertTrue(result.success)
        assertTrue(result.output.contains("Results for kotlin flow: Kotlin Flows"))
    }

    @Test
    fun `execute fetch_web_page executes successfully even when project is unbound`() = runTest {
        val fakeFetcher = object : com.koda.dev.core.data.web.WebPageFetcher {
            override suspend fun fetch(url: String, startLine: Int?, endLine: Int?): Result<String> {
                return Result.success("# Sample Documentation\nContent from $url")
            }
        }
        val customExecutor = ToolExecutor(
            terminalExecutor = com.koda.dev.core.terminal.DefaultTerminalExecutor(),
            webSearchClient = com.koda.dev.core.data.web.DefaultWebSearchClient(),
            webPageFetcher = fakeFetcher
        )
        // No bind call on customExecutor
        val result = customExecutor.execute("fetch_web_page", """{"url":"https://example.com/docs"}""")
        assertTrue(result.success)
        assertTrue(result.output.contains("# Sample Documentation"))
        assertTrue(result.output.contains("https://example.com/docs"))
    }

    @Test
    fun `execute execute_command with background true launches managed task`() = runTest {
        val result = executor.execute("execute_command", """{"command":"sleep 10","is_background":true}""")
        assertTrue(result.success)
        assertTrue(result.output.contains("Command is running in background task"))

        val taskId = result.output.substringAfter("background task '").substringBefore("'")
        assertTrue(taskId.startsWith("task_"))

        // Status
        val statusRes = executor.execute("manage_task", """{"action":"status","task_id":"$taskId"}""")
        assertTrue(statusRes.success)
        assertTrue(statusRes.output.contains("RUNNING"))

        // List
        val listRes = executor.execute("manage_task", """{"action":"list"}""")
        assertTrue(listRes.success)
        assertTrue(listRes.output.contains(taskId))

        // Kill
        val killRes = executor.execute("manage_task", """{"action":"kill","task_id":"$taskId"}""")
        assertTrue(killRes.success)
        assertTrue(killRes.output.contains("was terminated successfully"))
    }

    @Test
    fun `globMatch handles brace patterns correctly without throwing regex exceptions`() {
        val pattern = "*.{kts,sh,toml}"
        assertTrue(executor.globMatch(pattern, "build.gradle.kts"))
        assertTrue(executor.globMatch(pattern, "scripts/check-env.sh"))
        assertTrue(executor.globMatch(pattern, "gradle.properties.toml"))
        assertFalse(executor.globMatch(pattern, "App.kt"))
        assertFalse(executor.globMatch(pattern, "MainActivity.java"))

        // Malformed brace pattern should not throw
        assertTrue(executor.globMatch("*.{kts,sh", "test.kts"))

        // Special regex characters in pattern
        assertTrue(executor.globMatch("file+name[1].txt", "file+name[1].txt"))
        assertTrue(executor.globMatch("*.kt", "App.kt"))
        assertTrue(executor.globMatch("*Test*.java", "ToolExecutorTest.java"))
    }

    @Test
    fun `execute search_code with brace glob pattern and alternation query`() = runTest {
        fs.putFile("build.gradle.kts", "org.gradle.parallel=true\norg.gradle.jvmargs=-Xmx2048m")
        fs.putFile("build.sh", "#!/bin/bash\nexport GRADLE_OPTS=\"-Dorg.gradle.workers.max=4\"")
        fs.putFile("gradle/libs.versions.toml", "[versions]\nkotlin = \"2.1.0\"")
        fs.putFile("Main.kt", "fun main() {\n    val parallel = false\n}")

        val result = executor.execute(
            "search_code",
            """{"file_pattern":"*.{kts,sh,toml}","query":"no-daemon|parallel|workers.max|org.gradle"}"""
        )
        assertTrue(result.success)
        assertTrue(result.output.contains("build.gradle.kts:1: org.gradle.parallel=true"))
        assertTrue(result.output.contains("build.sh:2: export GRADLE_OPTS=\"-Dorg.gradle.workers.max=4\""))
        assertFalse(result.output.contains("Main.kt"))
    }

    @Test
    fun `execute create_file refuses directory symlink escaping project root`() = runTest {
        val outsideDir = Files.createTempDirectory("outside-target").toFile()
        try {
            val outsideExisting = File(outsideDir, "secret.txt").apply { writeText("KEEP") }
            val outsideNew = File(outsideDir, "new.txt")
            Files.createSymbolicLink(File(tempDir, "escape").toPath(), outsideDir.toPath())

            val realFs = com.koda.dev.core.files.FileProjectFileSystem(
                dispatchers = com.koda.dev.core.common.DefaultAppDispatchers,
                uriFactory = { file -> FakeUri("file://${file.absolutePath}") }
            )
            val realExecutor = ToolExecutor()
            val realRootUri = FakeUri("file://${tempDir.absolutePath}")
            realExecutor.bind(realFs, realRootUri, tempDir)

            val overwriteAttempt = realExecutor.execute(
                "create_file",
                """{"path":"escape/secret.txt","content":"PWNED"}"""
            )
            assertFalse(overwriteAttempt.success)
            assertEquals("KEEP", outsideExisting.readText())

            val createAttempt = realExecutor.execute(
                "create_file",
                """{"path":"escape/new.txt","content":"PWNED"}"""
            )
            assertFalse(createAttempt.success)
            assertFalse(outsideNew.exists())
        } finally {
            outsideDir.deleteRecursively()
        }
    }

    @Test
    fun `execute create_file refuses leaf symlink escaping project root`() = runTest {
        val outsideDir = Files.createTempDirectory("outside-leaf").toFile()
        try {
            val outsideTarget = File(outsideDir, "secret.txt").apply { writeText("KEEP") }
            Files.createSymbolicLink(File(tempDir, "leaf_escape.txt").toPath(), outsideTarget.toPath())

            val realFs = com.koda.dev.core.files.FileProjectFileSystem(
                dispatchers = com.koda.dev.core.common.DefaultAppDispatchers,
                uriFactory = { file -> FakeUri("file://${file.absolutePath}") }
            )
            val realExecutor = ToolExecutor()
            val realRootUri = FakeUri("file://${tempDir.absolutePath}")
            realExecutor.bind(realFs, realRootUri, tempDir)

            val result = realExecutor.execute(
                "create_file",
                """{"path":"leaf_escape.txt","content":"PWNED"}"""
            )
            assertFalse(result.success)
            assertEquals("KEEP", outsideTarget.readText())
        } finally {
            outsideDir.deleteRecursively()
        }
    }

    @Test
    fun `execute search_code skips symlinks escaping project root`() = runTest {
        val outsideDir = Files.createTempDirectory("outside-search").toFile()
        try {
            val outsideSecret = File(outsideDir, "secret.txt").apply { writeText("TOP_SECRET_TOKEN") }
            Files.createSymbolicLink(File(tempDir, "escape_dir").toPath(), outsideDir.toPath())
            Files.createSymbolicLink(File(tempDir, "escape_file.txt").toPath(), outsideSecret.toPath())
            File(tempDir, "safe.txt").writeText("normal code")

            val realFs = com.koda.dev.core.files.FileProjectFileSystem(
                dispatchers = com.koda.dev.core.common.DefaultAppDispatchers,
                uriFactory = { file -> FakeUri("file://${file.absolutePath}") }
            )
            val realExecutor = ToolExecutor()
            val realRootUri = FakeUri("file://${tempDir.absolutePath}")
            realExecutor.bind(realFs, realRootUri, tempDir)

            val result = realExecutor.execute("search_code", """{"query":"TOP_SECRET_TOKEN"}""")
            assertTrue(result.success)
            assertEquals("No matches found", result.output)
        } finally {
            outsideDir.deleteRecursively()
        }
    }
}
