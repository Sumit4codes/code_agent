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
                        ?: return Result.failure(IllegalArgumentException("File not found: ${change.filePath}"))
                    fs.writeTextFile(fileUri, change.proposedContent ?: "")
                    pendingChangeDao.updateStatus(change.id, ChangeStatus.APPLIED.name)
                    Result.success("Applied edit to ${change.filePath}")
                }
                ChangeType.CREATE -> {
                    val parentUri = resolveOrCreateParent(fs, rootUri, change.filePath)
                        ?: return Result.failure(IllegalArgumentException("Cannot resolve or create parent directory for: ${change.filePath}"))
                    val fileName = change.filePath.substringAfterLast("/")
                    val existingUri = fs.resolveRelativeUri(rootUri, change.filePath)
                    val targetUri = if (existingUri != null) {
                        existingUri
                    } else {
                        val mimeType = mimeTypeForFileName(fileName)
                        fs.createFile(parentUri, fileName, mimeType)
                            ?: return Result.failure(IllegalArgumentException("Failed to create file: ${change.filePath}"))
                    }
                    fs.writeTextFile(targetUri, change.proposedContent ?: "")
                    pendingChangeDao.updateStatus(change.id, ChangeStatus.APPLIED.name)
                    Result.success("Created ${change.filePath}")
                }
                ChangeType.DELETE -> {
                    val fileUri = fs.resolveRelativeUri(rootUri, change.filePath)
                        ?: return Result.failure(IllegalArgumentException("File not found: ${change.filePath}"))
                    if (!fs.deleteFile(fileUri)) {
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
            return Result.failure(IllegalStateException("No project is open"))
        }

        return try {
            val orig = change.originalContent
            when (change.changeType) {
                ChangeType.CREATE -> {
                    // Rollback creation: delete the newly created file from disk
                    val fileUri = fs.resolveRelativeUri(rootUri, change.filePath)
                    if (fileUri != null && !fs.deleteFile(fileUri)) {
                        return Result.failure(IOException("Failed to delete ${change.filePath}"))
                    }
                }
                ChangeType.EDIT -> {
                    // Rollback edit: revert file content back to originalContent
                    if (orig == null) {
                        return Result.failure(IllegalStateException("Missing original content for ${change.filePath}"))
                    }
                    val fileUri = fs.resolveRelativeUri(rootUri, change.filePath)
                        ?: return Result.failure(IllegalArgumentException("File not found: ${change.filePath}"))
                    fs.writeTextFile(fileUri, orig)
                }
                ChangeType.DELETE -> {
                    // Rollback deletion: recreate the file with originalContent
                    if (orig == null) {
                        return Result.failure(IllegalStateException("Missing original content for ${change.filePath}"))
                    }
                    val parentUri = resolveOrCreateParent(fs, rootUri, change.filePath)
                        ?: return Result.failure(IllegalArgumentException("Cannot resolve or create parent directory for: ${change.filePath}"))
                    val fileName = change.filePath.substringAfterLast("/")
                    val targetUri = fs.resolveRelativeUri(rootUri, change.filePath)
                        ?: run {
                            val mimeType = mimeTypeForFileName(fileName)
                            fs.createFile(parentUri, fileName, mimeType)
                        }
                        ?: return Result.failure(IOException("Failed to recreate ${change.filePath}"))
                    fs.writeTextFile(targetUri, orig)
                }
            }
            pendingChangeDao.updateStatus(change.id, ChangeStatus.REJECTED.name)
            Result.success("Reverted change for ${change.filePath}")
        } catch (e: Exception) {
            // Leave the change PENDING so the user can retry after the rollback failure.
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
