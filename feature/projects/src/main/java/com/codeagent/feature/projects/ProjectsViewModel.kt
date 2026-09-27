package com.codeagent.feature.projects

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeagent.core.data.ProjectDao
import com.codeagent.core.data.ProjectEntity
import com.codeagent.core.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    val projects: StateFlow<List<Project>> = projectDao.getAll()
        .map { entities: List<ProjectEntity> -> entities.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addProject(pathOrUri: String, name: String) {
        val fileUri = if (pathOrUri.startsWith("file://") || pathOrUri.startsWith("content://")) {
            pathOrUri
        } else {
            Uri.fromFile(File(pathOrUri)).toString()
        }
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            projectDao.upsert(
                ProjectEntity(
                    id = id,
                    name = name.ifBlank { File(fileUri.removePrefix("file://")).name.ifBlank { "Project" } },
                    treeUri = fileUri,
                    lastOpened = System.currentTimeMillis()
                )
            )
        }
    }

    fun addProject(treeUri: Uri, name: String) {
        addProject(treeUri.toString(), name)
    }

    fun removeProject(project: Project) {
        viewModelScope.launch {
            projectDao.delete(
                ProjectEntity(project.id, project.name, project.treeUri, project.lastOpened)
            )
        }
    }

    private fun ProjectEntity.toModel() = Project(id, name, treeUri, lastOpened)
}
