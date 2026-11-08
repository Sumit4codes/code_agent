package com.koda.dev.feature.editor

import android.net.FakeUri
import com.koda.dev.core.data.ProjectEntity
import com.koda.dev.core.data.ProjectDao
import com.koda.dev.core.files.FileNode
import com.koda.dev.core.testing.FakeProjectFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var projectDao: FakeProjectDao
    private lateinit var fileSystem: FakeProjectFileSystem
    private lateinit var viewModel: EditorViewModel

    private val rootUri = FakeUri("content://test/root")

    class FakeProjectDao : ProjectDao {
        val projects = mutableListOf<ProjectEntity>()
        override fun getAll(): Flow<List<ProjectEntity>> = flowOf(projects)
        override suspend fun getById(id: String): ProjectEntity? = projects.find { it.id == id }
        override suspend fun upsert(project: ProjectEntity) {
            projects.removeAll { it.id == project.id }
            projects.add(project)
        }
        override suspend fun delete(project: ProjectEntity) {
            projects.removeAll { it.id == project.id }
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        projectDao = FakeProjectDao()
        fileSystem = FakeProjectFileSystem("content://test/root")
        viewModel = EditorViewModel(projectDao, fileSystem).apply {
            uriParser = { FakeUri(it) }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `openProjectById loads directory and sets project state`() = runTest {
        val project = ProjectEntity(
            id = "proj-1",
            name = "Test Project",
            treeUri = rootUri.toString(),
            lastOpened = 1000L
        )
        projectDao.upsert(project)
        fileSystem.putFile("README.md", "# Test Project")
        fileSystem.putFile("src/Main.kt", "fun main() {}")

        viewModel.openProjectById("proj-1")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Test Project", state.projectName)
        assertEquals(rootUri.toString(), state.projectUri.toString())
        assertEquals(2, state.fileTree.size)
    }

    @Test
    fun `selectFile opens new tab and sets activeTab`() = runTest {
        fileSystem.putFile("main.py", "print('hello')")
        viewModel.openProject(rootUri, "Python Project")
        advanceUntilIdle()

        val mainNode = viewModel.state.value.fileTree.first { it.name == "main.py" }
        viewModel.selectFile(mainNode)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.openTabs.size)
        assertEquals(mainNode.uri, state.activeTabUri)
        val active = state.activeTab
        assertNotNull(active)
        assertEquals("main.py", active?.name)
        assertEquals("print('hello')", active?.currentContent)
        assertFalse(active?.isDirty ?: true)
    }

    @Test
    fun `selectFile on already open tab switches active without duplicating`() = runTest {
        fileSystem.putFile("a.txt", "Content A")
        fileSystem.putFile("b.txt", "Content B")
        viewModel.openProject(rootUri, "Multi Tab Project")
        advanceUntilIdle()

        val fileA = viewModel.state.value.fileTree.first { it.name == "a.txt" }
        val fileB = viewModel.state.value.fileTree.first { it.name == "b.txt" }

        viewModel.selectFile(fileA)
        advanceUntilIdle()
        viewModel.selectFile(fileB)
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.openTabs.size)
        assertEquals(fileB.uri, viewModel.state.value.activeTabUri)

        // Select file A again
        viewModel.selectFile(fileA)
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.openTabs.size)
        assertEquals(fileA.uri, viewModel.state.value.activeTabUri)
    }

    @Test
    fun `updateActiveContent updates text, sets isDirty and supports undo`() = runTest {
        fileSystem.putFile("script.sh", "echo 1")
        viewModel.openProject(rootUri, "Shell Project")
        advanceUntilIdle()

        val scriptNode = viewModel.state.value.fileTree.first { it.name == "script.sh" }
        viewModel.selectFile(scriptNode)
        advanceUntilIdle()

        viewModel.updateActiveContent("echo 1\necho 2")
        val active = viewModel.state.value.activeTab
        assertNotNull(active)
        assertEquals("echo 1\necho 2", active?.currentContent)
        assertTrue(active?.isDirty ?: false)

        // Undo
        viewModel.undoActiveTab()
        val afterUndo = viewModel.state.value.activeTab
        assertEquals("echo 1", afterUndo?.currentContent)
        assertFalse(afterUndo?.isDirty ?: true)
    }

    @Test
    fun `saveActiveFile writes changes to disk and clears isDirty`() = runTest {
        fileSystem.putFile("index.js", "console.log(1);")
        viewModel.openProject(rootUri, "Node Project")
        advanceUntilIdle()

        val jsNode = viewModel.state.value.fileTree.first { it.name == "index.js" }
        viewModel.selectFile(jsNode)
        advanceUntilIdle()

        viewModel.updateActiveContent("console.log('updated');")
        assertTrue(viewModel.state.value.activeTab?.isDirty ?: false)

        viewModel.saveActiveFile()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.activeTab?.isDirty ?: true)
        val onDisk = fileSystem.readTextFile(jsNode.uri)
        assertEquals("console.log('updated');", onDisk)
        assertEquals("Saved index.js", viewModel.state.value.userMessage)
    }

    @Test
    fun `closeTab on clean tab closes immediately and on dirty tab prompts dialog`() = runTest {
        fileSystem.putFile("file.txt", "Initial text")
        viewModel.openProject(rootUri, "Project")
        advanceUntilIdle()

        val node = viewModel.state.value.fileTree.first { it.name == "file.txt" }
        viewModel.selectFile(node)
        advanceUntilIdle()

        // Clean close
        viewModel.closeTab(node.uri)
        assertTrue(viewModel.state.value.openTabs.isEmpty())

        // Re-open and modify
        viewModel.selectFile(node)
        advanceUntilIdle()
        viewModel.updateActiveContent("Modified text")

        viewModel.closeTab(node.uri)
        // Tab should still be open, and unsavedTabDialog opened
        assertEquals(1, viewModel.state.value.openTabs.size)
        assertTrue(viewModel.state.value.unsavedTabDialog.isOpen)
        assertEquals("file.txt", viewModel.state.value.unsavedTabDialog.tabToClose?.name)

        // Confirm discard
        viewModel.confirmDiscardAndCloseTab()
        assertTrue(viewModel.state.value.openTabs.isEmpty())
        assertFalse(viewModel.state.value.unsavedTabDialog.isOpen)
    }

    @Test
    fun `createFile creates file on disk and opens in active editor tab`() = runTest {
        viewModel.openProject(rootUri, "Project")
        advanceUntilIdle()

        viewModel.createFile("Utils.kt")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Created Utils.kt", state.userMessage)
        val active = state.activeTab
        assertNotNull(active)
        assertEquals("Utils.kt", active?.name)
        assertTrue(state.fileTree.any { it.name == "Utils.kt" })
    }

    @Test
    fun `createFolder creates directory on disk and reloads tree`() = runTest {
        viewModel.openProject(rootUri, "Project")
        advanceUntilIdle()

        viewModel.createFolder("components")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Created folder components", state.userMessage)
        val folder = state.fileTree.find { it.name == "components" }
        assertNotNull(folder)
        assertTrue(folder?.isDirectory ?: false)
    }

    @Test
    fun `renameItem renames file and updates open tab`() = runTest {
        fileSystem.putFile("old.txt", "sample text")
        viewModel.openProject(rootUri, "Project")
        advanceUntilIdle()

        val oldNode = viewModel.state.value.fileTree.first { it.name == "old.txt" }
        viewModel.selectFile(oldNode)
        advanceUntilIdle()

        viewModel.renameItem(oldNode, "renamed.txt")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Renamed to renamed.txt", state.userMessage)
        assertEquals("renamed.txt", state.activeTab?.name)
        assertTrue(state.fileTree.any { it.name == "renamed.txt" })
        assertFalse(state.fileTree.any { it.name == "old.txt" })
    }

    @Test
    fun `deleteItem deletes file and closes open tab`() = runTest {
        fileSystem.putFile("del.txt", "to delete")
        viewModel.openProject(rootUri, "Project")
        advanceUntilIdle()

        val node = viewModel.state.value.fileTree.first { it.name == "del.txt" }
        viewModel.selectFile(node)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.openTabs.size)

        viewModel.deleteItem(node)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Deleted del.txt", state.userMessage)
        assertTrue(state.openTabs.isEmpty())
        assertNull(state.activeTabUri)
        assertFalse(state.fileTree.any { it.name == "del.txt" })
    }

    @Test
    fun `buildRunCommand creates appropriate terminal commands`() {
        assertEquals("python3 \"test.py\"", buildRunCommand("test.py", "test.py"))
        assertEquals("node \"index.js\"", buildRunCommand("index.js", "index.js"))
        assertEquals("bash \"script.sh\"", buildRunCommand("script.sh", "script.sh"))
        assertEquals("kotlinc -script \"app.kts\"", buildRunCommand("app.kts", "app.kts"))
        assertEquals("gcc \"main.c\" -o /tmp/run_out && /tmp/run_out", buildRunCommand("main.c", "main.c"))
    }
}
