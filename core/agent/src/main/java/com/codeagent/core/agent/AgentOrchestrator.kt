package com.codeagent.core.agent

import com.codeagent.core.ai.AiProvider
import com.codeagent.core.ai.ChatMessage
import com.codeagent.core.ai.ChatStreamEvent
import com.codeagent.core.ai.ChatRequest
import com.codeagent.core.model.PendingChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class AgentRunResult(
    val messages: List<ChatMessage>,
    val newMessages: List<ChatMessage> = emptyList(),
    val pendingChanges: List<PendingChange>,
    val totalTokens: Int
)

@Singleton
class AgentOrchestrator @Inject constructor(
    private val aiProvider: AiProvider,
    private val toolExecutor: ToolExecutor,
    private val toolRegistry: ToolRegistry
) {
    private val mutex = Mutex()

    companion object {
        const val MAX_TOOL_ITERATIONS = 25
        const val MAX_CONTEXT_TOKENS = 100_000
    }

    suspend fun run(
        userMessage: String,
        context: AgentContext,
        model: String,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096,
        systemPrompt: String? = null,
        onDelta: ((String) -> Unit)? = null
    ): AgentRunResult = mutex.withLock {
        val history = context.history.toMutableList()
        val allPendingChanges = mutableListOf<PendingChange>()
        val newMessages = mutableListOf<ChatMessage>()
        var totalTokens = 0

        // Add system prompt if provided
        val messages = mutableListOf<ChatMessage>()
        if (systemPrompt != null) {
            messages.add(ChatMessage(role = ChatMessage.Role.SYSTEM, content = systemPrompt))
        }
        messages.addAll(history)
        messages.add(ChatMessage(role = ChatMessage.Role.USER, content = userMessage))

        var iteration = 0
        var lastAssistantContent = ""

        while (currentCoroutineContext().isActive && iteration < MAX_TOOL_ITERATIONS) {
            iteration++

            val request = ChatRequest(
                messages = messages,
                model = model,
                temperature = temperature,
                maxTokens = maxTokens,
                tools = toolRegistry.allSpecs()
            )

            val currentContent = StringBuilder()
            val toolCalls = mutableListOf<com.codeagent.core.ai.ToolCall>()
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
                        }
                        is ChatStreamEvent.ToolCallStart -> {
                            // Flush any previous tool call being accumulated
                            if (currentToolCallId != null && currentToolCallName != null) {
                                toolCalls.add(
                                    com.codeagent.core.ai.ToolCall(
                                        id = currentToolCallId!!,
                                        name = currentToolCallName!!,
                                        arguments = currentToolCallArgs.toString()
                                    )
                                )
                            }
                            currentToolCallId = event.id
                            currentToolCallName = event.name
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
                break
            }

            // Flush any remaining tool call
            if (currentToolCallId != null && currentToolCallName != null) {
                toolCalls.add(
                    com.codeagent.core.ai.ToolCall(
                        id = currentToolCallId!!,
                        name = currentToolCallName!!,
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

            // If no tool calls, the agent is done
            if (toolCalls.isEmpty()) break

            // Execute tool calls and add results
            for (tc in toolCalls) {
                val result = toolExecutor.execute(tc.name, tc.arguments)

                if (result.pendingChange != null) {
                    allPendingChanges.add(result.pendingChange.copy(sessionId = context.sessionId))
                }

                val toolMsg = ChatMessage(
                    role = ChatMessage.Role.TOOL,
                    content = if (result.success) result.output else "Error: ${result.output}",
                    toolCallId = tc.id
                )
                messages.add(toolMsg)
                newMessages.add(toolMsg)
            }
        }

        if (iteration >= MAX_TOOL_ITERATIONS) {
            val maxMsg = ChatMessage(
                role = ChatMessage.Role.ASSISTANT,
                content = "I reached the maximum number of tool iterations ($MAX_TOOL_ITERATIONS). Please continue the conversation if you need more work done."
            )
            messages.add(maxMsg)
            newMessages.add(maxMsg)
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
