package com.codeagent.core.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.codeagent.core.model.ChangeStatus
import com.codeagent.core.model.ChangeType
import com.codeagent.core.model.MessageRole

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val treeUri: String,
    val lastOpened: Long
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("sessionId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val toolCallsJson: String?,
    val toolCallId: String?,
    val timestamp: Long
)

@Entity(
    tableName = "pending_changes",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("sessionId")]
)
data class PendingChangeEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val filePath: String,
    val changeType: String,
    val originalContent: String?,
    val proposedContent: String?,
    val createdAt: Long,
    val status: String
)

@Entity(tableName = "provider_configs")
data class ProviderConfigEntity(
    @PrimaryKey val id: String,
    val providerType: String,
    val name: String,
    val model: String,
    val baseUrl: String,
    val temperature: Float,
    val maxTokens: Int,
    val systemPrompt: String?,
    val isDefault: Boolean
)
