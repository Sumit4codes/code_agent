package com.koda.dev.core.agent

import com.koda.dev.core.ai.AiProvider
import com.koda.dev.core.ai.ChatMessage
import com.koda.dev.core.ai.ChatStreamEvent
import com.koda.dev.core.ai.ChatRequest
import com.koda.dev.core.model.PendingChange
import com.koda.dev.core.model.ToolNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class AgentRunResult(
    val messages: List<ChatMessage>,
    val newMessages: List<ChatMessage> = emptyList(),
    val pendingChanges: List<PendingChange>,
    val totalTokens: Int
)

sealed class AgentEvent {
    data class TextDelta(val text: String) : AgentEvent()
    data class ToolCallStart(
        val toolCallId: String,
        val toolName: String,
        val arguments: String
    ) : AgentEvent()
    data class ToolCallOutputChunk(
        val toolCallId: String,
        val chunk: String
    ) : AgentEvent()
    data class ToolCallComplete(
        val toolCallId: String,
        val toolName: String,
        val arguments: String,
        val output: String,
        val success: Boolean,
        val durationMs: Long
    ) : AgentEvent()
    data class MessageAdded(
        val message: ChatMessage,
        val messageId: String = UUID.randomUUID().toString()
    ) : AgentEvent()
    data class ContextEstimated(val usage: ContextUsageState) : AgentEvent()
    data class ContextCompacted(
        val summary: String,
        val noticeMessage: ChatMessage,
        val noticeMessageId: String = UUID.randomUUID().toString()
    ) : AgentEvent()
}

