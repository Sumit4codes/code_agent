package com.koda.dev.feature.chat

import android.content.ContextWrapper
import com.koda.dev.core.agent.AgentOrchestrator
import com.koda.dev.core.agent.PendingChangeManager
import com.koda.dev.core.agent.ToolExecutor
import com.koda.dev.core.agent.ToolRegistry
import com.koda.dev.core.agent.service.AgentWorkManager
import com.koda.dev.core.data.ApiKeyStorage
import com.koda.dev.core.data.MessageDao
import com.koda.dev.core.data.MessageEntity
import com.koda.dev.core.data.ProjectDao
import com.koda.dev.core.data.ProjectEntity
import com.koda.dev.core.data.ProviderConfigDao
import com.koda.dev.core.data.ProviderConfigEntity
import com.koda.dev.core.data.SessionDao
import com.koda.dev.core.data.SessionEntity
import com.koda.dev.core.data.SettingsRepository
import com.koda.dev.core.model.Message
import com.koda.dev.core.model.MessageRole
import com.koda.dev.core.terminal.TaskLaunchResult
import com.koda.dev.core.terminal.TaskManager
import com.koda.dev.core.terminal.TaskStatusInfo
import com.koda.dev.core.terminal.TaskSummary
import com.koda.dev.core.testing.FakeAiProvider
import com.koda.dev.core.testing.FakePendingChangeDao
import com.koda.dev.core.testing.FakeProjectFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

private class ChatTestMessageDao : MessageDao {
    private val lock = Any()
    private val messages = mutableListOf<MessageEntity>()

    fun snapshot(): List<MessageEntity> = synchronized(lock) { messages.toList() }

    override fun getBySession(sessionId: String): Flow<List<MessageEntity>> =
        flowOf(snapshot().filter { it.sessionId == sessionId })

    override suspend fun getBySessionList(sessionId: String): List<MessageEntity> =
        snapshot().filter { it.sessionId == sessionId }

    override suspend fun insert(message: MessageEntity) {
        synchronized(lock) {
            messages.removeAll { it.id == message.id }
            messages.add(message)
        }
    }

    override suspend fun insertAll(messages: List<MessageEntity>) {
        messages.forEach { insert(it) }
    }

    override suspend fun deleteBySession(sessionId: String) {
        synchronized(lock) { messages.removeAll { it.sessionId == sessionId } }
    }
}

private class ChatTestSessionDao : SessionDao {
    private val lock = Any()
    private val sessions = mutableListOf<SessionEntity>()

    override fun getByProject(projectId: String): Flow<List<SessionEntity>> =
        flowOf(synchronized(lock) { sessions.filter { it.projectId == projectId } })

    override suspend fun getById(id: String): SessionEntity? =
        synchronized(lock) { sessions.find { it.id == id } }

    override suspend fun upsert(session: SessionEntity) {
        synchronized(lock) {
            sessions.removeAll { it.id == session.id }
            sessions.add(session)
        }
    }

    override suspend fun delete(session: SessionEntity) {
        synchronized(lock) { sessions.remove(session) }
    }

    override suspend fun deleteById(id: String) {
        synchronized(lock) { sessions.removeAll { it.id == id } }
    }

    override suspend fun updateTitle(id: String, title: String, updatedAt: Long) {
        synchronized(lock) {
            val existing = sessions.find { it.id == id } ?: return
            sessions.remove(existing)
            sessions.add(existing.copy(title = title, updatedAt = updatedAt))
        }
    }
}

private class ChatTestProjectDao : ProjectDao {
    private val projects = mutableListOf<ProjectEntity>()

    override fun getAll(): Flow<List<ProjectEntity>> = flowOf(projects.toList())
    override suspend fun getById(id: String): ProjectEntity? = projects.find { it.id == id }

    override suspend fun upsert(project: ProjectEntity) {
        projects.removeAll { it.id == project.id }
        projects.add(project)
    }

    override suspend fun delete(project: ProjectEntity) {
        projects.remove(project)
    }
}

