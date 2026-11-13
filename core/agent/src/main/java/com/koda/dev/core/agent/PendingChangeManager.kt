package com.koda.dev.core.agent

import android.net.Uri
import com.koda.dev.core.data.PendingChangeDao
import com.koda.dev.core.data.PendingChangeEntity
import com.koda.dev.core.files.ProjectFileSystem
import com.koda.dev.core.model.ChangeStatus
import com.koda.dev.core.model.ChangeType
import com.koda.dev.core.model.PendingChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class PendingChangeActionResult(
    val change: PendingChange,
    val result: Result<String>
)

@Singleton
class PendingChangeManager @Inject constructor(
    private val pendingChangeDao: PendingChangeDao
) {
    private var fileSystem: ProjectFileSystem? = null
    private var projectRootUri: Uri? = null

    fun bind(fileSystem: ProjectFileSystem, rootUri: Uri) {
        this.fileSystem = fileSystem
        this.projectRootUri = rootUri
    }

    fun unbind() {
        this.fileSystem = null
        this.projectRootUri = null
    }

    fun getPendingChanges(sessionId: String): Flow<List<PendingChange>> =
        pendingChangeDao.getPendingBySession(sessionId).map { entities ->
            entities.map { it.toModel() }
        }

    suspend fun addChange(change: PendingChange) {
        pendingChangeDao.upsert(change.toEntity())
    }

    suspend fun approve(change: PendingChange): Result<String> {
        val fs = fileSystem
        val rootUri = projectRootUri
        if (fs == null || rootUri == null) {
            return Result.failure(IllegalStateException("No project is open"))
        }

        return try {
            when (change.changeType) {
                ChangeType.EDIT -> {
                    val fileUri = fs.resolveRelativeUri(rootUri, change.filePath)
                    if (fileUri == null || !fs.exists(fileUri)) {
                        return Result.failure(IllegalArgumentException("File not found: ${change.filePath}"))
                    }
                    val current = fs.readTextFile(fileUri)
                        ?: return Result.failure(IllegalStateException("Cannot safely apply edit to ${change.filePath}: current contents are unreadable"))
                    val proposed = change.proposedContent ?: ""
                    when (current) {
                        proposed -> Unit // ToolExecutor already applied this change.
                        change.originalContent -> fs.writeTextFile(fileUri, proposed)
                        else -> return Result.failure(IllegalStateException("Cannot safely apply edit to ${change.filePath}: file changed since the proposal"))
                    }
                    pendingChangeDao.updateStatus(change.id, ChangeStatus.APPLIED.name)
                    Result.success("Applied edit to ${change.filePath}")
                }
                ChangeType.CREATE -> {
                    val fileName = change.filePath.substringAfterLast("/")
                    val existingUri = fs.resolveRelativeUri(rootUri, change.filePath)
                    if (existingUri != null && fs.exists(existingUri)) {
                        val current = fs.readTextFile(existingUri)
                            ?: return Result.failure(IllegalStateException("Cannot safely approve ${change.filePath}: existing contents are unreadable"))
                        if (current != change.proposedContent) {
                            return Result.failure(IllegalStateException("Cannot safely approve ${change.filePath}: a different file exists at that path"))
                        }
                        // The agent wrote the proposed file immediately; do not overwrite a path here.
                    } else {
                        val parentUri = resolveOrCreateParent(fs, rootUri, change.filePath)
                            ?: return Result.failure(IllegalArgumentException("Cannot resolve or create parent directory for: ${change.filePath}"))
                        val targetUri = fs.createFile(parentUri, fileName, mimeTypeForFileName(fileName))
                            ?: return Result.failure(IllegalArgumentException("Failed to create file: ${change.filePath}"))
                        fs.writeTextFile(targetUri, change.proposedContent ?: "")
                    }
                    pendingChangeDao.updateStatus(change.id, ChangeStatus.APPLIED.name)
                    Result.success("Created ${change.filePath}")
                }
                ChangeType.DELETE -> {
                    val fileUri = fs.resolveRelativeUri(rootUri, change.filePath)
                    // ToolExecutor applies deletes immediately. A missing target means the desired
                    // state is already present; if it still exists, delete it and check the result.
                    if (fileUri != null && fs.exists(fileUri) && !fs.deleteFile(fileUri)) {
                        return Result.failure(IOException("Failed to delete ${change.filePath}"))
                    }
                    pendingChangeDao.updateStatus(change.id, ChangeStatus.APPLIED.name)
                    Result.success("Deleted ${change.filePath}")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reject(change: PendingChange): Result<String> {
        val fs = fileSystem
        val rootUri = projectRootUri
        if (fs == null || rootUri == null) {
            return Result.failure(IllegalStateException("Cannot roll back ${change.filePath}: no project is open"))
        }

        return try {
            val orig = change.originalContent
            when (change.changeType) {
                ChangeType.CREATE -> {
                    val fileUri = fs.resolveRelativeUri(rootUri, change.filePath)
                    if (fileUri != null && fs.exists(fileUri)) {
                        val currentContent = fs.readTextFile(fileUri)
                            ?: return Result.failure(IllegalStateException("Cannot safely roll back ${change.filePath}: current contents are unreadable"))
                        if (currentContent != change.proposedContent) {
                            return Result.failure(IllegalStateException("Cannot safely roll back ${change.filePath}: it changed after the agent created it"))
                        }
                        if (!fs.deleteFile(fileUri)) {
                            return Result.failure(IOException("Failed to remove created file ${change.filePath}"))
                        }
                    }
                }
                ChangeType.EDIT -> {
                    val original = orig
                        ?: return Result.failure(IllegalStateException("Cannot restore ${change.filePath}: original contents were not recorded"))
                    val fileUri = fs.resolveRelativeUri(rootUri, change.filePath)
                    if (fileUri == null || !fs.exists(fileUri)) {
                        return Result.failure(IllegalStateException("Cannot restore ${change.filePath}: file is missing"))
                    }
                    val currentContent = fs.readTextFile(fileUri)
                        ?: return Result.failure(IllegalStateException("Cannot safely restore ${change.filePath}: current contents are unreadable"))
                    if (currentContent != change.proposedContent) {
                        return Result.failure(IllegalStateException("Cannot safely restore ${change.filePath}: it changed after the agent edited it"))
                    }
                    fs.writeTextFile(fileUri, original)
                }
                ChangeType.DELETE -> {
                    val original = orig
                        ?: return Result.failure(IllegalStateException("Cannot restore deleted ${change.filePath}: original contents were not recorded"))
                    val existingUri = fs.resolveRelativeUri(rootUri, change.filePath)
                    if (existingUri != null && fs.exists(existingUri)) {
                        return Result.failure(IllegalStateException("Cannot restore deleted ${change.filePath}: a file now exists at that path"))
                    }
                    val parentUri = resolveOrCreateParent(fs, rootUri, change.filePath)
                        ?: return Result.failure(IllegalStateException("Cannot resolve or create parent directory for ${change.filePath}"))
                    val fileName = change.filePath.substringAfterLast("/")
                    val targetUri = fs.createFile(parentUri, fileName, mimeTypeForFileName(fileName))
                        ?: return Result.failure(IOException("Failed to recreate deleted file ${change.filePath}"))
                    fs.writeTextFile(targetUri, original)
                }
            }
            pendingChangeDao.updateStatus(change.id, ChangeStatus.REJECTED.name)
            Result.success("Reverted change for ${change.filePath}")
        } catch (e: Exception) {
            // Preserve PENDING so the UI can tell the user that rollback did not complete and retry.
            Result.failure(e)
        }
    }

    suspend fun approveAll(sessionId: String): List<PendingChangeActionResult> {
        val entities = pendingChangeDao.getPendingBySession(sessionId).first()
        val changes = entities.map { it.toModel() }
        return changes.map { change ->
            PendingChangeActionResult(change, approve(change))
        }
    }

    suspend fun rejectAll(sessionId: String): List<PendingChangeActionResult> {
        val entities = pendingChangeDao.getPendingBySession(sessionId).first()
        val changes = entities.map { it.toModel() }.reversed()
        return changes.map { change ->
            PendingChangeActionResult(change, reject(change))
        }
    }

    private fun PendingChange.toEntity() = PendingChangeEntity(
        id = id,
        sessionId = sessionId,
        filePath = filePath,
        changeType = changeType.name,
        originalContent = originalContent,
        proposedContent = proposedContent,
        createdAt = createdAt,
        status = status.name
    )
}

private fun PendingChangeEntity.toModel() = PendingChange(
    id = id,
    sessionId = sessionId,
    filePath = filePath,
    changeType = ChangeType.valueOf(changeType),
    originalContent = originalContent,
    proposedContent = proposedContent,
    createdAt = createdAt,
    status = ChangeStatus.valueOf(status)
)
