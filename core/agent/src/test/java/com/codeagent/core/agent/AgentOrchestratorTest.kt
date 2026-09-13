package com.codeagent.core.agent

import android.net.FakeUri
import com.codeagent.core.ai.ChatMessage
import com.codeagent.core.ai.ChatStreamEvent
import com.codeagent.core.ai.ToolCall
import com.codeagent.core.model.ChangeType
import com.codeagent.core.testing.FakeAiProvider
import com.codeagent.core.testing.FakeProjectFileSystem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentOrchestratorTest {

    private lateinit var aiProvider: FakeAiProvider
    private lateinit var fs: FakeProjectFileSystem
    private lateinit var toolExecutor: ToolExecutor
    private lateinit var orchestrator: AgentOrchestrator
    private val rootUri = FakeUri("content://test/root")

    @Before
    fun setUp() {
        aiProvider = FakeAiProvider()
        fs = FakeProjectFileSystem("content://test/root")
        toolExecutor = ToolExecutor()
        toolExecutor.bind(fs, rootUri)
        orchestrator = AgentOrchestrator(
            aiProvider = aiProvider,
            toolExecutor = toolExecutor,
            toolRegistry = ToolRegistry
        )
    }

    @Test
    fun `simple text response streams delta and tracks newMessages`() = runTest {
        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.TextDelta("Hello "),
                ChatStreamEvent.TextDelta("there!"),
                ChatStreamEvent.Done
            )
        )

        val deltas = mutableListOf<String>()
        val context = AgentContext(sessionId = "s1", projectId = "p1")

        val result = orchestrator.run(
            userMessage = "Hi",
            context = context,
            model = "test-model",
            onDelta = { deltas.add(it) }
        )

        assertEquals(listOf("Hello ", "there!"), deltas)
        assertEquals(1, result.newMessages.size)
        assertEquals(ChatMessage.Role.ASSISTANT, result.newMessages[0].role)
        assertEquals("Hello there!", result.newMessages[0].content)

        // full messages should include user message and assistant message
        val nonSystemMessages = result.messages.filter { it.role != ChatMessage.Role.SYSTEM }
        assertEquals(2, nonSystemMessages.size)
        assertEquals(ChatMessage.Role.USER, nonSystemMessages[0].role)
        assertEquals("Hi", nonSystemMessages[0].content)
        assertEquals(ChatMessage.Role.ASSISTANT, nonSystemMessages[1].role)
    }

    @Test
    fun `tool execution loop invokes tool and continues chat`() = runTest {
        fs.putFile("greet.txt", "Welcome to CodeAgent")

        // First AI response: call read_file
        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.ToolCallComplete(
                    ToolCall("c1", "read_file", """{"path":"greet.txt"}""")
                ),
                ChatStreamEvent.Done
            )
        )

        // Second AI response: summarize tool output
        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.TextDelta("The file content is Welcome to CodeAgent"),
                ChatStreamEvent.Done
            )
        )

        val context = AgentContext(sessionId = "s1", projectId = "p1")
        val result = orchestrator.run(
            userMessage = "What does greet.txt say?",
            context = context,
            model = "test-model"
        )

        // newMessages produced by agent:
        // 1. Assistant message with tool call
        // 2. Tool output message
        // 3. Final assistant message
        assertEquals(3, result.newMessages.size)
        assertEquals(ChatMessage.Role.ASSISTANT, result.newMessages[0].role)
        assertEquals(1, result.newMessages[0].toolCalls.size)
        assertEquals(ChatMessage.Role.TOOL, result.newMessages[1].role)
        assertTrue(result.newMessages[1].content.contains("Welcome to CodeAgent"))
        assertEquals(ChatMessage.Role.ASSISTANT, result.newMessages[2].role)
        assertEquals("The file content is Welcome to CodeAgent", result.newMessages[2].content)
    }

    @Test
    fun `propose_file_edit tool registers pending changes in AgentRunResult`() = runTest {
        fs.putFile("Main.kt", "fun old() {}")

        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.ToolCallComplete(
                    ToolCall(
                        "c2",
                        "propose_file_edit",
                        """{"path":"Main.kt","content":"fun new() {}"}"""
                    )
                ),
                ChatStreamEvent.Done
            )
        )
        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.TextDelta("I proposed the changes."),
                ChatStreamEvent.Done
            )
        )

        val context = AgentContext(sessionId = "session-42", projectId = "p1")
        val result = orchestrator.run(
            userMessage = "Update Main.kt",
            context = context,
            model = "test-model"
        )

        assertEquals(1, result.pendingChanges.size)
        val pending = result.pendingChanges.first()
        assertEquals("session-42", pending.sessionId)
        assertEquals("Main.kt", pending.filePath)
        assertEquals(ChangeType.EDIT, pending.changeType)
        assertEquals("fun new() {}", pending.proposedContent)
    }
}
