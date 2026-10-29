package com.koda.dev.core.agent.service

import android.content.ContextWrapper
import android.net.FakeUri
import com.koda.dev.core.agent.AgentOrchestrator
import com.koda.dev.core.agent.PendingChangeManager
import com.koda.dev.core.agent.ToolExecutor
import com.koda.dev.core.agent.ToolRegistry
import com.koda.dev.core.ai.ChatStreamEvent
import com.koda.dev.core.data.MessageDao
import com.koda.dev.core.data.MessageEntity
import com.koda.dev.core.data.ProjectDao
import com.koda.dev.core.data.ProjectEntity
import com.koda.dev.core.data.SessionDao
import com.koda.dev.core.data.SessionEntity
import com.koda.dev.core.testing.FakeAiProvider
import com.koda.dev.core.testing.FakePendingChangeDao
import com.koda.dev.core.testing.FakeProjectFileSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class TestMessageDao : MessageDao {
    val messages = mutableListOf<MessageEntity>()
    override fun getBySession(sessionId: String): Flow<List<MessageEntity>> =
        flowOf(messages.filter { it.sessionId == sessionId })

    override suspend fun getBySessionList(sessionId: String): List<MessageEntity> =
        messages.filter { it.sessionId == sessionId }

    override suspend fun insert(message: MessageEntity) {
        messages.removeAll { it.id == message.id }
        messages.add(message)
    }

    override suspend fun insertAll(messages: List<MessageEntity>) {
        messages.forEach { insert(it) }
    }

    override suspend fun deleteBySession(sessionId: String) {
        messages.removeAll { it.sessionId == sessionId }
    }
}

private class TestSessionDao : SessionDao {
    val sessions = mutableListOf<SessionEntity>()
    override fun getByProject(projectId: String): Flow<List<SessionEntity>> =
        flowOf(sessions.filter { it.projectId == projectId })

    override suspend fun getById(id: String): SessionEntity? =
        sessions.find { it.id == id }

    override suspend fun upsert(session: SessionEntity) {
        sessions.removeAll { it.id == session.id }
        sessions.add(session)
    }

    override suspend fun delete(session: SessionEntity) {
        sessions.remove(session)
    }

    override suspend fun deleteById(id: String) {
        sessions.removeAll { it.id == id }
    }

    override suspend fun updateTitle(id: String, title: String, updatedAt: Long) {
        val s = sessions.find { it.id == id }
        if (s != null) {
            sessions.remove(s)
            sessions.add(s.copy(title = title, updatedAt = updatedAt))
        }
    }
}

private class TestProjectDao : ProjectDao {
    val projects = mutableListOf<ProjectEntity>()
    override fun getAll(): Flow<List<ProjectEntity>> = flowOf(projects)
    override suspend fun getById(id: String): ProjectEntity? = projects.find { it.id == id }
    override suspend fun upsert(project: ProjectEntity) {
        projects.removeAll { it.id == project.id }
        projects.add(project)
    }

    override suspend fun delete(project: ProjectEntity) {
        projects.remove(project)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AgentWorkManagerTest {

    private lateinit var aiProvider: FakeAiProvider
    private lateinit var fs: FakeProjectFileSystem
    private lateinit var toolExecutor: ToolExecutor
    private lateinit var pendingChangeManager: PendingChangeManager
    private lateinit var orchestrator: AgentOrchestrator
    private lateinit var messageDao: TestMessageDao
    private lateinit var pendingChangeDao: FakePendingChangeDao
    private lateinit var sessionDao: TestSessionDao
    private lateinit var projectDao: TestProjectDao
    private lateinit var workManager: AgentWorkManager

    private val rootUri = FakeUri("content://test/root")
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        aiProvider = FakeAiProvider()
        fs = FakeProjectFileSystem("content://test/root")
        toolExecutor = ToolExecutor()
        toolExecutor.bind(fs, rootUri)
        pendingChangeDao = FakePendingChangeDao()
        pendingChangeManager = PendingChangeManager(pendingChangeDao)
        pendingChangeManager.bind(fs, rootUri)

        orchestrator = AgentOrchestrator(
            aiProvider = aiProvider,
            toolExecutor = toolExecutor,
            toolRegistry = ToolRegistry
        )

        messageDao = TestMessageDao()
        sessionDao = TestSessionDao()
        projectDao = TestProjectDao()

        val context = ContextWrapper(null)

        workManager = AgentWorkManager(
            context = context,
            agentOrchestrator = orchestrator,
            toolExecutor = toolExecutor,
            pendingChangeManager = pendingChangeManager,
            messageDao = messageDao,
            pendingChangeDao = pendingChangeDao,
            sessionDao = sessionDao,
            projectDao = projectDao,
            fileSystem = fs,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `startTask initializes activeTaskState and runs execution`() = runTest(testDispatcher) {
        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.TextDelta("Done!"),
                ChatStreamEvent.Done
            )
        )

        val taskId = workManager.startTask(
            sessionId = "s1",
            projectId = "p1",
            projectName = "TestProj",
            projectUri = rootUri,
            prompt = "Build the feature",
            model = "gpt-4o",
            isGoalMode = false
        )

        assertNotNull(taskId)
        val initial = workManager.activeTaskState.value
        assertNotNull(initial)
        assertEquals("s1", initial?.sessionId)
        assertEquals("TestProj", initial?.projectName)
        assertEquals("Build the feature", initial?.userPrompt)
        assertFalse(initial?.isGoalMode ?: true)
        assertTrue(initial?.isRunning ?: false)

        advanceUntilIdle()

        val completed = workManager.activeTaskState.value
        assertNotNull(completed)
        assertFalse(completed?.isRunning ?: true)
        assertEquals(true, completed?.completedSuccessfully)

        // Verify user message and assistant message persisted to Room
        val savedMessages = messageDao.getBySessionList("s1")
        assertEquals(2, savedMessages.size)
        assertEquals("USER", savedMessages[0].role)
        assertEquals("Build the feature", savedMessages[0].content)
        assertEquals("ASSISTANT", savedMessages[1].role)
        assertEquals("Done!", savedMessages[1].content)
    }

    @Test
    fun `startTask in Goal Mode sets isGoalMode true`() = runTest(testDispatcher) {
        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.TextDelta("Objective reached!"),
                ChatStreamEvent.Done
            )
        )

