package com.codeagent.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeagent.core.agent.AgentContext
import com.codeagent.core.agent.AgentOrchestrator
import com.codeagent.core.agent.PendingChangeManager
import com.codeagent.core.agent.ToolExecutor
import com.codeagent.core.ai.ChatMessage
import com.codeagent.core.data.MessageDao
import com.codeagent.core.data.MessageEntity
import com.codeagent.core.data.PendingChangeDao
import com.codeagent.core.data.ProjectDao
import com.codeagent.core.data.SessionDao
import com.codeagent.core.data.SessionEntity
import android.util.Log
import com.codeagent.core.data.SettingsRepository
import com.codeagent.core.files.SafProjectFileSystem
import com.codeagent.core.model.ChangeStatus
import com.codeagent.core.model.Message
import com.codeagent.core.model.MessageRole
import com.codeagent.core.model.PendingChange
import com.codeagent.core.model.ToolCallData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

data class ChatState(
    val sessionId: String? = null,
    val messages: List<Message> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val pendingChanges: List<PendingChange> = emptyList(),
    val error: String? = null,
    val model: String = "gpt-4o",
    val projectUri: Uri? = null,
    val projectName: String = "",
    val projectId: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentOrchestrator: AgentOrchestrator,
    private val toolExecutor: ToolExecutor,
    private val pendingChangeManager: PendingChangeManager,
    private val projectDao: ProjectDao,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val pendingChangeDao: PendingChangeDao,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var chatHistory: List<ChatMessage> = emptyList()
    private var agentJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.getActiveProviderFlow().collect { config ->
                if (config != null && config.model.isNotBlank()) {
                    _state.value = _state.value.copy(model = config.model)
                }
            }
        }
    }

    fun openProject(treeUri: Uri, name: String, projectId: String) {
        val fs = SafProjectFileSystem(context)
        toolExecutor.bind(fs, treeUri)
        pendingChangeManager.bind(fs, treeUri)

        _state.value = _state.value.copy(
            projectUri = treeUri,
            projectName = name,
            projectId = projectId
        )

        // Try to restore last session for this project, or create new
        viewModelScope.launch {
            val existing = sessionDao.getByProject(projectId)
            val lastSession = existing.first().firstOrNull()
            if (lastSession != null) {
                restoreSession(lastSession.id, treeUri, name, projectId)
            } else {
                createNewSession(treeUri, name, projectId)
            }
        }
    }

    fun openProjectById(projectId: String) {
        viewModelScope.launch {
            val entity = projectDao.getById(projectId)
            if (entity != null) {
                val treeUri = Uri.parse(entity.treeUri)
                openProject(treeUri, entity.name, projectId)
            }
        }
    }

    private suspend fun createNewSession(treeUri: Uri, name: String, projectId: String) {
        val sessionId = java.util.UUID.randomUUID().toString()
        _state.value = _state.value.copy(
            sessionId = sessionId,
            messages = emptyList(),
            pendingChanges = emptyList(),
            error = null
        )
        chatHistory = emptyList()
        sessionDao.upsert(
            SessionEntity(
                id = sessionId,
                projectId = projectId,
                title = "Chat with $name",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun restoreSession(sessionId: String, treeUri: Uri, name: String, projectId: String) {
        _state.value = _state.value.copy(
            sessionId = sessionId,
            messages = emptyList(),
            pendingChanges = emptyList(),
            error = null
        )
        chatHistory = emptyList()

        // Load persisted messages
        val entities = messageDao.getBySessionList(sessionId)
        val restoredMessages = entities.map { entity ->
            val toolCalls = entity.toolCallsJson?.let { parseToolCalls(it) } ?: emptyList()
            Message(
                id = entity.id,
                sessionId = entity.sessionId,
                role = try { MessageRole.valueOf(entity.role) } catch (_: Exception) { MessageRole.ASSISTANT },
                content = entity.content,
                toolCalls = toolCalls,
                toolCallId = entity.toolCallId,
                timestamp = entity.timestamp
            )
        }

        // Rebuild chat history from persisted messages
        chatHistory = restoredMessages.map { msg ->
            ChatMessage(
                role = when (msg.role) {
                    MessageRole.SYSTEM -> ChatMessage.Role.SYSTEM
                    MessageRole.USER -> ChatMessage.Role.USER
                    MessageRole.ASSISTANT -> ChatMessage.Role.ASSISTANT
                    MessageRole.TOOL -> ChatMessage.Role.TOOL
                },
                content = msg.content,
                toolCalls = msg.toolCalls.map { tc ->
                    com.codeagent.core.ai.ToolCall(id = tc.id, name = tc.name, arguments = tc.arguments)
                },
                toolCallId = msg.toolCallId
            )
        }

        // Load pending changes
        val pendingEntities = pendingChangeDao.getPendingBySessionList(sessionId)
        val restoredPending = pendingEntities.map { entity ->
            PendingChange(
                id = entity.id,
                sessionId = entity.sessionId,
                filePath = entity.filePath,
                changeType = com.codeagent.core.model.ChangeType.valueOf(entity.changeType),
                originalContent = entity.originalContent,
                proposedContent = entity.proposedContent,
                createdAt = entity.createdAt,
                status = ChangeStatus.valueOf(entity.status)
            )
        }

        _state.value = _state.value.copy(
            messages = restoredMessages,
            pendingChanges = restoredPending
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _state.value.isStreaming) return

        val userMsg = Message(
            id = java.util.UUID.randomUUID().toString(),
            sessionId = _state.value.sessionId ?: "local",
            role = MessageRole.USER,
            content = text,
            timestamp = System.currentTimeMillis()
        )

        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg,
            isStreaming = true,
            streamingText = "",
            error = null
        )

        persistMessage(userMsg)

        agentJob = viewModelScope.launch {
            try {
                val sessionId = _state.value.sessionId ?: "local"
                val activeConfig = settingsRepository.getActiveProvider()
                val currentModel = activeConfig?.model?.takeIf { it.isNotBlank() } ?: _state.value.model

                val streamBuffer = StringBuilder()
                var lastDeltaTime = 0L

                val result = agentOrchestrator.run(
                    userMessage = text,
                    context = AgentContext(
                        sessionId = sessionId,
                        projectId = _state.value.projectId,
                        history = chatHistory
                    ),
                    model = currentModel,
                    systemPrompt = "You are a helpful coding assistant working within the project '${_state.value.projectName}'. Use the available tools to read, search, and edit files. When proposing file edits, always show the complete new file content. Be concise and accurate.",
                    onDelta = { delta ->
                        streamBuffer.append(delta)
                        val now = System.currentTimeMillis()
                        if (now - lastDeltaTime >= 60L) {
                            lastDeltaTime = now
                            _state.value = _state.value.copy(
                                streamingText = streamBuffer.toString()
                            )
                        }
                    }
                )

                val uiMessages = result.newMessages.map { msg ->
                    Message(
                        id = java.util.UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        role = when (msg.role) {
                            ChatMessage.Role.ASSISTANT -> MessageRole.ASSISTANT
                            ChatMessage.Role.TOOL -> MessageRole.TOOL
                            ChatMessage.Role.USER -> MessageRole.USER
                            ChatMessage.Role.SYSTEM -> MessageRole.SYSTEM
                        },
                        content = msg.content,
                        toolCalls = msg.toolCalls.map { tc ->
                            ToolCallData(id = tc.id, name = tc.name, arguments = tc.arguments)
                        },
                        toolCallId = msg.toolCallId,
                        timestamp = System.currentTimeMillis()
                    )
                }

                chatHistory = chatHistory + listOf(ChatMessage(ChatMessage.Role.USER, text)) + result.newMessages

                for (change in result.pendingChanges) {
                    pendingChangeManager.addChange(change.copy(sessionId = sessionId))
                }

                _state.value = _state.value.copy(
                    messages = _state.value.messages + uiMessages,
                    isStreaming = false,
                    streamingText = "",
                    pendingChanges = _state.value.pendingChanges + result.pendingChanges.map { it.copy(sessionId = sessionId) }
                )

                for (msg in uiMessages) {
                    persistMessage(msg)
                }

                // Update session timestamp
                _state.value.sessionId?.let { sid ->
                    sessionDao.upsert(
                        SessionEntity(
                            id = sid,
                            projectId = _state.value.projectId,
                            title = "Chat with ${_state.value.projectName}",
                            createdAt = sessionDao.getById(sid)?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = _state.value.copy(
                    isStreaming = false,
                    streamingText = "",
                    error = "Generation cancelled"
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error running agent", e)
                _state.value = _state.value.copy(
                    isStreaming = false,
                    streamingText = "",
                    error = "Agent error: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun cancelGeneration() {
        agentJob?.cancel()
        agentJob = null
    }

    fun approveChange(change: PendingChange) {
        viewModelScope.launch {
            val result = pendingChangeManager.approve(change)
            if (result.isFailure) {
                _state.value = _state.value.copy(
                    error = "Failed to apply change: ${result.exceptionOrNull()?.message}"
                )
            } else {
                _state.value = _state.value.copy(
                    pendingChanges = _state.value.pendingChanges.filter { it.id != change.id }
                )
            }
        }
    }

    fun rejectChange(change: PendingChange) {
        viewModelScope.launch {
            pendingChangeManager.reject(change)
            _state.value = _state.value.copy(
                pendingChanges = _state.value.pendingChanges.filter { it.id != change.id }
            )
        }
    }

    fun approveAllChanges() {
        val sessionId = _state.value.sessionId ?: return
        viewModelScope.launch {
            pendingChangeManager.approveAll(sessionId)
            _state.value = _state.value.copy(pendingChanges = emptyList())
        }
    }

    fun rejectAllChanges() {
        val sessionId = _state.value.sessionId ?: return
        viewModelScope.launch {
            pendingChangeManager.rejectAll(sessionId)
            _state.value = _state.value.copy(pendingChanges = emptyList())
        }
    }

    fun setModel(model: String) {
        _state.value = _state.value.copy(model = model)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun persistMessage(message: Message) {
        val sessionId = _state.value.sessionId ?: return
        viewModelScope.launch {
            val toolCallsJson = if (message.toolCalls.isNotEmpty()) {
                Json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.json.JsonElement.serializer()),
                    message.toolCalls.map { tc ->
                        buildJsonObject {
                            put("id", tc.id)
                            put("name", tc.name)
                            put("arguments", tc.arguments)
                            if (tc.result != null) put("result", tc.result)
                        }
                    }
                )
            } else null

            messageDao.insert(
                MessageEntity(
                    id = message.id,
                    sessionId = sessionId,
                    role = message.role.name,
                    content = message.content,
                    toolCallsJson = toolCallsJson,
                    toolCallId = message.toolCallId,
                    timestamp = message.timestamp
                )
            )
        }
    }

    private fun parseToolCalls(json: String): List<ToolCallData> {
        return try {
            val array = Json.parseToJsonElement(json).jsonArray
            array.map { element ->
                val obj = element.jsonObject
                ToolCallData(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    arguments = obj["arguments"]?.jsonPrimitive?.content ?: "",
                    result = obj["result"]?.jsonPrimitive?.content
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