@Singleton
class AgentOrchestrator @Inject constructor(
    private val aiProvider: AiProvider,
    private val toolExecutor: ToolExecutor,
    private val toolRegistry: ToolRegistry
) {
    private val mutex = Mutex()

    companion object {
        const val MAX_CONTEXT_TOKENS = 100_000
        const val AUTO_COMPACT_THRESHOLD = 0.80f
        const val MIN_AUTO_COMPACT_CHARS = 500
    }

    suspend fun run(
        userMessage: String,
        context: AgentContext,
        model: String,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096,
        systemPrompt: String? = null,
        isGoalMode: Boolean = false,
        steeringInbox: SteeringInbox? = null,
        onEvent: ((AgentEvent) -> Unit)? = null,
        onDelta: ((String) -> Unit)? = null
    ): AgentRunResult = mutex.withLock {
        val history = context.history.toMutableList()
        val allPendingChanges = mutableListOf<PendingChange>()
        val newMessages = mutableListOf<ChatMessage>()
        var totalTokens = 0

        val effectiveSystemPrompt = buildString {
            if (systemPrompt != null) {
                append(systemPrompt)
                append("\n\n")
            }
            append("""
                ONLINE RESEARCH & LIVE WEB ACCESS:
                You have access to `web_search(query)` and `fetch_web_page(url)`. When answering queries requiring up-to-date documentation, external libraries, framework changes, or troubleshooting unfamiliar errors, search the web for authoritative sources and read documentation pages before writing or editing code.
            """.trimIndent())
            append("\n\n")
            append("""
                WORKSPACE & FILE OPERATIONS:
                When you create or edit files using `create_file` or `propose_file_edit`, the files are applied immediately to your workspace. You do NOT need to wait for or ask the user to approve changes before executing commands. You can and should immediately use `execute_command` to compile, run, test, or lint your code (e.g. running gcc, python, pytest, cargo test) to verify that your changes work. Changes are simultaneously staged for the user's visual diff review.
            """.trimIndent())
            append("\n\n")
            append("""
                TASK MANAGEMENT & PROCESS CONTROL:
                Commands that take longer than 20s automatically move to background tasks with a task_id. You can also launch background daemons via `execute_command(command="...", is_background=true)`.
                Use `manage_task(action="status", task_id="...")` to inspect live progress, runtime, and output.
                DECISION RULES:
                1. KEEP RUNNING: If recent output shows active compilation, downloads, or ongoing test progress, allow it to continue.
                2. KILL: If you see a [STALL WARNING], an infinite loop, or a prompt waiting on stdin that cannot be satisfied, call `manage_task(action="kill", task_id="...")` immediately.
                3. Maximum 5 background tasks can run at once. Clean up tasks with `kill` when finished or no longer needed.
                4. NEVER PIPE BUILDS/TESTS TO TAIL OR HEAD: Do NOT pipe long-running, test, or build commands to `tail` or `head` (e.g. `bash build.sh 2>&1 | tail -60` or `./gradlew ... | tail -n 50`). In Unix, `tail` buffers and blocks ALL output until the process exits (EOF), which prevents real-time streaming to the user chat UI and triggers false stall warnings. The task runner and circular buffer already automatically capture and truncate output. Run commands directly (e.g. `bash build.sh` or `./test.sh`).
                5. NO BUSY-WAIT POLLING: Do NOT poll `manage_task(action="status")` repeatedly in a loop while waiting for a background task to finish. Active background tasks are already monitored live in the user interface. When a long task (build, test, daemon) is running: check status once to confirm it started cleanly, then provide a concise update to the user and END YOUR TURN (do not execute any more tools). The user can steer you or let you know when to proceed. Never loop calling manage_task(status).
            """.trimIndent())
            append("\n\n")
            append("""
                MID-RUN USER STEERING & GUIDANCE:
                The user may send live steering directives during your task enclosed in `<user_steering_guidance>...</user_steering_guidance>`.
                When you receive user steering guidance:
                1. Immediately prioritize and adhere to the user's new instructions or constraints over prior assumptions.
                2. Acknowledge the user's guidance concisely in your next response.
                3. Adapt your ongoing execution without restarting completed, valid work.
            """.trimIndent())
            append("\n\n")
            if (isGoalMode) {
                append("""
                    AUTONOMOUS GOAL MODE ACTIVE:
                    You are running autonomously to achieve the user's objective: "$userMessage".
                    Guidelines:
                    1. Formulate a plan and execute changes step-by-step.
                    2. If you need documentation or library examples, use `web_search` and `fetch_web_page`.
                    3. After modifying files, verify your changes using `execute_command` (e.g., run test suites, linters, or compiler commands).
                    4. If any test or command fails, inspect the error output, diagnose the issue, and apply fixes until all verifications pass.
                    5. Do not stop until the objective is fully satisfied and all verification tests pass.
                """.trimIndent())
            }
        }.trim().takeIf { it.isNotBlank() }

        // Add system prompt if provided
        val messages = mutableListOf<ChatMessage>()
        if (effectiveSystemPrompt != null) {
            messages.add(ChatMessage(role = ChatMessage.Role.SYSTEM, content = effectiveSystemPrompt))
        }
        val sanitizedHistory = if (history.lastOrNull()?.let {
            it.role == ChatMessage.Role.USER && (
                it.content.trim() == userMessage.trim() ||
                it.content.trim().removePrefix("/goal").trim() == userMessage.trim()
            )
        } == true) {
            history.dropLast(1)
        } else {
            history
        }
        val prunedHistory = pruneStatusHistory(sanitizedHistory)
        messages.addAll(prunedHistory)
        messages.add(ChatMessage(role = ChatMessage.Role.USER, content = userMessage))

        var iteration = 0
        var lastAssistantContent = ""
        var consecutiveRunningStatusChecks = 0
        var lastRunningStatusTaskId: String? = null
        var autoCompactionAttempts = 0

        while (currentCoroutineContext().isActive) {
            iteration++

            if (consecutiveRunningStatusChecks >= 5 && lastRunningStatusTaskId != null) {
                val yieldMsg = ChatMessage(
                    role = ChatMessage.Role.ASSISTANT,
                    content = "Task '$lastRunningStatusTaskId' is currently running in the background. I have yielded control so you can monitor progress or steer further tasks."
                )
                messages.add(yieldMsg)
                newMessages.add(yieldMsg)
                onEvent?.invoke(AgentEvent.MessageAdded(yieldMsg))
                break
            }

            var hygieneMessages = ContextHygiene.shrinkToolResults(messages)
            var currentEstimate = ContextEstimator.estimate(
                messages = hygieneMessages,
                systemPrompt = effectiveSystemPrompt,
                tools = toolRegistry.allSpecs(),
                maxTokens = com.koda.dev.core.ai.ModelCatalogResolver.resolveContextLimit(model)
            )
            val rawEstimate = ContextEstimator.estimate(
                messages = messages,
                systemPrompt = effectiveSystemPrompt,
                tools = toolRegistry.allSpecs(),
                maxTokens = com.koda.dev.core.ai.ModelCatalogResolver.resolveContextLimit(model)
            )
            onEvent?.invoke(AgentEvent.ContextEstimated(currentEstimate))

            // Autonomous 80% context threshold compaction guard (triggers if either working or raw history reaches 80%)
            if ((currentEstimate.fraction >= AUTO_COMPACT_THRESHOLD || rawEstimate.fraction >= AUTO_COMPACT_THRESHOLD) &&
                autoCompactionAttempts < 2 &&
                canAutoCompact(messages)
            ) {
                autoCompactionAttempts++
                val startIndex = if (messages.firstOrNull()?.role == ChatMessage.Role.SYSTEM) 1 else 0
                val endIndex = (messages.size - 2).coerceAtLeast(startIndex)
                if (endIndex - startIndex >= 2) {
                    val candidateMessages = messages.subList(startIndex, endIndex).toList()
                    val noticeText = "\n\n> 📦 *Context reached 80% threshold (${currentEstimate.usedTokens}/${currentEstimate.maxTokens} tokens). Automatically compacting earlier history...*\n\n"
                    onDelta?.invoke(noticeText)
                    onEvent?.invoke(AgentEvent.TextDelta(noticeText))

                    try {
                        val summary = HistoryCompactor.summarizeHistory(aiProvider, candidateMessages, model)
                        if (summary.isNotBlank()) {
                            val formattedNotice = buildString {
                                append("### 📦 Context Compacted (80% Threshold Reached)\n\n")
                                append("Earlier conversation turns have been automatically summarized to free up working context:\n\n")
                                append(summary)
                            }
                            val noticeMsg = ChatMessage(
                                role = ChatMessage.Role.ASSISTANT,
                                content = formattedNotice
                            )
                            val systemSummaryMsg = ChatMessage(
                                role = ChatMessage.Role.SYSTEM,
                                content = "${HistoryCompactor.COMPACTION_PREFIX}\n$summary"
                            )

                            val retainedTail = messages.subList(endIndex, messages.size).toList()
                            messages.clear()
                            if (startIndex == 1 && effectiveSystemPrompt != null) {
                                messages.add(ChatMessage(role = ChatMessage.Role.SYSTEM, content = effectiveSystemPrompt))
                            }
                            messages.add(systemSummaryMsg)
                            messages.addAll(retainedTail)

                            newMessages.add(noticeMsg)
                            onEvent?.invoke(
                                AgentEvent.ContextCompacted(
                                    summary = summary,
                                    noticeMessage = noticeMsg
                                )
                            )

                            hygieneMessages = ContextHygiene.shrinkToolResults(messages)
                            currentEstimate = ContextEstimator.estimate(
                                messages = hygieneMessages,
                                systemPrompt = effectiveSystemPrompt,
                                tools = toolRegistry.allSpecs(),
                                maxTokens = com.koda.dev.core.ai.ModelCatalogResolver.resolveContextLimit(model)
                            )
                            onEvent?.invoke(AgentEvent.ContextEstimated(currentEstimate))
                        }
                    } catch (_: Exception) {
                        // If auto-compaction fails, proceed with current working context
                    }
                }
            }

            val request = ChatRequest(
                messages = hygieneMessages,
                model = model,
                temperature = temperature,
                maxTokens = maxTokens,
                tools = toolRegistry.allSpecs()
            )

            val currentContent = StringBuilder()
            val toolCalls = mutableListOf<com.koda.dev.core.ai.ToolCall>()
            var currentToolCallId: String? = null
            var currentToolCallName: String? = null
            var currentToolCallArgs = StringBuilder()

            try {
                val stream = aiProvider.streamChat(request)
                stream.collect { event ->
                    when (event) {
                        is ChatStreamEvent.TextDelta -> {
                            currentContent.append(event.text)
                            onDelta?.invoke(event.text)
                            onEvent?.invoke(AgentEvent.TextDelta(event.text))
                        }
                        is ChatStreamEvent.ToolCallStart -> {
                            // Flush any previous tool call being accumulated
                            if (currentToolCallId != null && currentToolCallName != null) {
                                toolCalls.add(
                                    com.koda.dev.core.ai.ToolCall(
                                        id = currentToolCallId!!,
                                        name = ToolNames.normalize(currentToolCallName!!),
                                        arguments = currentToolCallArgs.toString()
                                    )
                                )
                            }
                            currentToolCallId = event.id
                            currentToolCallName = ToolNames.normalize(event.name)
                            currentToolCallArgs = StringBuilder()
                        }
                        is ChatStreamEvent.ToolCallArgsDelta -> {
                            currentToolCallArgs.append(event.args)
                        }
                        is ChatStreamEvent.ToolCallComplete -> {
                            toolCalls.add(event.call)
                            currentToolCallId = null
                            currentToolCallName = null
                            currentToolCallArgs = StringBuilder()
                        }
                        is ChatStreamEvent.Usage -> {
                            totalTokens += event.promptTokens + event.completionTokens
                            val updatedEstimate = currentEstimate.copy(
                                usedTokens = event.promptTokens + event.completionTokens,
                                lastInputTokens = event.promptTokens,
                                lastOutputTokens = event.completionTokens
                            )
                            onEvent?.invoke(AgentEvent.ContextEstimated(updatedEstimate))
                        }
                        is ChatStreamEvent.Error -> {
                            throw AgentException(event.message, event.cause)
                        }
                        is ChatStreamEvent.Done -> { /* stream ended */ }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = ChatMessage(
                    role = ChatMessage.Role.ASSISTANT,
                    content = "Error: ${e.message ?: "Unknown error"}"
                )
                messages.add(errorMsg)
                newMessages.add(errorMsg)
                onEvent?.invoke(AgentEvent.MessageAdded(errorMsg))
                break
            }

            // Flush any remaining tool call
            if (currentToolCallId != null && currentToolCallName != null) {
                toolCalls.add(
                    com.koda.dev.core.ai.ToolCall(
                        id = currentToolCallId!!,
                        name = ToolNames.normalize(currentToolCallName!!),
                        arguments = currentToolCallArgs.toString()
                    )
                )
            }

            lastAssistantContent = currentContent.toString()

            // Add assistant message to history
            val assistantMessage = ChatMessage(
                role = ChatMessage.Role.ASSISTANT,
                content = lastAssistantContent,
                toolCalls = toolCalls
            )
            messages.add(assistantMessage)
            newMessages.add(assistantMessage)
            onEvent?.invoke(AgentEvent.MessageAdded(assistantMessage))

            // If no tool calls, check if there is pending mid-run steering before finishing
            if (toolCalls.isEmpty()) {
                val pendingSteering = steeringInbox?.drain() ?: emptyList()
                if (pendingSteering.isNotEmpty()) {
                    for (steer in pendingSteering) {
                        val formatted = buildString {
                            append("<user_steering_guidance>\n")
                            append(steer.text)
                            append("\n</user_steering_guidance>")
                        }
                        val steerMsg = ChatMessage(
                            role = ChatMessage.Role.USER,
                            content = formatted
                        )
                        messages.add(steerMsg)
                        newMessages.add(steerMsg)
                        onEvent?.invoke(AgentEvent.MessageAdded(steerMsg, messageId = steer.id))
                    }
                    continue // Continue loop to address the user's live guidance
                }
                break
            }

            // Execute tool calls and add results
            for ((index, tc) in toolCalls.withIndex()) {
                // If user provided steering guidance during previous tools, skip remaining planned tools
                if (index > 0 && steeringInbox?.hasPending == true) {
                    val skippedOutput = "Tool execution skipped: User provided mid-run steering guidance"
                    onEvent?.invoke(
                        AgentEvent.ToolCallComplete(
                            toolCallId = tc.id,
                            toolName = ToolNames.normalize(tc.name),
                            arguments = tc.arguments,
                            output = skippedOutput,
                            success = false,
                            durationMs = 0
                        )
                    )
                    val toolMsg = ChatMessage(
                        role = ChatMessage.Role.TOOL,
                        content = skippedOutput,
                        toolCallId = tc.id
                    )
                    messages.add(toolMsg)
                    newMessages.add(toolMsg)
                    onEvent?.invoke(AgentEvent.MessageAdded(toolMsg))
                    continue
                }

                val cleanName = ToolNames.normalize(tc.name)
                val (manageAction, manageTaskId) = extractManageTaskArgs(tc.arguments)
                val isStatusCheck = cleanName == ToolNames.MANAGE_TASK && manageAction == "status" && manageTaskId != null

                // Anti-spin guard: suppress repetitive status polling for the same running background task
                if (isStatusCheck && manageTaskId == lastRunningStatusTaskId && consecutiveRunningStatusChecks >= 4) {
                    val suppressedOutput = "Task '$manageTaskId' is actively running in the background. [Anti-polling guard active]: Repetitive polling has been blocked. DO NOT call manage_task(status) again. Provide a concise update to the user and END YOUR TURN now."
                    onEvent?.invoke(
                        AgentEvent.ToolCallComplete(
                            toolCallId = tc.id,
                            toolName = cleanName,
                            arguments = tc.arguments,
                            output = suppressedOutput,
                            success = false,
                            durationMs = 0
                        )
                    )
                    pruneSupersededStatusChecks(messages, manageTaskId)
                    val toolMsg = ChatMessage(
                        role = ChatMessage.Role.TOOL,
                        content = suppressedOutput,
                        toolCallId = tc.id
                    )
                    messages.add(toolMsg)
                    newMessages.add(toolMsg)
                    onEvent?.invoke(AgentEvent.MessageAdded(toolMsg))
                    continue
                }

                val startTime = System.currentTimeMillis()
                onEvent?.invoke(AgentEvent.ToolCallStart(tc.id, cleanName, tc.arguments))

                val result = toolExecutor.execute(
                    name = cleanName,
                    argumentsJson = tc.arguments,
                    onOutput = { chunk ->
                        onEvent?.invoke(AgentEvent.ToolCallOutputChunk(tc.id, chunk))
                    },
                    yieldEarlyIf = { steeringInbox?.hasPending == true }
                )
                val duration = System.currentTimeMillis() - startTime
                var toolOutput = if (result.success) result.output else "Error: ${result.output}"

                if (isStatusCheck) {
                    val isStillRunning = toolOutput.contains("Status: RUNNING") || toolOutput.contains("is running")
                    if (isStillRunning) {
                        if (manageTaskId == lastRunningStatusTaskId) {
                            consecutiveRunningStatusChecks++
                        } else {
                            lastRunningStatusTaskId = manageTaskId
                            consecutiveRunningStatusChecks = 1
                        }
                        if (consecutiveRunningStatusChecks >= 3) {
                            toolOutput += "\n\n[NOTICE: Task '$manageTaskId' is actively running in the background. Active tasks are displayed in real-time in the UI. DO NOT call manage_task(status) again in a loop. Provide a concise update to the user and END YOUR TURN now. Do not call any further tools.]"
                        }
                    } else {
                        // Finished or failed
                        consecutiveRunningStatusChecks = 0
                        lastRunningStatusTaskId = null
                    }
                } else if (cleanName != ToolNames.MANAGE_TASK) {
                    consecutiveRunningStatusChecks = 0
                    lastRunningStatusTaskId = null
                }

                onEvent?.invoke(
                    AgentEvent.ToolCallComplete(
                        toolCallId = tc.id,
                        toolName = cleanName,
                        arguments = tc.arguments,
                        output = toolOutput,
                        success = result.success,
                        durationMs = duration
                    )
                )

                if (result.pendingChange != null) {
                    allPendingChanges.add(result.pendingChange.copy(sessionId = context.sessionId))
                }

                if (isStatusCheck) {
                    pruneSupersededStatusChecks(messages, manageTaskId)
                }

                val toolMsg = ChatMessage(
                    role = ChatMessage.Role.TOOL,
                    content = toolOutput,
                    toolCallId = tc.id
                )
                messages.add(toolMsg)
                newMessages.add(toolMsg)
                onEvent?.invoke(AgentEvent.MessageAdded(toolMsg))
            }

            // Drain any user steering directives submitted during tool execution
            val pendingSteering = steeringInbox?.drain() ?: emptyList()
            if (pendingSteering.isNotEmpty()) {
                for (steer in pendingSteering) {
                    val formatted = buildString {
                        append("<user_steering_guidance>\n")
                        append(steer.text)
                        append("\n</user_steering_guidance>")
                    }
                    val steerMsg = ChatMessage(
                        role = ChatMessage.Role.USER,
                        content = formatted
                    )
                    messages.add(steerMsg)
                    newMessages.add(steerMsg)
                    onEvent?.invoke(AgentEvent.MessageAdded(steerMsg, messageId = steer.id))
                }
            }
        }

        AgentRunResult(
            messages = messages,
            newMessages = newMessages,
            pendingChanges = allPendingChanges,
            totalTokens = totalTokens
        )
    }
}

class AgentException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class AgentContext(
    val sessionId: String,
    val projectId: String,
    val history: List<ChatMessage> = emptyList()
)

internal fun extractManageTaskArgs(argumentsJson: String): Pair<String?, String?> {
    return try {
        val json = Json.parseToJsonElement(argumentsJson).jsonObject
        val action = json["action"]?.jsonPrimitive?.content
        val taskId = json["task_id"]?.jsonPrimitive?.content
        Pair(action, taskId)
    } catch (_: Exception) {
        Pair(null, null)
    }
}

internal fun pruneStatusHistory(history: List<ChatMessage>): List<ChatMessage> {
    if (history.isEmpty()) return history

    val lastIndexByTaskId = mutableMapOf<String, Int>()
    val taskIdRegex = Regex("""^Task ID:\s*([^\s\n]+)""")

    for ((index, msg) in history.withIndex()) {
        if (msg.role == ChatMessage.Role.TOOL) {
            val match = taskIdRegex.find(msg.content)
            if (match != null) {
                val taskId = match.groupValues[1]
                lastIndexByTaskId[taskId] = index
            }
        }
    }

    if (lastIndexByTaskId.isEmpty()) return history

    return history.mapIndexed { index, msg ->
        if (msg.role == ChatMessage.Role.TOOL) {
            val match = taskIdRegex.find(msg.content)
            if (match != null) {
                val taskId = match.groupValues[1]
                val lastIdx = lastIndexByTaskId[taskId]
                if (lastIdx != null && index < lastIdx) {
                    msg.copy(content = "(Task $taskId status output superseded by newer check)")
                } else {
                    msg
                }
            } else {
                msg
            }
        } else {
            msg
        }
    }
}

internal fun pruneSupersededStatusChecks(messages: MutableList<ChatMessage>, taskId: String) {
    val prefix = "Task ID: $taskId\n"
    for (i in 0 until messages.size) {
        val msg = messages[i]
        if (msg.role == ChatMessage.Role.TOOL && msg.content.startsWith(prefix)) {
            messages[i] = msg.copy(content = "(Task $taskId status output superseded by newer check)")
        }
    }
}

internal fun canAutoCompact(messages: List<ChatMessage>): Boolean {
    val startIndex = if (messages.firstOrNull()?.role == ChatMessage.Role.SYSTEM) 1 else 0
    val endIndex = messages.size - 2
    if (endIndex - startIndex < 2) return false
    val candidateChars = messages.subList(startIndex, endIndex).sumOf { it.content.length }
    return candidateChars >= AgentOrchestrator.MIN_AUTO_COMPACT_CHARS
}