        workManager.startTask(
            sessionId = "s2",
            projectId = "p1",
            projectName = "GoalProj",
            projectUri = rootUri,
            prompt = "Run tests and refactor",
            model = "gpt-4o",
            isGoalMode = true
        )

        val initial = workManager.activeTaskState.value
        assertNotNull(initial)
        assertTrue(initial?.isGoalMode ?: false)
        assertTrue(initial?.currentAction?.contains("autonomous goal", ignoreCase = true) ?: false)

        advanceUntilIdle()

        val completed = workManager.activeTaskState.value
        assertNotNull(completed)
        assertFalse(completed?.isRunning ?: true)
        assertEquals(true, completed?.completedSuccessfully)
    }

    @Test
    fun `cancelCurrentTask cancels running task and marks state cancelled`() = runTest(testDispatcher) {
        workManager.startTask(
            sessionId = "s3",
            projectId = "p1",
            projectName = "CancelProj",
            projectUri = rootUri,
            prompt = "Long running task",
            model = "gpt-4o"
        )

        workManager.cancelCurrentTask()

        val cancelled = workManager.activeTaskState.value
        assertNotNull(cancelled)
        assertFalse(cancelled?.isRunning ?: true)
        assertTrue(cancelled?.isCancelled ?: false)
        assertEquals("Cancelled by user", cancelled?.currentAction)
    }

    @Test
    fun `startTask does not duplicate user message if already persisted in Room DB`() = runTest(testDispatcher) {
        // Simulate ChatViewModel persisting the user message first
        messageDao.insert(
            com.koda.dev.core.data.MessageEntity(
                id = "pre-persisted-user-msg",
                sessionId = "s4",
                role = "USER",
                content = "Autonomous Goal Objective",
                toolCallsJson = null,
                toolCallId = null,
                timestamp = System.currentTimeMillis()
            )
        )

        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.TextDelta("Goal fully achieved!"),
                ChatStreamEvent.Done
            )
        )

        workManager.startTask(
            sessionId = "s4",
            projectId = "p1",
            projectName = "DedupeProj",
            projectUri = rootUri,
            prompt = "Autonomous Goal Objective",
            model = "gpt-4o",
            isGoalMode = true
        )

        advanceUntilIdle()

        val savedMessages = messageDao.getBySessionList("s4")
        assertEquals(2, savedMessages.size)
        assertEquals("USER", savedMessages[0].role)
        assertEquals("Autonomous Goal Objective", savedMessages[0].content)
        assertEquals("ASSISTANT", savedMessages[1].role)
        assertEquals("Goal fully achieved!", savedMessages[1].content)
    }

    @Test
    fun `startTask resets streamingDelta on completion and emits liveEvents`() = runTest(testDispatcher) {
        val receivedEvents = mutableListOf<com.koda.dev.core.agent.AgentEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            workManager.liveEvents.collect { receivedEvents.add(it) }
        }

        aiProvider.enqueueResponse(
            listOf(
                ChatStreamEvent.TextDelta("Step 1 "),
                ChatStreamEvent.TextDelta("in progress"),
                ChatStreamEvent.Done
            )
        )

        workManager.startTask(
            sessionId = "s5",
            projectId = "p1",
            projectName = "StreamingProj",
            projectUri = rootUri,
            prompt = "Stream this task",
            model = "gpt-4o",
            isGoalMode = true
        )

        advanceUntilIdle()

        val completed = workManager.activeTaskState.value
        assertNotNull(completed)
        assertEquals("", completed?.streamingDelta)
        assertEquals(true, completed?.completedSuccessfully)

        assertTrue(receivedEvents.any { it is com.koda.dev.core.agent.AgentEvent.TextDelta && it.text == "Step 1 " })
        assertTrue(receivedEvents.any { it is com.koda.dev.core.agent.AgentEvent.TextDelta && it.text == "in progress" })
        assertTrue(receivedEvents.any { it is com.koda.dev.core.agent.AgentEvent.MessageAdded })

        job.cancel()
    }
}
