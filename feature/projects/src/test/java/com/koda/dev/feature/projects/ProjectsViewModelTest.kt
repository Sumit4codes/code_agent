package com.koda.dev.feature.projects

import android.net.FakeUri
import com.koda.dev.core.data.ProjectDao
import com.koda.dev.core.data.ProjectEntity
import com.koda.dev.core.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var projectDao: FakeProjectDao
    private lateinit var viewModel: ProjectsViewModel
    private var fakeTime = 1_000L

    private class FakeProjectDao : ProjectDao {
        private val items = MutableStateFlow<List<ProjectEntity>>(emptyList())

        override fun getAll(): Flow<List<ProjectEntity>> =
            items.map { list -> list.sortedByDescending { it.lastOpened } }

        override suspend fun getById(id: String): ProjectEntity? =
            items.value.firstOrNull { it.id == id }

        override suspend fun upsert(project: ProjectEntity) {
            items.value = items.value.filterNot { it.id == project.id } + project
        }

        override suspend fun delete(project: ProjectEntity) {
            items.value = items.value.filterNot { it.id == project.id }
        }

        fun current(): List<ProjectEntity> = items.value.sortedByDescending { it.lastOpened }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeTime = 1_000L
        projectDao = FakeProjectDao()
        viewModel = ProjectsViewModel(projectDao).apply {
            nowMillis = { fakeTime }
            fileUriBuilder = { file -> "file://${file.path}" }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addProject deduplicates same directory across raw path, trailing slash, and file URI while preserving ID`() = runTest {
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.projects.collect {}
        }

        fakeTime = 100L
        viewModel.addProject("/storage/emulated/0/MyApp", "MyApp")
        advanceUntilIdle()

        assertEquals(1, viewModel.projects.value.size)
        val originalId = viewModel.projects.value.first().id
        assertEquals("file:///storage/emulated/0/MyApp", viewModel.projects.value.first().treeUri)

        // Re-adding same directory with trailing slash and file:// URI updates recency without duplicating
        fakeTime = 500L
        viewModel.addProject("file:///storage/emulated/0/MyApp/", "")
        advanceUntilIdle()

        assertEquals(1, projectDao.current().size)
        val updated = viewModel.projects.value.single()
        assertEquals(originalId, updated.id)
        assertEquals("MyApp", updated.name)
        assertEquals(500L, updated.lastOpened)

        // Re-adding via Uri overload also deduplicates
        fakeTime = 900L
        viewModel.addProject(FakeUri("file:///storage/emulated/0/MyApp"), "MyApp Renamed")
        advanceUntilIdle()

        assertEquals(1, projectDao.current().size)
        assertEquals(originalId, viewModel.projects.value.single().id)
        assertEquals("MyApp Renamed", viewModel.projects.value.single().name)
        assertEquals(900L, viewModel.projects.value.single().lastOpened)

        collector.cancel()
    }

    @Test
    fun `markProjectOpened refreshes lastOpened and reorders recent projects`() = runTest {
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.projects.collect {}
        }

        fakeTime = 100L
        viewModel.addProject("/work/alpha", "Alpha")
        advanceUntilIdle()

        fakeTime = 200L
        viewModel.addProject("/work/beta", "Beta")
        advanceUntilIdle()

        assertEquals(listOf("Beta", "Alpha"), viewModel.projects.value.map { it.name })

        val alpha = viewModel.projects.value.first { it.name == "Alpha" }
        fakeTime = 300L
        viewModel.markProjectOpened(alpha)
        advanceUntilIdle()

        assertEquals(listOf("Alpha", "Beta"), viewModel.projects.value.map { it.name })
        assertEquals(300L, viewModel.projects.value.first().lastOpened)

        collector.cancel()
    }

    @Test
    fun `removeProject removes entry and fallback name uses folder name`() = runTest {
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.projects.collect {}
        }

        fakeTime = 100L
        viewModel.addProject("/work/sample_repo/", "")
        advanceUntilIdle()

        val project: Project = viewModel.projects.value.single()
        assertEquals("sample_repo", project.name)

        viewModel.removeProject(project)
        advanceUntilIdle()

        assertTrue(viewModel.projects.value.isEmpty())
        collector.cancel()
    }
}
