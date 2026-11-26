package com.koda.dev.core.agent

import android.net.FakeUri
import android.net.Uri
import com.koda.dev.core.files.ProjectFileSystem
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
import java.io.IOException

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
    fun `approve create refuses to overwrite a different existing file`() = runTest {
        fs.putFile("new.txt", "user content")
        val change = PendingChange(
            id = "c_create_conflict",
            sessionId = "s1",
            filePath = "new.txt",
            changeType = ChangeType.CREATE,
            originalContent = null,
            proposedContent = "agent content",
            createdAt = 3500L
        )
        manager.addChange(change)

        val result = manager.approve(change)

        assertFalse(result.isSuccess)
        assertEquals("user content", fs.readTextFile(fs.resolveRelativeUri(rootUri, "new.txt")!!))
        assertEquals(ChangeStatus.PENDING.name, dao.getAllBySession("s1").first().first().status)
    }

    @Test
    fun `approve delete leaves change pending when filesystem delete fails`() = runTest {
        fs.putFile("cannot-delete.txt", "keep")
        val failingFs = object : com.koda.dev.core.files.ProjectFileSystem by fs {
            override suspend fun deleteFile(uri: android.net.Uri): Boolean = false
        }
        manager.bind(failingFs, rootUri)
        val change = PendingChange(
            id = "c_delete_failure",
            sessionId = "s1",
            filePath = "cannot-delete.txt",
            changeType = ChangeType.DELETE,
            originalContent = "keep",
            proposedContent = null,
            createdAt = 3600L
        )
        manager.addChange(change)

        val result = manager.approve(change)

        assertFalse(result.isSuccess)
        assertEquals(ChangeStatus.PENDING.name, dao.getAllBySession("s1").first().first().status)
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
    fun `reject create preserves file changed by user after agent created it`() = runTest {
        fs.putFile("created.txt", "user's later edit")
        val change = PendingChange(
            id = "c_create_modified",
            sessionId = "s1",
            filePath = "created.txt",
            changeType = ChangeType.CREATE,
            originalContent = null,
            proposedContent = "agent's initial content",
            createdAt = 4300L
        )
        manager.addChange(change)

        val result = manager.reject(change)

        assertFalse(result.isSuccess)
        assertEquals("user's later edit", fs.readTextFile(fs.resolveRelativeUri(rootUri, "created.txt")!!))
        assertEquals(ChangeStatus.PENDING.name, dao.getAllBySession("s1").first().first().status)
    }

    @Test
    fun `reject edit does not overwrite user changes made after agent edit`() = runTest {
        fs.putFile("edited.txt", "user's later edit")
        val change = PendingChange(
            id = "c_edit_modified",
            sessionId = "s1",
            filePath = "edited.txt",
            changeType = ChangeType.EDIT,
            originalContent = "before agent",
            proposedContent = "agent version",
            createdAt = 4400L
        )
        manager.addChange(change)

        val result = manager.reject(change)

        assertFalse(result.isSuccess)
        assertEquals("user's later edit", fs.readTextFile(fs.resolveRelativeUri(rootUri, "edited.txt")!!))
        assertEquals(ChangeStatus.PENDING.name, dao.getAllBySession("s1").first().first().status)
    }

    @Test
    fun `reject delete fails visibly when original bytes were not captured`() = runTest {
        val change = PendingChange(
            id = "c_del_binary",
            sessionId = "s1",
            filePath = "binary.dat",
            changeType = ChangeType.DELETE,
            originalContent = null,
            proposedContent = null,
            createdAt = 4500L
        )
        manager.addChange(change)

        val result = manager.reject(change)

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("original contents were not recorded"))
        assertEquals(ChangeStatus.PENDING.name, dao.getAllBySession("s1").first().first().status)
    }

    @Test
    fun `reject delete refuses to overwrite a replacement created at the deleted path`() = runTest {
        fs.putFile("restored.txt", "new file from another actor")
        val change = PendingChange(
            id = "c_del_conflict",
            sessionId = "s1",
            filePath = "restored.txt",
            changeType = ChangeType.DELETE,
            originalContent = "deleted original",
            proposedContent = null,
            createdAt = 4600L
        )
        manager.addChange(change)

        val result = manager.reject(change)

        assertFalse(result.isSuccess)
        assertEquals("new file from another actor", fs.readTextFile(fs.resolveRelativeUri(rootUri, "restored.txt")!!))
        assertEquals(ChangeStatus.PENDING.name, dao.getAllBySession("s1").first().first().status)
    }

    @Test
    fun `reject delete restores file when filesystem resolves non-existent paths`() = runTest {
        val resolvingFs = object : com.koda.dev.core.files.ProjectFileSystem by fs {
            override suspend fun resolveRelativeUri(rootUri: android.net.Uri, relPath: String): android.net.Uri =
                FakeUri("$rootUri/$relPath")
        }
        manager.bind(resolvingFs, rootUri)
        val change = PendingChange(
            id = "c_del_resolving",
            sessionId = "s1",
            filePath = "deleted_path.txt",
            changeType = ChangeType.DELETE,
            originalContent = "restored content",
            proposedContent = null,
            createdAt = 4800L
        )
        manager.addChange(change)

        val result = manager.reject(change)

        assertTrue(result.isSuccess)
        assertEquals("restored content", fs.readTextFile(fs.resolveRelativeUri(rootUri, "deleted_path.txt")!!))
        assertEquals(ChangeStatus.REJECTED.name, dao.getAllBySession("s1").first().first().status)
    }

    @Test
    fun `reject fails and leaves change pending when project is unbound`() = runTest {
        val change = PendingChange(
            id = "c_unbound_reject",
            sessionId = "s1",
            filePath = "file.txt",
            changeType = ChangeType.EDIT,
            originalContent = "old",
            proposedContent = "new",
            createdAt = 4700L
        )
        manager.addChange(change)
        manager.unbind()

        val result = manager.reject(change)

        assertFalse(result.isSuccess)
        assertEquals(ChangeStatus.PENDING.name, dao.getAllBySession("s1").first().first().status)
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

    @Test
    fun `reject fails when unbound and keeps change pending`() = runTest {
        val change = PendingChange(
            id = "c_unbound_reject",
            sessionId = "s1",
            filePath = "keep.txt",
            changeType = ChangeType.EDIT,
            originalContent = "original",
            proposedContent = "modified",
            createdAt = 5900L
        )
        manager.addChange(change)
        manager.unbind()

        val result = manager.reject(change)

        assertTrue(result.isFailure)
        assertEquals(
            ChangeStatus.PENDING.name,
            dao.getAllBySession("s1").first().first().status
        )
    }

    @Test
    fun `reject edit keeps change pending when rollback write fails`() = runTest {
        fs.putFile("keep.txt", "modified")
        manager.bind(
            RollbackFailingFileSystem(fs, failWrites = true),
            rootUri
        )

        val change = PendingChange(
            id = "c_fail_edit",
            sessionId = "s1",
            filePath = "keep.txt",
            changeType = ChangeType.EDIT,
            originalContent = "original",
            proposedContent = "modified",
            createdAt = 6000L
        )
        manager.addChange(change)

        val result = manager.reject(change)

        assertTrue(result.isFailure)
        assertEquals(
            ChangeStatus.PENDING.name,
            dao.getAllBySession("s1").first().first().status
        )
        assertEquals(
            "modified",
            fs.readTextFile(fs.resolveRelativeUri(rootUri, "keep.txt")!!)
        )
    }

    @Test
    fun `reject create keeps change pending when delete fails`() = runTest {
        fs.putFile("created.txt", "new file content")
        manager.bind(
            RollbackFailingFileSystem(fs, deleteSucceeds = false),
            rootUri
        )

        val change = PendingChange(
            id = "c_fail_create",
            sessionId = "s1",
            filePath = "created.txt",
            changeType = ChangeType.CREATE,
            originalContent = null,
            proposedContent = "new file content",
            createdAt = 6100L
        )
        manager.addChange(change)

        val result = manager.reject(change)

        assertTrue(result.isFailure)
        assertEquals(
            ChangeStatus.PENDING.name,
            dao.getAllBySession("s1").first().first().status
        )
        assertEquals(
            "new file content",
            fs.readTextFile(fs.resolveRelativeUri(rootUri, "created.txt")!!)
        )
    }

    @Test
    fun `approve delete keeps change pending when delete fails`() = runTest {
        fs.putFile("old.txt", "deprecated")
        manager.bind(
            RollbackFailingFileSystem(fs, deleteSucceeds = false),
            rootUri
        )

        val change = PendingChange(
            id = "c_fail_delete",
            sessionId = "s1",
            filePath = "old.txt",
            changeType = ChangeType.DELETE,
            originalContent = "deprecated",
            proposedContent = null,
            createdAt = 6200L
        )
        manager.addChange(change)

        val result = manager.approve(change)

        assertTrue(result.isFailure)
        assertEquals(
            ChangeStatus.PENDING.name,
            dao.getAllBySession("s1").first().first().status
        )
        assertEquals(
            "deprecated",
            fs.readTextFile(fs.resolveRelativeUri(rootUri, "old.txt")!!)
        )
    }

    @Test
    fun `approve create and reject delete refuse symlink directories escaping project root`() = runTest {
        val projDir = java.nio.file.Files.createTempDirectory("pcm-proj").toFile()
        val outsideDir = java.nio.file.Files.createTempDirectory("pcm-outside").toFile()
        try {
            val outsideSecret = java.io.File(outsideDir, "secret.txt").apply { writeText("KEEP") }
            val outsideRestored = java.io.File(outsideDir, "restored.txt")
            java.nio.file.Files.createSymbolicLink(java.io.File(projDir, "escape").toPath(), outsideDir.toPath())

            val realFs = com.koda.dev.core.files.FileProjectFileSystem(
                dispatchers = com.koda.dev.core.common.DefaultAppDispatchers,
                uriFactory = { file -> FakeUri("file://${file.absolutePath}") }
            )
            val realRootUri = FakeUri("file://${projDir.absolutePath}")
            manager.bind(realFs, realRootUri)

            val createEscape = PendingChange(
                id = "c_escape_create",
                sessionId = "s1",
                filePath = "escape/secret.txt",
                changeType = ChangeType.CREATE,
                originalContent = null,
                proposedContent = "PWNED",
                createdAt = 6300L
            )
            assertTrue(manager.approve(createEscape).isFailure)
            assertEquals("KEEP", outsideSecret.readText())

            val deleteRestoreEscape = PendingChange(
                id = "c_escape_restore",
                sessionId = "s1",
                filePath = "escape/restored.txt",
                changeType = ChangeType.DELETE,
                originalContent = "PWNED",
                proposedContent = null,
                createdAt = 6400L
            )
            assertTrue(manager.reject(deleteRestoreEscape).isFailure)
            assertFalse(outsideRestored.exists())
        } finally {
            projDir.deleteRecursively()
            outsideDir.deleteRecursively()
        }
    }
}

private class RollbackFailingFileSystem(
    private val delegate: FakeProjectFileSystem,
    private val failWrites: Boolean = false,
    private val deleteSucceeds: Boolean = true
) : ProjectFileSystem by delegate {

    override suspend fun writeTextFile(uri: Uri, content: String) {
        if (failWrites) throw IOException("Simulated write failure")
        delegate.writeTextFile(uri, content)
    }

    override suspend fun deleteFile(uri: Uri): Boolean {
        if (!deleteSucceeds) return false
        return delegate.deleteFile(uri)
    }
}
