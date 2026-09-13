package com.codeagent.core.agent

import android.net.Uri
import com.codeagent.core.data.PendingChangeDao
import com.codeagent.core.data.PendingChangeEntity
import com.codeagent.core.files.ProjectFileSystem
import com.codeagent.core.model.ChangeStatus
import com.codeagent.core.model.ChangeType
import com.codeagent.core.model.PendingChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

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
                    val newUri = fs.createFile(parentUri, fileName, "text/plain")
                        ?: return Result.failure(IllegalArgumentException("Failed to create file: ${change.filePath}"))
                    fs.writeTextFile(newUri, change.proposedContent ?: "")
                    pendingChangeDao.updateStatus(change.id, ChangeStatus.APPLIED.name)
                    Result.success("Created ${change.filePath}")
                }
                ChangeType.DELETE -> {
                    val fileUri = fs.resolveRelativeUri(rootUri, change.filePath)
                        ?: return Result.failure(IllegalArgumentException("File not found: ${change.filePath}"))
                    fs.deleteFile(fileUri)
                    pendingChangeDao.updateStatus(change.id, ChangeStatus.APPLIED.name)
                    Result.success("Deleted ${change.filePath}")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reject(change: PendingChange) {
        pendingChangeDao.updateStatus(change.id, ChangeStatus.REJECTED.name)
    }

    suspend fun approveAll(sessionId: String): List<Result<String>> {
        val entities = pendingChangeDao.getPendingBySession(sessionId).first()
        val changes = entities.map { it.toModel() }
        return changes.map { approve(it) }
    }

    suspend fun rejectAll(sessionId: String) {
        val entities = pendingChangeDao.getPendingBySession(sessionId).first()
        entities.forEach { entity ->
            pendingChangeDao.updateStatus(entity.id, ChangeStatus.REJECTED.name)
        }
    }

    private suspend fun resolveOrCreateParent(fs: ProjectFileSystem, rootUri: Uri, relPath: String): Uri? {
        val segments = relPath.split("/").filter { it.isNotEmpty() }
        if (segments.size <= 1) return rootUri
        val parentSegments = segments.dropLast(1)
        var currentUri = rootUri
        for (segment in parentSegments) {
            val children = fs.listChildren(currentUri)
            val existing = children.find { it.name == segment }
            if (existing != null) {
                if (!existing.isDirectory) return null
                currentUri = existing.uri
            } else {
                currentUri = fs.createDirectory(currentUri, segment) ?: return null
            }
        }
        return currentUri
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
