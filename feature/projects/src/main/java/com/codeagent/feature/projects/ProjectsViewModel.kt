package com.codeagent.feature.projects

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeagent.core.data.ProjectDao
import com.codeagent.core.data.ProjectEntity
import com.codeagent.core.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectDao: ProjectDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val projects: StateFlow<List<Project>> = projectDao.getAll()
        .map { entities: List<ProjectEntity> -> entities.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addProject(treeUri: Uri, name: String) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            projectDao.upsert(
                ProjectEntity(
                    id = id,
                    name = name,
                    treeUri = treeUri.toString(),
                    lastOpened = System.currentTimeMillis()
                )
            )
            persistUriPermission(treeUri)
        }
    }

    fun removeProject(project: Project) {
        viewModelScope.launch {
            projectDao.delete(
                ProjectEntity(project.id, project.name, project.treeUri, project.lastOpened)
            )
        }
    }

    private fun persistUriPermission(treeUri: Uri) {
        try {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: Exception) { }
    }

    private fun ProjectEntity.toModel() = Project(id, name, treeUri, lastOpened)
}
