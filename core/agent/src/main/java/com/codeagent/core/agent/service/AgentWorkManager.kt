package com.codeagent.core.agent.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.codeagent.core.agent.AgentContext
import com.codeagent.core.agent.AgentEvent
import com.codeagent.core.agent.AgentOrchestrator
import com.codeagent.core.agent.PendingChangeManager
import com.codeagent.core.agent.ToolExecutor
import com.codeagent.core.ai.ChatMessage
import com.codeagent.core.data.MessageDao
import com.codeagent.core.data.MessageEntity
import com.codeagent.core.data.PendingChangeDao
import com.codeagent.core.data.PendingChangeEntity
import com.codeagent.core.data.ProjectDao
import com.codeagent.core.data.SessionDao
import com.codeagent.core.files.ProjectFileSystem
import com.codeagent.core.model.ChangeStatus
import com.codeagent.core.model.ChangeType
import com.codeagent.core.model.PendingChange
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class AgentTaskState(
    val taskId: String,
    val sessionId: String,
    val projectId: String,
    val projectName: String,
    val userPrompt: String,
    val isGoalMode: Boolean = false,
    val stepCount: Int = 0,
    val currentAction: String = "Initializing...",
    val streamingDelta: String = "",
    val isRunning: Boolean = true,
    val isCancelled: Boolean = false,
    val completedSuccessfully: Boolean? = null,
    val error: String? = null,
    val startTimeMs: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val pendingChanges: List<PendingChange> = emptyList()
)

