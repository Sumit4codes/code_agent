package com.koda.dev.core.agent

import android.net.FakeUri
import com.koda.dev.core.model.ChangeStatus
import com.koda.dev.core.model.ChangeType
import com.koda.dev.core.model.PendingChange
import com.koda.dev.core.testing.FakePendingChangeDao
import com.koda.dev.core.testing.FakeProjectFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PendingChangeManagerTest {

    private lateinit var dao: FakePendingChangeDao
    private lateinit var fs: FakeProjectFileSystem
    private lateinit var manager: PendingChangeManager
    private val rootUri = FakeUri("content://test/root")

    @Before
    fun setUp() {
        dao = FakePendingChangeDao()
        fs = FakeProjectFileSystem("content://test/root")
        manager = PendingChangeManager(dao)
        manager.bind(fs, rootUri)
    }

    @Test
    fun `approve edit updates file content and marks status APPLIED`() = runTest {
        fs.putFile("Main.kt", "println(\"Hello\")")

        val change = PendingChange(
            id = "c1",
            sessionId = "s1",
            filePath = "Main.kt",
            changeType = ChangeType.EDIT,
            originalContent = "println(\"Hello\")",
            proposedContent = "println(\"Hello, World!\")",
            createdAt = 1000L
        )
        manager.addChange(change)

        val result = manager.approve(change)
        assertTrue(result.isSuccess)

        val fileUri = fs.resolveRelativeUri(rootUri, "Main.kt")!!
        val updated = fs.readTextFile(fileUri)
        assertEquals("println(\"Hello, World!\")", updated)

        val all = dao.getAllBySession("s1").first()
        assertEquals(ChangeStatus.APPLIED.name, all.first().status)
    }

    @Test
    fun `approve create creates nested directories and file with content`() = runTest {
        val change = PendingChange(
            id = "c2",
            sessionId = "s1",
            filePath = "nested/sub/NewClass.kt",
            changeType = ChangeType.CREATE,
            originalContent = null,
            proposedContent = "class NewClass",
            createdAt = 2000L
        )
        manager.addChange(change)

        val result = manager.approve(change)
        assertTrue(result.isSuccess)

        val fileUri = fs.resolveRelativeUri(rootUri, "nested/sub/NewClass.kt")
        assertTrue(fileUri != null)
        val content = fs.readTextFile(fileUri!!)
        assertEquals("class NewClass", content)

        val all = dao.getAllBySession("s1").first()
        assertEquals(ChangeStatus.APPLIED.name, all.first().status)
    }

    @Test
    fun `approve delete removes file and marks status APPLIED`() = runTest {
        fs.putFile("old.txt", "deprecated")

        val change = PendingChange(
            id = "c3",
            sessionId = "s1",
            filePath = "old.txt",
            changeType = ChangeType.DELETE,
            originalContent = "deprecated",
            proposedContent = null,
            createdAt = 3000L
        )
        manager.addChange(change)

        val result = manager.approve(change)
        assertTrue(result.isSuccess)

        val fileUri = fs.resolveRelativeUri(rootUri, "old.txt")
        assertNull(fileUri)

        val all = dao.getAllBySession("s1").first()
        assertEquals(ChangeStatus.APPLIED.name, all.first().status)
    }

    @Test
    fun `reject edit reverts file content to originalContent and marks status REJECTED`() = runTest {
        // File has the agent's staged modified content
        fs.putFile("keep.txt", "modified")

        val change = PendingChange(
            id = "c4",
            sessionId = "s1",
            filePath = "keep.txt",
            changeType = ChangeType.EDIT,
            originalContent = "original",
            proposedContent = "modified",
            createdAt = 4000L
        )
        manager.addChange(change)

        val result = manager.reject(change)
        assertTrue(result.isSuccess)

        // File must be rolled back to original
        val fileUri = fs.resolveRelativeUri(rootUri, "keep.txt")!!
        assertEquals("original", fs.readTextFile(fileUri))

        val all = dao.getAllBySession("s1").first()
        assertEquals(ChangeStatus.REJECTED.name, all.first().status)
    }

    @Test
    fun `reject create deletes newly created file and marks status REJECTED`() = runTest {
        fs.putFile("created.txt", "new file content")

        val change = PendingChange(
            id = "c_create",
            sessionId = "s1",
            filePath = "created.txt",
            changeType = ChangeType.CREATE,
            originalContent = null,
            proposedContent = "new file content",
            createdAt = 4100L
        )
        manager.addChange(change)

        val result = manager.reject(change)
        assertTrue(result.isSuccess)

        // Created file must be deleted on rejection
        val fileUri = fs.resolveRelativeUri(rootUri, "created.txt")
        assertNull(fileUri)

        val all = dao.getAllBySession("s1").first()
        assertEquals(ChangeStatus.REJECTED.name, all.first().status)
    }

    @Test
    fun `reject delete restores file with originalContent and marks status REJECTED`() = runTest {
        val change = PendingChange(
            id = "c_del",
            sessionId = "s1",
            filePath = "restored.txt",
            changeType = ChangeType.DELETE,
            originalContent = "restored original",
            proposedContent = null,
            createdAt = 4200L
        )
        manager.addChange(change)

        val result = manager.reject(change)
        assertTrue(result.isSuccess)

        // File must be recreated with original content
        val fileUri = fs.resolveRelativeUri(rootUri, "restored.txt")!!
        assertEquals("restored original", fs.readTextFile(fileUri))

        val all = dao.getAllBySession("s1").first()
        assertEquals(ChangeStatus.REJECTED.name, all.first().status)
    }

    @Test
    fun `approve fails when unbound`() = runTest {
        manager.unbind()

        val change = PendingChange(
            id = "c5",
            sessionId = "s1",
            filePath = "file.txt",
            changeType = ChangeType.EDIT,
            originalContent = "",
            proposedContent = "test",
            createdAt = 5000L
        )

        val result = manager.approve(change)
        assertFalse(result.isSuccess)
    }
}
