package com.codeagent.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeagent.core.agent.AgentContext
import com.codeagent.core.agent.AgentEvent
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
import com.codeagent.core.files.UriPathResolver
import com.codeagent.core.model.ActiveToolExecution
import com.codeagent.core.model.ChangeStatus
import com.codeagent.core.model.ChatSession
import com.codeagent.core.model.Message
import com.codeagent.core.model.MessageRole
import com.codeagent.core.model.PendingChange
import com.codeagent.core.model.ToolCallData
import com.codeagent.core.model.ToolExecutionStatus
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
    val sessionTitle: String = "",
    val sessions: List<ChatSession> = emptyList(),
    val messages: List<Message> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val activeTool: ActiveToolExecution? = null,
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
    private var sessionsJob: Job? = null

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
        val localDir = UriPathResolver.resolveLocalDirectory(context, treeUri, projectId.ifBlank { name })
        toolExecutor.bind(fs, treeUri, localDir)
        pendingChangeManager.bind(fs, treeUri)

        _state.value = _state.value.copy(
            projectUri = treeUri,
            projectName = name,
            projectId = projectId
        )

        // Observe all sessions for this project
        sessionsJob?.cancel()
        sessionsJob = viewModelScope.launch {
            sessionDao.getByProject(projectId).collect { entities ->
                _state.value = _state.value.copy(
                    sessions = entities.map { entity ->
                        ChatSession(
                            id = entity.id,
                            projectId = entity.projectId,
                            title = entity.title,
                            createdAt = entity.createdAt,
                            updatedAt = entity.updatedAt
                        )
                    }
                )
            }
        }

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
        val title = "New Chat"
        _state.value = _state.value.copy(
            sessionId = sessionId,
            sessionTitle = title,
            messages = emptyList(),
            pendingChanges = emptyList(),
            error = null
        )
        chatHistory = emptyList()
        sessionDao.upsert(
            SessionEntity(
                id = sessionId,
                projectId = projectId,
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun restoreSession(sessionId: String, treeUri: Uri, name: String, projectId: String) {
        val sessionEntity = sessionDao.getById(sessionId)
        val title = sessionEntity?.title ?: "Chat with $name"
        _state.value = _state.value.copy(
            sessionId = sessionId,
            sessionTitle = title,
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

        // Link tool results into preceding assistant tool calls
        val toolResultsById = restoredMessages.filter { it.role == MessageRole.TOOL && it.toolCallId != null }
            .associate { it.toolCallId!! to it.content }

        val linkedMessages = restoredMessages.map { msg ->
            if (msg.role == MessageRole.ASSISTANT && msg.toolCalls.isNotEmpty()) {
                msg.copy(
                    toolCalls = msg.toolCalls.map { tc ->
                        if (tc.result == null && toolResultsById.containsKey(tc.id)) {
                            tc.copy(result = toolResultsById[tc.id])
                        } else tc
                    }
                )
            } else msg
        }

        // Rebuild chat history from persisted messages
        chatHistory = linkedMessages.map { msg ->
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
            messages = linkedMessages,
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

        val isFirstMessage = _state.value.messages.isEmpty()
        val currentTitle = _state.value.sessionTitle
        val generatedTitle = text.trim().lines().firstOrNull()?.take(40)?.trimEnd() ?: "New Chat"
        val shouldAutoTitle = isFirstMessage && (currentTitle.isBlank() || currentTitle == "New Chat" || currentTitle.startsWith("Chat with "))
        val activeTitle = if (shouldAutoTitle) generatedTitle else currentTitle.ifBlank { "New Chat" }

        if (shouldAutoTitle) {
            _state.value = _state.value.copy(sessionTitle = activeTitle)
            _state.value.sessionId?.let { sid ->
                viewModelScope.launch {
                    sessionDao.updateTitle(sid, activeTitle)
                }
            }
        }

        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg,
            isStreaming = true,
            streamingText = "",
            activeTool = null,
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

                val effectiveSystemPrompt = buildString {
                    append("You are an expert coding assistant working within the project '${_state.value.projectName}'.\n")
                    append("Use the available tools to inspect, execute commands, and modify files.\n\n")
                    append("FILE EDITING RULES:\n")
                    append("1. Always use `read_file` to read the file before editing it.\n")
                    append("2. When calling `propose_file_edit`:\n")
                    append("   - For partial/targeted edits, specify `old_content` with the exact code snippet to replace, and `content` with the replacement code. This preserves all surrounding code.\n")
                    append("   - If `old_content` is omitted, `content` MUST be the 100% complete new file content including all unchanged lines. NEVER truncate unchanged code or use placeholders like '// ... existing code ...'.\n")
                    append("3. Always preserve existing code structure and indentation.\n")
                    val custom = activeConfig?.systemPrompt?.takeIf { it.isNotBlank() }
                    if (custom != null) {
                        append("\nUser Instructions:\n$custom\n")
                    }
                }

                val result = agentOrchestrator.run(
                    userMessage = text,
                    context = AgentContext(
                        sessionId = sessionId,
                        projectId = _state.value.projectId,
                        history = chatHistory
                    ),
                    model = currentModel,
                    systemPrompt = effectiveSystemPrompt,
                    onEvent = { event ->
                        when (event) {
                            is AgentEvent.TextDelta -> {
                                streamBuffer.append(event.text)
                                val now = System.currentTimeMillis()
                                if (now - lastDeltaTime >= 40L) {
                                    lastDeltaTime = now
                                    _state.value = _state.value.copy(
                                        streamingText = streamBuffer.toString()
                                    )
                                }
                            }
                            is AgentEvent.ToolCallStart -> {
                                streamBuffer.clear()
                                _state.value = _state.value.copy(
                                    activeTool = ActiveToolExecution(
                                        id = event.toolCallId,
                                        name = event.toolName,
                                        arguments = event.arguments,
                                        status = ToolExecutionStatus.RUNNING,
                                        output = "",
                                        startTime = System.currentTimeMillis()
                                    ),
                                    streamingText = ""
                                )
                            }
                            is AgentEvent.ToolCallOutputChunk -> {
                                val current = _state.value.activeTool
                                if (current != null && current.id == event.toolCallId) {
                                    val newOutput = if (current.output.isEmpty()) event.chunk else "${current.output}\n${event.chunk}"
                                    _state.value = _state.value.copy(
                                        activeTool = current.copy(output = newOutput)
                                    )
                                }
                            }
                            is AgentEvent.ToolCallComplete -> {
                                val current = _state.value.activeTool
                                if (current != null && current.id == event.toolCallId) {
                                    _state.value = _state.value.copy(
                                        activeTool = current.copy(
                                            status = if (event.success) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.ERROR,
                                            output = event.output,
                                            durationMs = event.durationMs
                                        )
                                    )
                                }
                            }
                            is AgentEvent.MessageAdded -> {
                                val msg = event.message
                                val uiMsg = Message(
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
                                        ToolCallData(
                                            id = tc.id,
                                            name = tc.name,
                                            arguments = tc.arguments,
                                            result = if (msg.role == ChatMessage.Role.ASSISTANT) null else msg.content
                                        )
                                    },
                                    toolCallId = msg.toolCallId,
                                    timestamp = System.currentTimeMillis()
                                )

                                val updatedMessages = if (uiMsg.role == MessageRole.TOOL && uiMsg.toolCallId != null) {
                                    _state.value.messages.map { existing ->
                                        if (existing.role == MessageRole.ASSISTANT && existing.toolCalls.any { it.id == uiMsg.toolCallId }) {
                                            existing.copy(
                                                toolCalls = existing.toolCalls.map { tc ->
                                                    if (tc.id == uiMsg.toolCallId) tc.copy(result = uiMsg.content) else tc
                                                }
                                            )
                                        } else {
                                            existing
                                        }
                                    } + uiMsg
                                } else {
                                    _state.value.messages + uiMsg
                                }

                                _state.value = _state.value.copy(
                                    messages = updatedMessages,
                                    activeTool = if (uiMsg.role == MessageRole.TOOL) null else _state.value.activeTool,
                                    streamingText = ""
                                )
                                persistMessage(uiMsg)
                            }
                        }
                    }
                )

                chatHistory = chatHistory + listOf(ChatMessage(ChatMessage.Role.USER, text)) + result.newMessages

                for (change in result.pendingChanges) {
                    pendingChangeManager.addChange(change.copy(sessionId = sessionId))
                }

                _state.value = _state.value.copy(
                    isStreaming = false,
                    streamingText = "",
                    activeTool = null,
                    pendingChanges = _state.value.pendingChanges + result.pendingChanges.map { it.copy(sessionId = sessionId) }
                )

                // Update session timestamp and title
                _state.value.sessionId?.let { sid ->
                    sessionDao.updateTitle(
                        id = sid,
                        title = _state.value.sessionTitle.ifBlank { "Chat with ${_state.value.projectName}" },
                        updatedAt = System.currentTimeMillis()
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = _state.value.copy(
                    isStreaming = false,
                    streamingText = "",
                    activeTool = null,
                    error = "Generation cancelled"
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error running agent", e)
                _state.value = _state.value.copy(
                    isStreaming = false,
                    streamingText = "",
                    activeTool = null,
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

    fun startNewSession() {
        val treeUri = _state.value.projectUri ?: return
        val name = _state.value.projectName
        val projectId = _state.value.projectId
        cancelGeneration()
        viewModelScope.launch {
            createNewSession(treeUri, name, projectId)
        }
    }

    fun switchSession(sessionId: String) {
        if (sessionId == _state.value.sessionId) return
        val treeUri = _state.value.projectUri ?: return
        val name = _state.value.projectName
        val projectId = _state.value.projectId
        cancelGeneration()
        viewModelScope.launch {
            restoreSession(sessionId, treeUri, name, projectId)
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            sessionDao.updateTitle(sessionId, trimmed)
            if (_state.value.sessionId == sessionId) {
                _state.value = _state.value.copy(sessionTitle = trimmed)
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val isCurrent = _state.value.sessionId == sessionId
            sessionDao.deleteById(sessionId)
            if (isCurrent) {
                val remaining = _state.value.sessions.filter { it.id != sessionId }
                if (remaining.isNotEmpty()) {
                    val next = remaining.first()
                    val treeUri = _state.value.projectUri ?: return@launch
                    restoreSession(next.id, treeUri, _state.value.projectName, _state.value.projectId)
                } else {
                    val treeUri = _state.value.projectUri ?: return@launch
                    createNewSession(treeUri, _state.value.projectName, _state.value.projectId)
                }
            }
        }
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