private class ChatTestProviderConfigDao : ProviderConfigDao {
    override fun getAll(): Flow<List<ProviderConfigEntity>> = flowOf(emptyList())
    override suspend fun getAllList(): List<ProviderConfigEntity> = emptyList()
    override suspend fun getDefault(): ProviderConfigEntity? = null
    override fun getDefaultFlow(): Flow<ProviderConfigEntity?> = flowOf(null)
    override suspend fun getById(id: String): ProviderConfigEntity? = null
    override suspend fun upsert(config: ProviderConfigEntity) = Unit
    override suspend fun delete(config: ProviderConfigEntity) = Unit
    override suspend fun deleteById(id: String) = Unit
    override suspend fun setDefault(providerId: String) = Unit
}

private class ChatTestApiKeyStorage : ApiKeyStorage {
    override fun storeKey(providerId: String, apiKey: String) = Unit
    override fun getKey(providerId: String): String? = null
    override fun removeKey(providerId: String) = Unit
}

private class ChatTestTaskManager : TaskManager {
    override val activeTasksFlow: StateFlow<List<TaskSummary>> = MutableStateFlow(emptyList())

    override suspend fun launch(
        command: String,
        workingDir: File?,
        waitSeconds: Int,
        isBackground: Boolean,
        onOutputChunk: ((String) -> Unit)?,
        yieldEarlyIf: (() -> Boolean)?
    ): TaskLaunchResult = TODO("Not used in ChatViewModel tests")

    override fun getStatus(taskId: String): TaskStatusInfo? = TODO("Not used in ChatViewModel tests")
    override fun kill(taskId: String): Boolean = TODO("Not used in ChatViewModel tests")
    override fun sendInput(taskId: String, input: String): Boolean = TODO("Not used in ChatViewModel tests")
    override fun list(): List<TaskSummary> = TODO("Not used in ChatViewModel tests")
    override fun killAll() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var messageDao: ChatTestMessageDao
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val aiProvider = FakeAiProvider()
        val fileSystem = FakeProjectFileSystem()
        val toolExecutor = ToolExecutor()
        val pendingChangeDao = FakePendingChangeDao()
        val pendingChangeManager = PendingChangeManager(pendingChangeDao)
        val messageDao = ChatTestMessageDao()
        val sessionDao = ChatTestSessionDao()
        val projectDao = ChatTestProjectDao()
        val orchestrator = AgentOrchestrator(aiProvider, toolExecutor, ToolRegistry)
        val workManager = AgentWorkManager(
            context = ContextWrapper(null),
            agentOrchestrator = orchestrator,
            toolExecutor = toolExecutor,
            pendingChangeManager = pendingChangeManager,
            messageDao = messageDao,
            pendingChangeDao = pendingChangeDao,
            sessionDao = sessionDao,
            projectDao = projectDao,
            fileSystem = fileSystem,
            ioDispatcher = testDispatcher
        )
        val settingsRepository = SettingsRepository(
            ChatTestProviderConfigDao(),
            ChatTestApiKeyStorage()
        )

        this.messageDao = messageDao
        viewModel = ChatViewModel(
            agentOrchestrator = orchestrator,
            agentWorkManager = workManager,
            aiProvider = aiProvider,
            toolExecutor = toolExecutor,
            taskManager = ChatTestTaskManager(),
            pendingChangeManager = pendingChangeManager,
            projectDao = projectDao,
            sessionDao = sessionDao,
            messageDao = messageDao,
            pendingChangeDao = pendingChangeDao,
            settingsRepository = settingsRepository,
            fileSystem = fileSystem
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `persistMessage writes to the message session instead of the active session`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            val activeSessionId = viewModel.state.value.sessionId
            val otherSessionId = "other-session"
            val message = Message(
                id = "message-1",
                sessionId = otherSessionId,
                role = MessageRole.USER,
                content = "hello",
                timestamp = 1L
            )

            viewModel.persistMessage(message)

            val saved = messageDao.snapshot().filter { it.id == "message-1" }
            assertEquals(1, saved.size)
            assertEquals(otherSessionId, saved.single().sessionId)
            assertTrue(
                "Message must not be written to the active session",
                messageDao.snapshot().none {
                    it.id == "message-1" && it.sessionId == activeSessionId
                }
            )
        }
}
