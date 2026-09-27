package com.codeagent.feature.terminal

import com.codeagent.core.data.ProjectDao
import com.codeagent.core.data.ProjectEntity
import com.codeagent.core.terminal.NativeBinaryManager
import com.codeagent.core.terminal.PosixTerminalExecutor
import com.codeagent.core.testing.FakeProjectFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var projectDao: FakeProjectDao
    private lateinit var fileSystem: FakeProjectFileSystem
    private lateinit var executor: PosixTerminalExecutor
    private lateinit var binaryManager: NativeBinaryManager
    private lateinit var viewModel: TerminalViewModel
    private lateinit var tempDir: File

    class FakeProjectDao : ProjectDao {
        val projects = mutableMapOf<String, ProjectEntity>()
        override fun getAll(): Flow<List<ProjectEntity>> = flowOf(projects.values.toList())
        override suspend fun getById(id: String): ProjectEntity? = projects[id]
        override suspend fun upsert(project: ProjectEntity) { projects[project.id] = project }
        override suspend fun delete(project: ProjectEntity) { projects.remove(project.id) }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempDir = Files.createTempDirectory("terminal-vm-test").toFile()
        projectDao = FakeProjectDao()
        fileSystem = FakeProjectFileSystem()
        executor = PosixTerminalExecutor()
        executor.ioDispatcher = testDispatcher
        executor.setWorkingDirectory(tempDir)
        binaryManager = NativeBinaryManager()
        binaryManager.ioDispatcher = testDispatcher
        viewModel = TerminalViewModel(projectDao, fileSystem, executor, binaryManager, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    @Test
    fun `initial state contains system info entry`() {
        val state = viewModel.uiState.value
        assertTrue(state.entries.isNotEmpty())
        assertEquals(TerminalEntryType.SYSTEM, state.entries.first().type)
        assertTrue(state.entries.first().text.contains("CodeAgent POSIX Terminal Engine"))
    }

    @Test
    fun `execute clear clears terminal entries`() {
        viewModel.executeCommand("clear")
        assertEquals(0, viewModel.uiState.value.entries.size)
    }

    @Test
    fun `execute help displays built-in help text`() {
        viewModel.executeCommand("help")
        val state = viewModel.uiState.value
        assertTrue(state.entries.any { it.text.contains("Built-in Commands") })
    }

    @Test
    fun `executeCommand runs real echo process and captures output`() = runTest {
        viewModel.executeCommand("echo 'Hello Terminal'")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.entries.any { it.type == TerminalEntryType.COMMAND && it.text.contains("echo 'Hello Terminal'") })
        assertTrue(state.entries.any { it.type == TerminalEntryType.STDOUT && it.text.contains("Hello Terminal") })
        assertFalse(state.isRunning)
    }

    @Test
    fun `history navigation cycles through previous commands`() {
        viewModel.executeCommand("ls")
        viewModel.executeCommand("pwd")

        viewModel.navigateHistoryPrevious()
        assertEquals("pwd", viewModel.uiState.value.commandInput)

        viewModel.navigateHistoryPrevious()
        assertEquals("ls", viewModel.uiState.value.commandInput)

        viewModel.navigateHistoryNext()
        assertEquals("pwd", viewModel.uiState.value.commandInput)
    }

    @Test
    fun `insertAccessoryKey appends key token`() {
        viewModel.onCommandInputChange("git")
        viewModel.insertAccessoryKey("status")
        assertEquals("git status", viewModel.uiState.value.commandInput)
    }
}