@Singleton
class AgentWorkManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val agentOrchestrator: AgentOrchestrator,
    private val toolExecutor: ToolExecutor,
    private val pendingChangeManager: PendingChangeManager,
    private val messageDao: MessageDao,
    private val pendingChangeDao: PendingChangeDao,
    private val sessionDao: SessionDao,
    private val projectDao: ProjectDao,
    private val fileSystem: ProjectFileSystem,
    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _activeTaskState = MutableStateFlow<AgentTaskState?>(null)
    val activeTaskState: StateFlow<AgentTaskState?> = _activeTaskState.asStateFlow()

    private val _liveEvents = MutableSharedFlow<AgentEvent>(
        extraBufferCapacity = 1024,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val liveEvents: SharedFlow<AgentEvent> = _liveEvents.asSharedFlow()

    private var activeJob: Job? = null
    val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startTask(
        sessionId: String,
        projectId: String,
        projectName: String,
        projectUri: Uri?,
        prompt: String,
        model: String,
        isGoalMode: Boolean = false,
        systemPrompt: String? = null,
        temperature: Float = 0.7f
    ): String {
        cancelCurrentTask()

        val taskId = UUID.randomUUID().toString()
        val initialTask = AgentTaskState(
            taskId = taskId,
            sessionId = sessionId,
            projectId = projectId,
            projectName = projectName,
            userPrompt = prompt,
            isGoalMode = isGoalMode,
            stepCount = 0,
            currentAction = if (isGoalMode) "Starting autonomous goal..." else "Thinking...",
            isRunning = true,
            startTimeMs = System.currentTimeMillis()
        )
        _activeTaskState.value = initialTask

        if (projectUri != null) {
            toolExecutor.bind(fileSystem, projectUri)
            pendingChangeManager.bind(fileSystem, projectUri)
        }

        try {
            CodeAgentWorkService.startWork(context, taskId, projectName, prompt, isGoalMode)
        } catch (e: Throwable) {
            Log.w("AgentWorkManager", "Could not start CodeAgentWorkService", e)
        }

        activeJob = coroutineScope.launch(ioDispatcher) {
            executeTask(initialTask, model, temperature, systemPrompt)
        }

        return taskId
    }

    fun cancelCurrentTask() {
        activeJob?.cancel()
        activeJob = null
        val current = _activeTaskState.value
        if (current != null && current.isRunning) {
            val duration = System.currentTimeMillis() - current.startTimeMs
            _activeTaskState.value = current.copy(
                isRunning = false,
                isCancelled = true,
                currentAction = "Cancelled by user",
                durationMs = duration
            )
        }
        try {
            CodeAgentWorkService.cancelWork(context)
        } catch (_: Throwable) {}
    }

    private suspend fun executeTask(
        task: AgentTaskState,
        model: String,
        temperature: Float,
        systemPrompt: String?
    ) {
        val streamBuffer = StringBuilder()

        try {
            // Reconstruct chat history from DB
            val existingEntities = messageDao.getBySessionList(task.sessionId)
            val history = existingEntities.map { entity ->
                val toolCalls = parseToolCalls(entity.toolCallsJson)
                ChatMessage(
                    role = when (entity.role) {
                        "USER" -> ChatMessage.Role.USER
                        "ASSISTANT" -> ChatMessage.Role.ASSISTANT
                        "TOOL" -> ChatMessage.Role.TOOL
                        "SYSTEM" -> ChatMessage.Role.SYSTEM
                        else -> ChatMessage.Role.USER
                    },
                    content = entity.content,
                    toolCalls = toolCalls,
                    toolCallId = entity.toolCallId
                )
            }

            // Ensure session exists in DB to prevent foreign key violations
            try {
                if (sessionDao.getById(task.sessionId) == null) {
                    sessionDao.upsert(
                        com.codeagent.core.data.SessionEntity(
                            id = task.sessionId,
                            projectId = task.projectId,
                            title = task.userPrompt.lines().firstOrNull()?.take(40)?.trim()?.ifBlank { "New Chat" } ?: "New Chat",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w("AgentWorkManager", "Failed to ensure session entity", e)
            }

            // Persist the user message
            val userMsgEntity = MessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = task.sessionId,
                role = "USER",
                content = task.userPrompt,
                toolCallsJson = null,
                toolCallId = null,
                timestamp = System.currentTimeMillis()
            )
            try {
                messageDao.insert(userMsgEntity)
            } catch (e: Exception) {
                Log.w("AgentWorkManager", "Failed to insert user message entity", e)
            }

            val contextObj = AgentContext(
                sessionId = task.sessionId,
                projectId = task.projectId,
                history = history
            )

            var stepCounter = 0

            val result = agentOrchestrator.run(
                userMessage = task.userPrompt,
                context = contextObj,
                model = model,
                temperature = temperature,
                systemPrompt = systemPrompt,
                isGoalMode = task.isGoalMode,
                onDelta = { delta ->
                    streamBuffer.append(delta)
                    val currentText = streamBuffer.toString()
                    _activeTaskState.update { it?.copy(streamingDelta = currentText) }
                },
                onEvent = { event ->
                    _liveEvents.tryEmit(event)
                    when (event) {
                        is AgentEvent.ToolCallStart -> {
                            stepCounter++
                            val actionDesc = formatToolAction(event.toolName, event.arguments)
                            _activeTaskState.update { current ->
                                current?.copy(
                                    stepCount = stepCounter,
                                    currentAction = actionDesc
                                )
                            }
                        }
                        is AgentEvent.ToolCallComplete -> {
                            _activeTaskState.update { current ->
                                current?.copy(currentAction = "Completed step $stepCounter")
                            }
                        }
                        is AgentEvent.MessageAdded -> {
                            val msg = event.message
                            val entity = MessageEntity(
                                id = UUID.randomUUID().toString(),
                                sessionId = task.sessionId,
                                role = when (msg.role) {
                                    ChatMessage.Role.USER -> "USER"
                                    ChatMessage.Role.ASSISTANT -> "ASSISTANT"
                                    ChatMessage.Role.TOOL -> "TOOL"
                                    ChatMessage.Role.SYSTEM -> "SYSTEM"
                                },
                                content = msg.content,
                                toolCallsJson = serializeToolCalls(msg.toolCalls),
                                toolCallId = msg.toolCallId,
                                timestamp = System.currentTimeMillis()
                            )
                            coroutineScope.launch(ioDispatcher) {
                                try {
                                    messageDao.insert(entity)
                                } catch (e: Exception) {
                                    Log.w("AgentWorkManager", "Failed to insert message entity", e)
                                }
                            }
                        }
                        else -> {}
                    }
                }
            )

            // Persist pending changes
            for (pc in result.pendingChanges) {
                try {
                    val pcEntity = PendingChangeEntity(
                        id = pc.id,
                        sessionId = task.sessionId,
                        filePath = pc.filePath,
                        changeType = pc.changeType.name,
                        originalContent = pc.originalContent,
                        proposedContent = pc.proposedContent,
                        createdAt = pc.createdAt,
                        status = pc.status.name
                    )
                    pendingChangeDao.upsert(pcEntity)
                } catch (e: Exception) {
                    Log.w("AgentWorkManager", "Failed to upsert pending change entity", e)
                }
            }

            val duration = System.currentTimeMillis() - task.startTimeMs
            _activeTaskState.update { current ->
                current?.copy(
                    isRunning = false,
                    completedSuccessfully = true,
                    currentAction = "Completed in ${formatDuration(duration)}",
                    durationMs = duration,
                    pendingChanges = result.pendingChanges
                )
            }

            try {
                CodeAgentWorkService.completeWork(
                    context = context,
                    projectName = task.projectName,
                    changesCount = result.pendingChanges.size,
                    durationMs = duration
                )
            } catch (_: Throwable) {}

        } catch (e: CancellationException) {
            val duration = System.currentTimeMillis() - task.startTimeMs
            _activeTaskState.update { current ->
                current?.copy(
                    isRunning = false,
                    isCancelled = true,
                    currentAction = "Task cancelled",
                    durationMs = duration
                )
            }
            try {
                CodeAgentWorkService.cancelWork(context)
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            Log.e("AgentWorkManager", "Task failed with exception", e)
            val duration = System.currentTimeMillis() - task.startTimeMs
            val errorMsg = e.message ?: e.javaClass.simpleName
            _activeTaskState.update { current ->
                current?.copy(
                    isRunning = false,
                    completedSuccessfully = false,
                    error = errorMsg,
                    currentAction = "Failed: $errorMsg",
                    durationMs = duration
                )
            }
            try {
                CodeAgentWorkService.failWork(
                    context = context,
                    projectName = task.projectName,
                    errorMessage = errorMsg
                )
            } catch (_: Throwable) {}
        }
    }

    private fun formatToolAction(toolName: String, args: String): String {
        return when (toolName) {
            "execute_command" -> {
                val cmd = extractJsonField(args, "command")
                if (cmd != null) "Executing: ${cmd.take(45)}" else "Executing command"
            }
            "read_file" -> {
                val path = extractJsonField(args, "path")
                if (path != null) "Reading: ${path.substringAfterLast('/')}" else "Reading file"
            }
            "propose_file_edit" -> {
                val path = extractJsonField(args, "path")
                if (path != null) "Editing: ${path.substringAfterLast('/')}" else "Proposing file edit"
            }
            "search_code" -> {
                val q = extractJsonField(args, "query")
                if (q != null) "Searching: '$q'" else "Searching codebase"
            }
            "list_directory" -> {
                val path = extractJsonField(args, "path")
                if (path != null) "Browsing: ${path.substringAfterLast('/')}" else "Listing directory"
            }
            else -> "Running tool: $toolName"
        }
    }

    private fun extractJsonField(jsonStr: String, fieldName: String): String? {
        return try {
            val regex = Regex("""\"$fieldName\"\s*:\s*\"([^\"]+)\"""")
            regex.find(jsonStr)?.groupValues?.get(1)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseToolCalls(rawJson: String?): List<com.codeagent.core.ai.ToolCall> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return try {
            val array = Json.parseToJsonElement(rawJson).jsonArray
            array.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val args = obj["arguments"]?.jsonPrimitive?.content ?: "{}"
                com.codeagent.core.ai.ToolCall(id = id, name = name, arguments = args)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeToolCalls(calls: List<com.codeagent.core.ai.ToolCall>): String? {
        if (calls.isEmpty()) return null
        return buildString {
            append("[")
            append(
                calls.joinToString(",") { tc ->
                    """{"id":"${escapeJson(tc.id)}","name":"${escapeJson(tc.name)}","arguments":"${escapeJson(tc.arguments)}"}"""
                }
            )
            append("]")
        }
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSecs = durationMs / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }
}
