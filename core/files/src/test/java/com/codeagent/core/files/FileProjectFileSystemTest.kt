package com.codeagent.core.files

import android.net.FakeUri
import android.net.Uri
import com.codeagent.core.common.DefaultAppDispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileProjectFileSystemTest {

    private lateinit var tempDir: File
    private lateinit var fileSystem: FileProjectFileSystem
    private lateinit var rootUri: Uri

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("codeagent-test").toFile()
        fileSystem = FileProjectFileSystem(
            dispatchers = DefaultAppDispatchers,
            uriFactory = { FakeUri("file://" + it.absolutePath) }
        )
        rootUri = FakeUri("file://" + tempDir.absolutePath)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `creates file, writes text and reads text successfully`() = runBlocking {
        val fileUri = fileSystem.createFile(rootUri, "hello.txt", "text/plain")
        assertNotNull(fileUri)
        assertTrue(fileSystem.exists(fileUri!!))

        fileSystem.writeTextFile(fileUri, "Hello CodeAgent!")
        val content = fileSystem.readTextFile(fileUri)
        assertEquals("Hello CodeAgent!", content)

        val size = fileSystem.fileSize(fileUri)
        assertEquals(16L, size)

        val lastMod = fileSystem.lastModified(fileUri)
        assertNotNull(lastMod)
        assertTrue(lastMod!! > 0)
    }

    @Test
    fun `creates directory and lists children`() = runBlocking {
        val subDirUri = fileSystem.createDirectory(rootUri, "src")
        assertNotNull(subDirUri)
        assertTrue(fileSystem.exists(subDirUri!!))

        val child1 = fileSystem.createFile(subDirUri, "A.kt", "text/plain")
        val child2 = fileSystem.createFile(subDirUri, "B.kt", "text/plain")
        assertNotNull(child1)
        assertNotNull(child2)

        val children = fileSystem.listChildren(subDirUri)
        assertEquals(2, children.size)
        val names = children.map { it.name }.toSet()
        assertTrue(names.contains("A.kt"))
        assertTrue(names.contains("B.kt"))
        assertFalse(children.first { it.name == "A.kt" }.isDirectory)
    }

    @Test
    fun `deletes file and directories recursively`() = runBlocking {
        val subDirUri = fileSystem.createDirectory(rootUri, "to_delete")
        assertNotNull(subDirUri)
        fileSystem.createFile(subDirUri!!, "temp.txt", "text/plain")

        val deleted = fileSystem.deleteFile(subDirUri)
        assertTrue(deleted)
        assertFalse(fileSystem.exists(subDirUri))
    }

    @Test
    fun `resolves relative paths with relative, leading slash, and dot-slash`() = runBlocking {
        val subDir = fileSystem.createDirectory(rootUri, "sub")
        val file = fileSystem.createFile(subDir!!, "target.txt", "text/plain")
        assertNotNull(file)

        // Standard relative path
        val resolved1 = fileSystem.resolveRelativeUri(rootUri, "sub/target.txt")
        assertNotNull(resolved1)
        assertEquals(file.toString(), resolved1.toString())

        // Path with leading slash
        val resolved2 = fileSystem.resolveRelativeUri(rootUri, "/sub/target.txt")
        assertNotNull(resolved2)
        assertEquals(file.toString(), resolved2.toString())

        // Path with ./
        val resolved3 = fileSystem.resolveRelativeUri(rootUri, "./sub/target.txt")
        assertNotNull(resolved3)
        assertEquals(file.toString(), resolved3.toString())

        // Full absolute path within project root
        val absPath = File(tempDir, "sub/target.txt").absolutePath
        val resolved4 = fileSystem.resolveRelativeUri(rootUri, absPath)
        assertNotNull(resolved4)
        assertEquals(file.toString(), resolved4.toString())
    }

    @Test
    fun `rejects path traversal outside root directory`() = runBlocking {
        val traversal1 = fileSystem.resolveRelativeUri(rootUri, "../../etc/passwd")
        assertNull(traversal1)

        val traversal2 = fileSystem.resolveRelativeUri(rootUri, "../other")
        assertNull(traversal2)
    }

    @Test
    fun `returns null on non-existent file read`() = runBlocking {
        val nonExistent = FakeUri("file://" + File(tempDir, "does_not_exist.txt").absolutePath)
        val content = fileSystem.readTextFile(nonExistent)
        assertNull(content)
        assertFalse(fileSystem.exists(nonExistent))
    }
}
