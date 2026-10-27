package com.codeagent.feature.chat

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeagent.core.agent.AgentContext
import com.codeagent.core.agent.AgentEvent
import com.codeagent.core.agent.AgentOrchestrator
import com.codeagent.core.agent.PendingChangeManager
import com.codeagent.core.agent.ToolExecutor
import com.codeagent.core.agent.service.AgentWorkManager
import com.codeagent.core.ai.ChatMessage
import com.codeagent.core.data.MessageDao
import com.codeagent.core.data.MessageEntity
import com.codeagent.core.data.PendingChangeDao
import com.codeagent.core.data.ProjectDao
import com.codeagent.core.data.SessionDao
import com.codeagent.core.data.SessionEntity
import com.codeagent.core.data.SettingsRepository
import com.codeagent.core.files.ProjectFileSystem
import com.codeagent.core.model.ActiveToolExecution
import com.codeagent.core.model.ChangeStatus
import com.codeagent.core.model.ChatSession
import com.codeagent.core.model.Message
import com.codeagent.core.model.MessageRole
import com.codeagent.core.model.PendingChange
import com.codeagent.core.model.ToolCallData
import com.codeagent.core.model.ToolExecutionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID
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
    val projectId: String = "",
    val isGoalMode: Boolean = false,
    val activeStepCount: Int = 0
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentOrchestrator: AgentOrchestrator,
    private val agentWorkManager: AgentWorkManager,
    private val toolExecutor: ToolExecutor,
    private val pendingChangeManager: PendingChangeManager,
    private val projectDao: ProjectDao,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val pendingChangeDao: PendingChangeDao,
    private val settingsRepository: SettingsRepository,
    private val fileSystem: ProjectFileSystem
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var chatHistory: List<ChatMessage> = emptyList()
    private var agentJob: Job? = null
    private var sessionsJob: Job? = null
    private var activeGoalTaskId: String? = null

    init {
        viewModelScope.launch {
            settingsRepository.getActiveProviderFlow().collect { config ->
                if (config != null && config.model.isNotBlank()) {
                    _state.value = _state.value.copy(model = config.model)
                }
            }
        }

        // Ensure an initial session exists so messages are never dropped
        val initialSessionId = UUID.randomUUID().toString()
        _state.value = _state.value.copy(sessionId = initialSessionId, sessionTitle = "New Chat")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sessionDao.upsert(
                    SessionEntity(
                        id = initialSessionId,
                        projectId = "",
                        title = "New Chat",
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.w("ChatViewModel", "Could not initialize default session", e)
            }
        }

        // Observe background service tasks (Goal mode or background runs)
        viewModelScope.launch {
            agentWorkManager.activeTaskState.collect { taskState ->
                if (taskState == null) return@collect
                val currentSessionId = _state.value.sessionId ?: return@collect
                if (taskState.sessionId == currentSessionId) {
                    val isNowRunning = taskState.isRunning
                    val currentGoalId = activeGoalTaskId

                    // Ignore stale/completed tasks from previous runs if they don't match activeGoalTaskId
                    if (!isNowRunning && currentGoalId != null && taskState.taskId != currentGoalId) {
                        return@collect
                    }
                    if (!isNowRunning && currentGoalId == taskState.taskId) {
                        activeGoalTaskId = null
                    }

                    _state.update { current ->
                        val newPending = if (!isNowRunning && taskState.completedSuccessfully == true && taskState.pendingChanges.isNotEmpty()) {
                            val existingIds = current.pendingChanges.map { it.id }.toSet()
                            val toAdd = taskState.pendingChanges.filter { it.id !in existingIds }
                            current.pendingChanges + toAdd
                        } else {
                            current.pendingChanges
                        }

                        current.copy(
                            isStreaming = isNowRunning,
                            isGoalMode = isNowRunning && taskState.isGoalMode,
                            activeStepCount = taskState.stepCount,
                            streamingText = if (isNowRunning) {
                                if (taskState.streamingDelta.isNotBlank()) taskState.streamingDelta else current.streamingText
                            } else "",
                            activeTool = if (!isNowRunning) null else current.activeTool,
                            error = if (!isNowRunning && taskState.completedSuccessfully == false) taskState.error else current.error,
                            pendingChanges = newPending
                        )
                    }

                    if (!isNowRunning && taskState.completedSuccessfully == true) {
                        try {
                            sessionDao.updateTitle(
                                id = currentSessionId,
                                title = _state.value.sessionTitle.ifBlank { "Chat with ${_state.value.projectName}" },
                                updatedAt = System.currentTimeMillis()
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        // Observe live events from background work manager
        viewModelScope.launch {
            val streamBuffer = StringBuilder()
            var lastDeltaTime = 0L
            val toolOutputBuffer = StringBuilder()
            var lastToolChunkTime = 0L
            val maxToolOutputPreview = 24_000

            fun flushStreamBuffer() {
                if (streamBuffer.isNotEmpty()) {
                    val flushed = streamBuffer.toString()
                    _state.update { it.copy(streamingText = flushed) }
                }
            }

            agentWorkManager.liveEvents.collect { event ->
                val currentSessionId = _state.value.sessionId ?: return@collect
                val activeTask = agentWorkManager.activeTaskState.value
                if (activeTask != null && activeTask.sessionId != currentSessionId) return@collect

                when (event) {
                    is AgentEvent.TextDelta -> {
                        streamBuffer.append(event.text)
                        val now = System.currentTimeMillis()
                        if (now - lastDeltaTime >= 40L) {
                            lastDeltaTime = now
                            val textSnapshot = streamBuffer.toString()
                            _state.update { it.copy(streamingText = textSnapshot) }
                        }
                    }
                    is AgentEvent.ToolCallStart -> {
                        flushStreamBuffer()
                        streamBuffer.clear()
                        toolOutputBuffer.clear()
                        _state.update {
                            it.copy(
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
                    }
                    is AgentEvent.ToolCallOutputChunk -> {
                        toolOutputBuffer.append(event.chunk).append('\n')
                        val now = System.currentTimeMillis()
                        if (now - lastToolChunkTime >= 60L) {
                            lastToolChunkTime = now
                            val currentStr = if (toolOutputBuffer.length > maxToolOutputPreview) {
                                toolOutputBuffer.substring(toolOutputBuffer.length - maxToolOutputPreview)
                            } else {
                                toolOutputBuffer.toString()
                            }
                            _state.update { state ->
                                val cur = state.activeTool
                                if (cur != null && cur.id == event.toolCallId) {
                                    state.copy(activeTool = cur.copy(output = currentStr))
                                } else state
                            }
                        }
                    }
                    is AgentEvent.ToolCallComplete -> {
                        toolOutputBuffer.clear()
                        _state.update { s ->
                            val cur = s.activeTool
                            if (cur != null && cur.id == event.toolCallId) {
                                s.copy(
                                    activeTool = cur.copy(
                                        status = if (event.success) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.ERROR,
                                        output = event.output,
                                        durationMs = event.durationMs
                                    )
                                )
                            } else s
                        }
                    }
                    is AgentEvent.MessageAdded -> {
                        flushStreamBuffer()
                        streamBuffer.clear()
                        toolOutputBuffer.clear()
                        val msg = event.message
                        val uiMsg = Message(
                            id = UUID.randomUUID().toString(),
                            sessionId = currentSessionId,
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

                        _state.update { state ->
                            val updatedMessages = if (uiMsg.role == MessageRole.TOOL && uiMsg.toolCallId != null) {
                                state.messages.map { existing ->
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
                                state.messages + uiMsg
                            }

                            state.copy(
                                messages = updatedMessages,
                                activeTool = if (uiMsg.role == MessageRole.TOOL) null else state.activeTool,
                                streamingText = ""
                            )
                        }

                        // Keep in-memory chatHistory in sync for ViewModel
                        chatHistory = chatHistory + msg
                    }
                }
            }
        }
    }

    fun openProject(treeUri: Uri, name: String, projectId: String) {
        toolExecutor.bind(fileSystem, treeUri)
        pendingChangeManager.bind(fileSystem, treeUri)

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

        // Restore last session for this project, or create new
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
        val sessionId = UUID.randomUUID().toString()
        val title = "New Chat"
        _state.value = _state.value.copy(
            sessionId = sessionId,
            sessionTitle = title,
            messages = emptyList(),
            pendingChanges = emptyList(),
            error = null,
            isStreaming = false,
            isGoalMode = false,
            activeStepCount = 0
        )
        chatHistory = emptyList()
        try {
            sessionDao.upsert(
                SessionEntity(
                    id = sessionId,
                    projectId = projectId,
                    title = title,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Log.w("ChatViewModel", "Could not upsert new session", e)
        }
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

        // Rebuild in-memory chat history from persisted messages
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

        val activeTask = agentWorkManager.activeTaskState.value
        val isThisTaskRunning = activeTask != null && activeTask.sessionId == sessionId && activeTask.isRunning

        _state.value = _state.value.copy(
            messages = linkedMessages,
            pendingChanges = restoredPending,
            isStreaming = isThisTaskRunning,
            isGoalMode = isThisTaskRunning && activeTask.isGoalMode,
            activeStepCount = if (isThisTaskRunning) activeTask.stepCount else 0,
            streamingText = if (isThisTaskRunning) activeTask.streamingDelta else ""
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _state.value.isStreaming) return

        val isGoal = text.trimStart().startsWith("/goal")
        val cleanGoalPrompt = if (isGoal) text.trimStart().removePrefix("/goal").trim() else text
        val displayPrompt = if (isGoal) cleanGoalPrompt.ifBlank { text } else text

        // Ensure session exists
        val currentSessionId = _state.value.sessionId ?: UUID.randomUUID().toString()
        val isFirstMessage = _state.value.messages.isEmpty()
        val currentTitle = _state.value.sessionTitle
        val generatedTitle = displayPrompt.lines().firstOrNull()?.take(40)?.trimEnd() ?: "New Chat"
        val shouldAutoTitle = isFirstMessage && (currentTitle.isBlank() || currentTitle == "New Chat" || currentTitle.startsWith("Chat with "))
        val activeTitle = if (shouldAutoTitle) generatedTitle else currentTitle.ifBlank { "New Chat" }

        val userMsg = Message(
            id = UUID.randomUUID().toString(),
            sessionId = currentSessionId,
            role = MessageRole.USER,
            content = text,
            timestamp = System.currentTimeMillis()
        )

        // If user typed only "/goal" with no objective, show helpful guidance immediately
        if (isGoal && cleanGoalPrompt.isBlank()) {
            val helpMsg = Message(
                id = UUID.randomUUID().toString(),
                sessionId = currentSessionId,
                role = MessageRole.ASSISTANT,
                content = """
                    **Autonomous Goal Mode (`/goal`)**

                    Goal Mode autonomously formulates plans, executes edits across files, and verifies work with tests until your objective is met.

                    To start, specify your objective after `/goal`, for example:
                    • `/goal Create a README.md explaining the codebase architecture`
                    • `/goal Write unit tests for all domain models`
                    • `/goal Refactor network calls to include retry handling`
                """.trimIndent(),
                timestamp = System.currentTimeMillis()
            )
            _state.update {
                it.copy(
                    sessionId = currentSessionId,
                    sessionTitle = activeTitle,
                    messages = it.messages + userMsg + helpMsg,
                    isStreaming = false,
                    isGoalMode = false,
                    streamingText = "",
                    activeTool = null,
                    error = null
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    if (sessionDao.getById(currentSessionId) == null) {
                        sessionDao.upsert(
                            SessionEntity(
                                id = currentSessionId,
                                projectId = _state.value.projectId,
                                title = activeTitle,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                } catch (_: Exception) {}
                persistMessage(userMsg)
                persistMessage(helpMsg)
            }
            return
        }

        _state.update {
            it.copy(
                sessionId = currentSessionId,
                sessionTitle = activeTitle,
                messages = it.messages + userMsg,
                isStreaming = true,
                isGoalMode = isGoal,
                streamingText = "",
                activeTool = null,
                error = null
            )
        }

        // Upsert session record in Room
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (sessionDao.getById(currentSessionId) == null) {
                    sessionDao.upsert(
                        SessionEntity(
                            id = currentSessionId,
                            projectId = _state.value.projectId,
                            title = activeTitle,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else if (shouldAutoTitle) {
                    sessionDao.updateTitle(currentSessionId, activeTitle)
                }
            } catch (e: Exception) {
                Log.w("ChatViewModel", "Could not upsert session record", e)
            }
        }

        val projectId = _state.value.projectId
        val projectName = _state.value.projectName
        val projectUri = _state.value.projectUri

        if (isGoal) {
            // Milestone 14: Background Headless Goal Mode Execution
            agentJob?.cancel()
            agentJob = null
            persistMessage(userMsg)
            chatHistory = chatHistory + ChatMessage(ChatMessage.Role.USER, cleanGoalPrompt)

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val activeConfig = settingsRepository.getActiveProvider()
                    val currentModel = activeConfig?.model?.takeIf { it.isNotBlank() } ?: _state.value.model
                    val custom = activeConfig?.systemPrompt?.takeIf { it.isNotBlank() }
                    val effectiveSystemPrompt = buildSystemPrompt(projectName, custom)

                    val taskId = agentWorkManager.startTask(
                        sessionId = currentSessionId,
                        projectId = projectId,
                        projectName = projectName,
                        projectUri = projectUri,
                        prompt = cleanGoalPrompt,
                        model = currentModel,
                        isGoalMode = true,
                        systemPrompt = effectiveSystemPrompt
                    )
                    activeGoalTaskId = taskId
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Failed to launch goal task", e)
                    _state.update {
                        it.copy(
                            isStreaming = false,
                            isGoalMode = false,
                            error = "Failed to start Goal Mode: ${e.message ?: "Unknown error"}"
                        )
                    }
                }
            }
        } else {
            // Interactive conversational execution directly in ViewModel for smooth real-time streaming
            persistMessage(userMsg)
            chatHistory = chatHistory + ChatMessage(ChatMessage.Role.USER, text)

            agentJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val activeConfig = settingsRepository.getActiveProvider()
                    val currentModel = activeConfig?.model?.takeIf { it.isNotBlank() } ?: _state.value.model
                    val custom = activeConfig?.systemPrompt?.takeIf { it.isNotBlank() }
                    val effectiveSystemPrompt = buildSystemPrompt(projectName, custom)

                    val streamBuffer = StringBuilder()
                    var lastDeltaTime = 0L
                    val toolOutputBuffer = StringBuilder()
                    var lastToolChunkTime = 0L
                    val maxToolOutputPreview = 24_000

                    fun flushStreamBuffer() {
                        if (streamBuffer.isNotEmpty()) {
                            val flushed = streamBuffer.toString()
                            _state.update { it.copy(streamingText = flushed) }
                        }
                    }

                    val result = agentOrchestrator.run(
                        userMessage = text,
                        context = AgentContext(
                            sessionId = currentSessionId,
                            projectId = projectId,
                            history = chatHistory.dropLast(1)
                        ),
                        model = currentModel,
                        systemPrompt = effectiveSystemPrompt,
                        isGoalMode = false,
                        onEvent = { event ->
                            when (event) {
                                is AgentEvent.TextDelta -> {
                                    streamBuffer.append(event.text)
                                    val now = System.currentTimeMillis()
                                    if (now - lastDeltaTime >= 50L) {
                                        lastDeltaTime = now
                                        val textSnapshot = streamBuffer.toString()
                                        _state.update { it.copy(streamingText = textSnapshot) }
                                    }
                                }
                                is AgentEvent.ToolCallStart -> {
                                    flushStreamBuffer()
                                    streamBuffer.clear()
                                    toolOutputBuffer.clear()
                                    _state.update {
                                        it.copy(
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
                                }
                                is AgentEvent.ToolCallOutputChunk -> {
                                    toolOutputBuffer.append(event.chunk).append('\n')
                                    val now = System.currentTimeMillis()
                                    if (now - lastToolChunkTime >= 60L) {
                                        lastToolChunkTime = now
                                        val currentStr = if (toolOutputBuffer.length > maxToolOutputPreview) {
                                            toolOutputBuffer.substring(toolOutputBuffer.length - maxToolOutputPreview)
                                        } else {
                                            toolOutputBuffer.toString()
                                        }
                                        _state.update { state ->
                                            val current = state.activeTool
                                            if (current != null && current.id == event.toolCallId) {
                                                state.copy(activeTool = current.copy(output = currentStr))
                                            } else state
                                        }
                                    }
                                }
                                is AgentEvent.ToolCallComplete -> {
                                    toolOutputBuffer.clear()
                                    _state.update { state ->
                                        val current = state.activeTool
                                        if (current != null && current.id == event.toolCallId) {
                                            state.copy(
                                                activeTool = current.copy(
                                                    status = if (event.success) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.ERROR,
                                                    output = event.output,
                                                    durationMs = event.durationMs
                                                )
                                            )
                                        } else state
                                    }
                                }
                                is AgentEvent.MessageAdded -> {
                                    streamBuffer.clear()
                                    toolOutputBuffer.clear()
                                    val msg = event.message
                                    val uiMsg = Message(
                                        id = UUID.randomUUID().toString(),
                                        sessionId = currentSessionId,
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

                                    _state.update { state ->
                                        val updatedMessages = if (uiMsg.role == MessageRole.TOOL && uiMsg.toolCallId != null) {
                                            state.messages.map { existing ->
                                                if (existing.role == MessageRole.ASSISTANT && existing.toolCalls.any { it.id == uiMsg.toolCallId }) {
                                                    existing.copy(
                                                        toolCalls = existing.toolCalls.map { tc ->
                                                            if (tc.id == uiMsg.toolCallId) tc.copy(result = uiMsg.content) else tc
                                                        }
                                                    )
                                                } else existing
                                            } + uiMsg
                                        } else {
                                            state.messages + uiMsg
                                        }

                                        state.copy(
                                            messages = updatedMessages,
                                            activeTool = if (uiMsg.role == MessageRole.TOOL) null else state.activeTool,
                                            streamingText = ""
                                        )
                                    }
                                    persistMessage(uiMsg)
                                }
                            }
                        }
                    )

                    chatHistory = chatHistory + result.newMessages

                    for (change in result.pendingChanges) {
                        pendingChangeManager.addChange(change.copy(sessionId = currentSessionId))
                    }

                    _state.update {
                        it.copy(
                            isStreaming = false,
                            streamingText = "",
                            activeTool = null,
                            pendingChanges = it.pendingChanges + result.pendingChanges.map { ch -> ch.copy(sessionId = currentSessionId) }
                        )
                    }

                    try {
                        sessionDao.updateTitle(
                            id = currentSessionId,
                            title = _state.value.sessionTitle.ifBlank { "Chat with ${_state.value.projectName}" },
                            updatedAt = System.currentTimeMillis()
                        )
                    } catch (_: Exception) {}

                } catch (e: kotlinx.coroutines.CancellationException) {
                    _state.update {
                        it.copy(
                            isStreaming = false,
                            streamingText = "",
                            activeTool = null,
                            error = "Generation cancelled"
                        )
                    }
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error running agent", e)
                    _state.update {
                        it.copy(
                            isStreaming = false,
                            streamingText = "",
                            activeTool = null,
                            error = "Agent error: ${e.message ?: "Unknown error"}"
                        )
                    }
                }
            }
        }
    }

    private fun buildSystemPrompt(projectName: String, custom: String?): String {
        return buildString {
            if (projectName.isNotBlank()) {
                append("You are an expert coding assistant working within the project '$projectName'.\n")
            } else {
                append("You are an expert coding assistant.\n")
            }
            append("Use the available tools to inspect, execute commands, and modify files.\n\n")
            append("FILE EDITING RULES:\n")
            append("1. Always use `read_file` to read the file before editing it.\n")
            append("2. When calling `propose_file_edit`:\n")
            append("   - For partial/targeted edits, specify `old_content` with the exact code snippet to replace, and `content` with the replacement code. This preserves all surrounding code.\n")
            append("   - If `old_content` is omitted, `content` MUST be the 100% complete new file content including all unchanged lines. NEVER truncate unchanged code or use placeholders like '// ... existing code ...'.\n")
            append("3. Always preserve existing code structure and indentation.\n")
            append("\nON-DEVICE BUILD & ENVIRONMENT CONSTRAINTS:\n")
            append("- Platform: Android device running Ubuntu 24.04 LTS (PRoot Linux userland).\n")
            append("- Architecture: Always check `uname -m`. If running on aarch64/arm64, standard AGP downloads x86_64 AAPT2 which will fail with 'Exec format error'.\n")
            append("- Bundled ARM64 AAPT2: A native ARM64 AAPT2 binary is pre-bundled in the repository at `tools/bin/arm64-v8a/aapt2`. Gradle automatically detects and uses it via `android.aapt2FromMavenOverride`.\n")
            append("- Pre-Build Inspection Checklist (when asked to build or compile on device):\n")
            append("  1. Run `./scripts/check-env.sh` (or `./build.sh --check`) to inspect available tools, SDK, AAPT2, and RAM.\n")
            append("  2. JDK 21: Required. If missing, install via `apt update && apt install -y openjdk-21-jdk-headless`.\n")
            append("  3. Android SDK: Ensure `ANDROID_HOME` or `local.properties` points to an SDK with API 37 (`platforms/android-37/android.jar`).\n")
            append("  4. Memory Safety: To prevent Android Low Memory Killer (LMK) aborts, always use `./build.sh` or pass `--no-daemon -Dorg.gradle.parallel=false -Dorg.gradle.workers.max=2` when running Gradle.\n")
            if (custom != null) {
                append("\nUser Instructions:\n$custom\n")
            }
        }
    }

    fun cancelGeneration() {
        agentJob?.cancel()
        agentJob = null
        activeGoalTaskId = null
        agentWorkManager.cancelCurrentTask()
        _state.update {
            it.copy(
                isStreaming = false,
                isGoalMode = false,
                streamingText = "",
                activeTool = null,
                error = "Generation cancelled"
            )
        }
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
        val treeUri = _state.value.projectUri
        val name = _state.value.projectName
        val projectId = _state.value.projectId
        cancelGeneration()
        viewModelScope.launch {
            if (treeUri != null) {
                createNewSession(treeUri, name, projectId)
            } else {
                val newSessionId = UUID.randomUUID().toString()
                _state.value = _state.value.copy(
                    sessionId = newSessionId,
                    sessionTitle = "New Chat",
                    messages = emptyList(),
                    pendingChanges = emptyList(),
                    error = null
                )
                chatHistory = emptyList()
                try {
                    sessionDao.upsert(
                        SessionEntity(
                            id = newSessionId,
                            projectId = "",
                            title = "New Chat",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } catch (_: Exception) {}
            }
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
            try {
                sessionDao.updateTitle(sessionId, trimmed)
            } catch (_: Exception) {}
            if (_state.value.sessionId == sessionId) {
                _state.value = _state.value.copy(sessionTitle = trimmed)
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val isCurrent = _state.value.sessionId == sessionId
            try {
                sessionDao.deleteById(sessionId)
            } catch (_: Exception) {}
            if (isCurrent) {
                val remaining = _state.value.sessions.filter { it.id != sessionId }
                if (remaining.isNotEmpty()) {
                    val next = remaining.first()
                    val treeUri = _state.value.projectUri ?: return@launch
                    restoreSession(next.id, treeUri, _state.value.projectName, _state.value.projectId)
                } else {
                    val treeUri = _state.value.projectUri
                    if (treeUri != null) {
                        createNewSession(treeUri, _state.value.projectName, _state.value.projectId)
                    } else {
                        startNewSession()
                    }
                }
            }
        }
    }

    private fun persistMessage(message: Message) {
        val sessionId = _state.value.sessionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure session exists
                if (sessionDao.getById(sessionId) == null) {
                    sessionDao.upsert(
                        SessionEntity(
                            id = sessionId,
                            projectId = _state.value.projectId,
                            title = _state.value.sessionTitle.ifBlank { "New Chat" },
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }

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
            } catch (e: Exception) {
                Log.w("ChatViewModel", "Could not persist message", e)
            }
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
