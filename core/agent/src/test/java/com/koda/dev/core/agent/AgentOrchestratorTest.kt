package com.koda.dev.core.agent

import android.net.FakeUri
import com.koda.dev.core.ai.ChatMessage
import com.koda.dev.core.ai.ChatStreamEvent
import com.koda.dev.core.ai.ToolCall
import com.koda.dev.core.model.ChangeType
import com.koda.dev.core.testing.FakeAiProvider
import com.koda.dev.core.testing.FakeProjectFileSystem
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
        fs.putFile("greet.txt", "Welcome to Koda Dev")

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
                ChatStreamEvent.TextDelta("The file content is Welcome to Koda Dev"),
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
        assertTrue(result.newMessages[1].content.contains("Welcome to Koda Dev"))
        assertEquals(ChatMessage.Role.ASSISTANT, result.newMessages[2].role)
        assertEquals("The file content is Welcome to Koda Dev", result.newMessages[2].content)
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

    @Test
    fun `onEvent receives ToolCallStart and ToolCallComplete events in real time`() = runTest {
        fs.putFile("info.txt", "Version 1.0.0")

        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.ToolCallComplete(
                    ToolCall("c-info", "read_file", """{"path":"info.txt"}""")
                ),
                ChatStreamEvent.Done
            )
        )
        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.TextDelta("Version is 1.0.0"),
                ChatStreamEvent.Done
            )
        )

        val events = mutableListOf<AgentEvent>()
        val context = AgentContext(sessionId = "s-test", projectId = "p1")

        orchestrator.run(
            userMessage = "Check version",
            context = context,
            model = "test-model",
            onEvent = { events.add(it) }
        )

        val toolStarts = events.filterIsInstance<AgentEvent.ToolCallStart>()
        val toolCompletes = events.filterIsInstance<AgentEvent.ToolCallComplete>()
        val messageAddeds = events.filterIsInstance<AgentEvent.MessageAdded>()

        assertEquals(1, toolStarts.size)
        assertEquals("c-info", toolStarts[0].toolCallId)
        assertEquals("read_file", toolStarts[0].toolName)

        assertEquals(1, toolCompletes.size)
        assertEquals("c-info", toolCompletes[0].toolCallId)
        assertTrue(toolCompletes[0].success)
        assertTrue(toolCompletes[0].output.contains("Version 1.0.0"))

        assertTrue(messageAddeds.isNotEmpty())
    }

    @Test
    fun `mid-run steering guidance is drained and injected into messages between tool calls`() = runTest {
        fs.putFile("fileA.txt", "Content A")
        val steeringInbox = SteeringInbox()

        // Turn 1: AI calls read_file
        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.ToolCallComplete(
                    ToolCall("c-read", "read_file", """{"path":"fileA.txt"}""")
                ),
                ChatStreamEvent.Done
            )
        )
        // Turn 2: AI completes following steering
        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.TextDelta("Acknowledged steering. Finishing up."),
                ChatStreamEvent.Done
            )
        )

        // User steers mid-run
        steeringInbox.send("Wait, make sure you don't edit fileA, just inspect it.")

        val events = mutableListOf<AgentEvent>()
        val context = AgentContext(sessionId = "s-steer", projectId = "p1")

        val result = orchestrator.run(
            userMessage = "Process files",
            context = context,
            model = "test-model",
            steeringInbox = steeringInbox,
            onEvent = { events.add(it) }
        )

        // Verify steering guidance was injected as a USER message
        val userMessages = result.messages.filter { it.role == ChatMessage.Role.USER }
        assertTrue("Expected steering guidance in messages", userMessages.any { it.content.contains("<user_steering_guidance>") })
        assertTrue(userMessages.any { it.content.contains("Wait, make sure you don't edit fileA") })

        val messageAddeds = events.filterIsInstance<AgentEvent.MessageAdded>()
        assertTrue(messageAddeds.any { it.message.content.contains("<user_steering_guidance>") })
    }

    @Test
    fun `mid-run steering guidance sent when agent produces no tools continues execution for another turn`() = runTest {
        val steeringInbox = SteeringInbox()

        // Turn 1: AI is about to finish without tools
        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.TextDelta("Here is the initial response."),
                ChatStreamEvent.Done
            )
        )
        // Turn 2: User steered, AI answers steering
        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.TextDelta("Understood your guidance. Here is the updated answer."),
                ChatStreamEvent.Done
            )
        )

        // Send steering before execution reaches completion
        steeringInbox.send("Also add documentation comments to all functions.")

        val context = AgentContext(sessionId = "s-steer-continue", projectId = "p1")
        val result = orchestrator.run(
            userMessage = "Write code",
            context = context,
            model = "test-model",
            steeringInbox = steeringInbox
        )

        // Result should contain messages from both turns
        val assistantMessages = result.messages.filter { it.role == ChatMessage.Role.ASSISTANT }
        assertEquals(2, assistantMessages.size)
        assertTrue(assistantMessages[0].content.contains("initial response"))
        assertTrue(assistantMessages[1].content.contains("updated answer"))

        val userMessages = result.messages.filter { it.role == ChatMessage.Role.USER }
        assertTrue(userMessages.any { it.content.contains("Also add documentation comments") })
    }

    @Test
    fun `system prompt instructs agent not to pipe build commands to tail or head`() = runTest {
        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.TextDelta("Done"),
                ChatStreamEvent.Done
            )
        )
        val context = AgentContext(sessionId = "s-prompt-check", projectId = "p1")
        val result = orchestrator.run(
            userMessage = "test",
            context = context,
            model = "test-model"
        )
        val systemMessage = result.messages.first { it.role == ChatMessage.Role.SYSTEM }
        assertTrue(systemMessage.content.contains("NEVER PIPE BUILDS/TESTS TO TAIL OR HEAD"))
        assertTrue(systemMessage.content.contains("tail"))
    }
}
