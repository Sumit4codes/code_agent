package com.codeagent.feature.terminal

import android.net.Uri
import com.codeagent.core.data.ProjectDao
import com.codeagent.core.data.ProjectEntity
import com.codeagent.core.files.ProjectFileSystem
import com.codeagent.core.terminal.NativeBinaryManager
import com.codeagent.core.terminal.TerminalExecutor
import com.codeagent.core.terminal.TerminalResult
import com.codeagent.core.testing.FakeProjectFileSystem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var projectDao: FakeProjectDao
    private lateinit var fileSystem: FakeProjectFileSystem
    private lateinit var executor: FakeTerminalExecutor
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

    class FakeTerminalExecutor : TerminalExecutor {
        override var isEnabled: Boolean = true
        override var activeDirectory: File? = null
        var boundUri: Uri? = null
        val executedCommands = mutableListOf<String>()
        var executionHandler: (suspend (String, ((String) -> Unit)?) -> TerminalResult)? = null

        override fun bind(fileSystem: ProjectFileSystem, rootUri: Uri?, localWorkDir: File?) {
            this.boundUri = rootUri
            this.activeDirectory = localWorkDir
        }

        override fun unbind() {
            this.boundUri = null
            this.activeDirectory = null
        }

        override suspend fun execute(command: String, onOutput: ((String) -> Unit)?): TerminalResult {
            if (!isEnabled) return TerminalResult.Disabled
            val trimmed = command.trim()
            executedCommands.add(trimmed)

            executionHandler?.let { return it(command, onOutput) }

            return when {
                trimmed.startsWith("echo ") -> {
                    val output = trimmed.removePrefix("echo ").trim('\'', '"')
                    onOutput?.invoke(output)
                    TerminalResult.Success(output, 0)
                }
                trimmed == "git --version" -> {
                    val output = "git version 2.43.0"
                    onOutput?.invoke(output)
                    TerminalResult.Success(output, 0)
                }
                trimmed == "busybox --help" -> {
                    val output = "BusyBox v1.36.1"
                    onOutput?.invoke(output)
                    TerminalResult.Success(output, 0)
                }
                trimmed.startsWith("cd ") -> {
                    val target = trimmed.removePrefix("cd ").trim()
                    activeDirectory = File(activeDirectory ?: File("/"), target)
                    TerminalResult.Success("", 0)
                }
                else -> {
                    TerminalResult.Success("", 0)
                }
            }
        }
    }

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        tempDir = Files.createTempDirectory("terminal-vm-test").toFile()
        projectDao = FakeProjectDao()
        fileSystem = FakeProjectFileSystem()
        executor = FakeTerminalExecutor()
        executor.activeDirectory = tempDir
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
    fun `initial state contains system info entry`() = runTest(testDispatcher) {
        val state = viewModel.uiState.value
        assertTrue(state.entries.isNotEmpty())
        assertEquals(TerminalEntryType.SYSTEM, state.entries.first().type)
        assertTrue(state.entries.first().text.contains("CodeAgent POSIX Terminal Engine"))
    }

    @Test
    fun `execute clear clears terminal entries`() = runTest(testDispatcher) {
        viewModel.executeCommand("clear")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.entries.size)
    }

    @Test
    fun `execute help displays built-in help text`() = runTest(testDispatcher) {
        viewModel.executeCommand("help")
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.entries.any { it.text.contains("Built-in Commands") })
    }

    @Test
    fun `executeCommand runs real echo process and captures output`() = runTest(testDispatcher) {
        viewModel.executeCommand("echo 'Hello Terminal'")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.entries.any { it.type == TerminalEntryType.COMMAND && it.text.contains("echo 'Hello Terminal'") })
        assertTrue(state.entries.any { it.type == TerminalEntryType.STDOUT && it.text.contains("Hello Terminal") })
        assertFalse(state.isRunning)
    }

    @Test
    fun `executeCommand displays stderr on command failure`() = runTest(testDispatcher) {
        executor.executionHandler = { cmd, _ ->
            TerminalResult.Error("command not found: $cmd", 127)
        }
        viewModel.executeCommand("invalid_cmd")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.entries.any { it.type == TerminalEntryType.STDERR && it.text.contains("Exit 127") })
        assertFalse(state.isRunning)
    }

    @Test
    fun `executeCommand shows message when terminal is disabled`() = runTest(testDispatcher) {
        executor.isEnabled = false
        viewModel.executeCommand("echo test")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.entries.any { it.type == TerminalEntryType.STDERR && it.text.contains("disabled") })
    }

    @Test
    fun `history navigation cycles through previous commands`() = runTest(testDispatcher) {
        viewModel.executeCommand("ls")
        viewModel.executeCommand("pwd")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigateHistoryPrevious()
        assertEquals("pwd", viewModel.uiState.value.commandInput)

        viewModel.navigateHistoryPrevious()
        assertEquals("ls", viewModel.uiState.value.commandInput)

        viewModel.navigateHistoryNext()
        assertEquals("pwd", viewModel.uiState.value.commandInput)
    }

    @Test
    fun `insertAccessoryKey appends key token`() = runTest(testDispatcher) {
        viewModel.onCommandInputChange("git")
        viewModel.insertAccessoryKey("status")
        assertEquals("git status", viewModel.uiState.value.commandInput)
    }

    @Test
    fun `cancelRunningCommand aborts command and appends termination entry`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        executor.executionHandler = { _, _ ->
            gate.await()
            TerminalResult.Success("", 0)
        }
        viewModel.executeCommand("long_running")
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isRunning)

        viewModel.cancelRunningCommand()
        val state = viewModel.uiState.value
        assertFalse(state.isRunning)
        assertTrue(state.entries.any { it.text.contains("^C") })

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `openProject binds workspace and adds system entry`() = runTest(testDispatcher) {
        val proj = ProjectEntity(
            id = "proj-1",
            name = "Test Project",
            treeUri = "file://${tempDir.absolutePath}",
            lastOpened = 1000L
        )
        projectDao.upsert(proj)

        viewModel.openProject("proj-1")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Test Project", state.projectName)
        assertEquals("proj-1", state.projectId)
        assertTrue(state.entries.any { it.text.contains("Workspace switched to: Test Project") })
    }

    @Test
    fun `execute bins displays available commands list`() = runTest(testDispatcher) {
        viewModel.executeCommand("bins")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.entries.any { it.text.contains("Available Commands in Terminal") })
        assertTrue(state.entries.any { it.text.contains("Android Toybox") })
    }

    @Test
    fun `executeCommand with system bin error appends SELinux notice`() = runTest(testDispatcher) {
        executor.executionHandler = { _, _ ->
            TerminalResult.Error("ls: /system/bin: Permission denied", 1)
        }
        viewModel.executeCommand("ls /system/bin")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val errEntry = state.entries.firstOrNull { it.type == TerminalEntryType.STDERR }
        assertNotNull(errEntry)
        assertTrue(errEntry!!.text.contains("Android SELinux blocks directory enumeration"))
    }

    @Test
    fun `terminal font size can be increased, decreased, and clamped`() = runTest(testDispatcher) {
        assertEquals(14, viewModel.uiState.value.fontSizeSp)

        viewModel.increaseFontSize()
        assertEquals(15, viewModel.uiState.value.fontSizeSp)

        viewModel.decreaseFontSize()
        assertEquals(14, viewModel.uiState.value.fontSizeSp)

        viewModel.setFontSize(22)
        assertEquals(22, viewModel.uiState.value.fontSizeSp)

        // Clamping min & max
        viewModel.setFontSize(5)
        assertEquals(9, viewModel.uiState.value.fontSizeSp)

        viewModel.setFontSize(50)
        assertEquals(28, viewModel.uiState.value.fontSizeSp)
    }

    @Test
    fun `viewClient pinch zoom scales font size`() = runTest(testDispatcher) {
        assertEquals(14, viewModel.uiState.value.fontSizeSp)

        // Pinch zoom in (scale > 1.1)
        val resetScale1 = viewModel.viewClient.onScale(1.2f)
        assertEquals(1.0f, resetScale1, 0.001f)
        assertEquals(15, viewModel.uiState.value.fontSizeSp)

        // Pinch zoom out (scale < 0.9)
        val resetScale2 = viewModel.viewClient.onScale(0.8f)
        assertEquals(1.0f, resetScale2, 0.001f)
        assertEquals(14, viewModel.uiState.value.fontSizeSp)

        // Normal small movement does not trigger scale threshold
        val normalScale = viewModel.viewClient.onScale(1.02f)
        assertEquals(1.02f, normalScale, 0.001f)
        assertEquals(14, viewModel.uiState.value.fontSizeSp)
    }
}

