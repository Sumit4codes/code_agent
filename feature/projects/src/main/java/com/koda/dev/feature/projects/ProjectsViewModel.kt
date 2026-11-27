package com.koda.dev.feature.projects

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koda.dev.core.data.ProjectDao
import com.koda.dev.core.data.ProjectEntity
import com.koda.dev.core.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectDao: ProjectDao
) : ViewModel() {

    internal var nowMillis: () -> Long = { System.currentTimeMillis() }
    internal var fileUriBuilder: (File) -> String = { file -> Uri.fromFile(file).toString() }

    val projects: StateFlow<List<Project>> = projectDao.getAll()
        .map { entities: List<ProjectEntity> -> entities.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addProject(pathOrUri: String, name: String) {
        val normalizedUri = normalizeTreeUri(pathOrUri)
        if (normalizedUri.isBlank()) return

        viewModelScope.launch {
            val timestamp = nowMillis()
            val existingProjects = projectDao.getAll().first()
            val existing = existingProjects.firstOrNull {
                normalizeTreeUri(it.treeUri) == normalizedUri
            }

            val resolvedName = name.trim().ifBlank {
                existing?.name?.ifBlank { null }
                    ?: File(normalizedUri.removePrefix("file://")).name.ifBlank { "Project" }
            }

            if (existing != null) {
                // Preserve existing project ID so linked chat sessions stay intact, while refreshing recency
                projectDao.upsert(
                    existing.copy(
                        name = resolvedName,
                        treeUri = normalizedUri,
                        lastOpened = timestamp
                    )
                )
            } else {
                projectDao.upsert(
                    ProjectEntity(
                        id = UUID.randomUUID().toString(),
                        name = resolvedName,
                        treeUri = normalizedUri,
                        lastOpened = timestamp
                    )
                )
            }
        }
    }

    fun addProject(treeUri: Uri, name: String) {
        addProject(treeUri.toString(), name)
    }

    fun markProjectOpened(project: Project) {
        viewModelScope.launch {
            val timestamp = nowMillis()
            val current = projectDao.getById(project.id)
            if (current != null) {
                projectDao.upsert(current.copy(lastOpened = timestamp))
            } else {
                projectDao.upsert(
                    ProjectEntity(
                        id = project.id,
                        name = project.name,
                        treeUri = project.treeUri,
                        lastOpened = timestamp
                    )
                )
            }
        }
    }

    fun removeProject(project: Project) {
        viewModelScope.launch {
            projectDao.delete(
                ProjectEntity(project.id, project.name, project.treeUri, project.lastOpened)
            )
        }
    }

    internal fun normalizeTreeUri(pathOrUri: String): String {
        val trimmed = pathOrUri.trim()
        if (trimmed.isEmpty()) return ""
        val uri = if (trimmed.startsWith("file://", ignoreCase = true) ||
            trimmed.startsWith("content://", ignoreCase = true)
        ) {
            trimmed
        } else {
            val cleanPath = trimmed.trimEnd('/').ifEmpty { "/" }
            fileUriBuilder(File(cleanPath))
        }
        return if (uri.startsWith("file://", ignoreCase = true)) {
            val withoutTrailing = uri.trimEnd('/')
            if (withoutTrailing == "file:/" || withoutTrailing == "file:") "file:///" else withoutTrailing
        } else {
            uri
        }
    }

    private fun ProjectEntity.toModel() = Project(id, name, treeUri, lastOpened)
}
