package com.codeagent.feature.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeagent.core.data.ProjectDao
import com.codeagent.core.files.FileNode
import com.codeagent.core.files.ProjectFileSystem
import com.codeagent.core.files.SafProjectFileSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Breadcrumb(val name: String, val uri: Uri)

data class EditorState(
    val projectUri: Uri? = null,
    val projectName: String = "",
    val breadcrumbs: List<Breadcrumb> = emptyList(),
    val fileTree: List<FileNode> = emptyList(),
    val selectedFile: FileNode? = null,
    val fileContent: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val projectDao: ProjectDao,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var fileSystem: SafProjectFileSystem? = null

    fun openProjectById(projectId: String) {
        viewModelScope.launch {
            val entity = projectDao.getById(projectId)
            if (entity != null) {
                val treeUri = Uri.parse(entity.treeUri)
                openProject(treeUri, entity.name)
            } else {
                _state.value = _state.value.copy(error = "Project not found")
            }
        }
    }

    fun openProject(treeUri: Uri, name: String) {
        fileSystem = SafProjectFileSystem(context)
        _state.value = _state.value.copy(
            projectUri = treeUri,
            projectName = name,
            breadcrumbs = listOf(Breadcrumb(name, treeUri)),
            selectedFile = null,
            fileContent = null,
            error = null
        )
        loadDirectory(treeUri)
    }

    fun navigateInto(node: FileNode) {
        if (!node.isDirectory) return
        val currentBreadcrumbs = _state.value.breadcrumbs
        _state.value = _state.value.copy(
            breadcrumbs = currentBreadcrumbs + Breadcrumb(node.name, node.uri)
        )
        loadDirectory(node.uri)
    }

    fun navigateUp() {
        val current = _state.value.breadcrumbs
        if (current.size > 1) {
            val parent = current[current.size - 2]
            _state.value = _state.value.copy(
                breadcrumbs = current.dropLast(1)
            )
            loadDirectory(parent.uri)
        }
    }

    fun navigateToBreadcrumb(breadcrumb: Breadcrumb) {
        val current = _state.value.breadcrumbs
        val index = current.indexOf(breadcrumb)
        if (index >= 0) {
            _state.value = _state.value.copy(
                breadcrumbs = current.take(index + 1)
            )
            loadDirectory(breadcrumb.uri)
        }
    }

    fun selectFile(node: FileNode) {
        _state.value = _state.value.copy(selectedFile = node, isLoading = true, fileContent = null)
        viewModelScope.launch {
            val content = fileSystem?.readTextFile(node.uri)
            _state.value = _state.value.copy(
                fileContent = content,
                isLoading = false,
                error = if (content == null) "Cannot read file (may be binary)" else null
            )
        }
    }

    fun closeFile() {
        _state.value = _state.value.copy(
            selectedFile = null,
            fileContent = null,
            error = null
        )
    }

    fun refresh() {
        val current = _state.value.breadcrumbs.lastOrNull()
        if (current != null) {
            loadDirectory(current.uri)
        }
    }

    private fun loadDirectory(uri: Uri) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val children = fileSystem?.listChildren(uri) ?: emptyList()
                val filtered = children.filter { node ->
                    !com.codeagent.core.files.PathSafety.shouldIgnore(node.name, node.isDirectory)
                }.sortedWith(compareBy<FileNode> { !it.isDirectory }.thenBy { it.name.lowercase() })
                _state.value = _state.value.copy(fileTree = filtered, isLoading = false, error = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Failed to load files: ${e.message}")
            }
        }
    }
}
