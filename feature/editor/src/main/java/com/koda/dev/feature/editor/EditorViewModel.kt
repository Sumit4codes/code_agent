package com.koda.dev.feature.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koda.dev.core.data.ProjectDao
import com.koda.dev.core.files.FileNode
import com.koda.dev.core.files.PathSafety
import com.koda.dev.core.files.ProjectFileSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val projectDao: ProjectDao,
    private val fileSystem: ProjectFileSystem
) : ViewModel() {

    internal var uriParser: (String) -> Uri = { Uri.parse(it) }

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    fun openProjectById(projectId: String) {
        if (_state.value.projectUri != null && _state.value.fileTree.isNotEmpty()) {
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val entity = projectDao.getById(projectId)
            if (entity != null) {
                val treeUri = uriParser(entity.treeUri)
                openProject(treeUri, entity.name)
            } else {
                _state.update { it.copy(isLoading = false, error = "Project not found") }
            }
        }
    }

    fun openProject(treeUri: Uri, name: String) {
        if (_state.value.projectUri == treeUri && _state.value.fileTree.isNotEmpty()) {
            return
        }
        _state.update {
            it.copy(
                projectUri = treeUri,
                projectName = name,
                breadcrumbs = listOf(Breadcrumb(name, treeUri)),
                openTabs = emptyList(),
                activeTabUri = null,
                isLoading = true,
                error = null,
                userMessage = null
            )
        }
        loadDirectory(treeUri, isAlreadyLoading = true)
    }

    fun navigateInto(node: FileNode) {
        if (!node.isDirectory) return
        val currentBreadcrumbs = _state.value.breadcrumbs
        _state.update {
            it.copy(breadcrumbs = currentBreadcrumbs + Breadcrumb(node.name, node.uri))
        }
        loadDirectory(node.uri)
    }

    fun navigateUp() {
        val current = _state.value.breadcrumbs
        if (current.size > 1) {
            val parent = current[current.size - 2]
            _state.update { it.copy(breadcrumbs = current.dropLast(1)) }
            loadDirectory(parent.uri)
        }
    }

    fun navigateToBreadcrumb(breadcrumb: Breadcrumb) {
        val current = _state.value.breadcrumbs
        val index = current.indexOf(breadcrumb)
        if (index >= 0) {
            _state.update { it.copy(breadcrumbs = current.take(index + 1)) }
            loadDirectory(breadcrumb.uri)
        }
    }

    fun selectFile(node: FileNode) {
        if (node.isDirectory) return

        // 1. If tab is already open, just switch to it
        val existingIndex = _state.value.openTabs.indexOfFirst { it.uri == node.uri }
        if (existingIndex >= 0) {
            _state.update { it.copy(activeTabUri = node.uri) }
            return
        }

        // 2. Otherwise load file content from disk and add a new tab
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val content = fileSystem.readTextFile(node.uri)
                if (content != null) {
                    val rootUriStr = _state.value.projectUri?.toString() ?: ""
                    val relPath = if (rootUriStr.isNotBlank() && node.uri.toString().startsWith(rootUriStr)) {
                        node.uri.toString().removePrefix(rootUriStr).removePrefix("/")
                    } else {
                        node.name
                    }
                    val newTab = EditorTab(
                        uri = node.uri,
                        name = node.name,
                        relativePath = relPath,
                        initialContent = content,
                        currentContent = content,
                        isDirty = false
                    )
                    _state.update { current ->
                        current.copy(
                            openTabs = current.openTabs + newTab,
                            activeTabUri = node.uri,
                            isLoading = false,
                            error = null
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Cannot open binary or unreadable file: ${node.name}"
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to open file: ${e.message}"
                    )
                }
            }
        }
    }

    fun switchTab(uri: Uri) {
        _state.update { it.copy(activeTabUri = uri) }
    }

    fun closeTab(uri: Uri, force: Boolean = false) {
        val tab = _state.value.openTabs.find { it.uri == uri } ?: return
        if (tab.isDirty && !force) {
            // Prompt user with unsaved changes dialog
            _state.update {
                it.copy(unsavedTabDialog = UnsavedTabDialogState(isOpen = true, tabToClose = tab))
            }
            return
        }

        // Close tab directly
        val currentTabs = _state.value.openTabs
        val tabIndex = currentTabs.indexOfFirst { it.uri == uri }
        val remainingTabs = currentTabs.filterNot { it.uri == uri }

        val newActiveUri = if (_state.value.activeTabUri == uri) {
            if (remainingTabs.isEmpty()) {
                null
            } else if (tabIndex < remainingTabs.size) {
                remainingTabs[tabIndex].uri
            } else {
                remainingTabs.last().uri
            }
        } else {
            _state.value.activeTabUri
        }

        _state.update {
            it.copy(
                openTabs = remainingTabs,
                activeTabUri = newActiveUri,
                unsavedTabDialog = UnsavedTabDialogState(isOpen = false, tabToClose = null)
            )
        }
    }

    fun closeActiveTab(force: Boolean = false) {
        val activeUri = _state.value.activeTabUri ?: return
        closeTab(activeUri, force)
    }

    fun confirmDiscardAndCloseTab() {
        val target = _state.value.unsavedTabDialog.tabToClose ?: return
        closeTab(target.uri, force = true)
    }

    fun confirmSaveAndCloseTab() {
        val target = _state.value.unsavedTabDialog.tabToClose ?: return
        viewModelScope.launch {
            saveTab(target.uri)
            closeTab(target.uri, force = true)
        }
    }

    fun dismissUnsavedTabDialog() {
        _state.update {
            it.copy(unsavedTabDialog = UnsavedTabDialogState(isOpen = false, tabToClose = null))
        }
    }

    fun updateActiveContent(newContent: String) {
        val activeUri = _state.value.activeTabUri ?: return
        _state.update { current ->
            val updatedTabs = current.openTabs.map { tab ->
                if (tab.uri == activeUri) {
                    val undo = if (tab.currentContent != newContent && tab.currentContent.isNotEmpty()) {
                        (tab.undoStack + tab.currentContent).takeLast(50)
                    } else {
                        tab.undoStack
                    }
                    tab.copy(
                        currentContent = newContent,
                        isDirty = newContent != tab.initialContent,
                        undoStack = undo,
                        redoStack = emptyList()
                    )
                } else {
                    tab
                }
            }
            current.copy(openTabs = updatedTabs)
        }
    }

    fun undoActiveTab() {
        val activeUri = _state.value.activeTabUri ?: return
        _state.update { current ->
            val updatedTabs = current.openTabs.map { tab ->
                if (tab.uri == activeUri && tab.undoStack.isNotEmpty()) {
                    val previous = tab.undoStack.last()
                    val newUndo = tab.undoStack.dropLast(1)
                    val newRedo = tab.redoStack + tab.currentContent
                    tab.copy(
                        currentContent = previous,
                        isDirty = previous != tab.initialContent,
                        undoStack = newUndo,
                        redoStack = newRedo
                    )
                } else {
                    tab
                }
            }
            current.copy(openTabs = updatedTabs)
        }
    }

    fun redoActiveTab() {
        val activeUri = _state.value.activeTabUri ?: return
        _state.update { current ->
            val updatedTabs = current.openTabs.map { tab ->
                if (tab.uri == activeUri && tab.redoStack.isNotEmpty()) {
                    val next = tab.redoStack.last()
                    val newRedo = tab.redoStack.dropLast(1)
                    val newUndo = tab.undoStack + tab.currentContent
                    tab.copy(
                        currentContent = next,
                        isDirty = next != tab.initialContent,
                        undoStack = newUndo,
                        redoStack = newRedo
                    )
                } else {
                    tab
                }
            }
            current.copy(openTabs = updatedTabs)
        }
    }

    fun saveActiveFile() {
        val active = _state.value.activeTab ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                fileSystem.writeTextFile(active.uri, active.currentContent)
                _state.update { current ->
                    val updatedTabs = current.openTabs.map { tab ->
                        if (tab.uri == active.uri) {
                            tab.copy(
                                initialContent = active.currentContent,
                                isDirty = false
                            )
                        } else {
                            tab
                        }
                    }
                    current.copy(
                        openTabs = updatedTabs,
                        isSaving = false,
                        userMessage = "Saved ${active.name}"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = "Failed to save ${active.name}: ${e.message}"
                    )
                }
            }
        }
    }

    fun saveTab(uri: Uri) {
        val tab = _state.value.openTabs.find { it.uri == uri } ?: return
        viewModelScope.launch {
            try {
                fileSystem.writeTextFile(tab.uri, tab.currentContent)
                _state.update { current ->
                    val updatedTabs = current.openTabs.map { t ->
                        if (t.uri == uri) {
                            t.copy(initialContent = t.currentContent, isDirty = false)
                        } else {
                            t
                        }
                    }
                    current.copy(openTabs = updatedTabs)
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Save failed: ${e.message}") }
            }
        }
    }

    // --- File Explorer CRUD ---

    fun openCreateFileDialog(parentUri: Uri? = null) {
        val targetParent = parentUri ?: _state.value.breadcrumbs.lastOrNull()?.uri
        _state.update {
            it.copy(
                createFileDialog = CreateFileDialogState(
                    isOpen = true,
                    parentUri = targetParent,
                    initialName = ""
                )
            )
        }
    }

    fun closeCreateFileDialog() {
        _state.update { it.copy(createFileDialog = CreateFileDialogState(isOpen = false)) }
    }

    fun createFile(name: String, parentUri: Uri? = null) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed.contains("/") || trimmed.contains("\\")) {
            _state.update {
                it.copy(createFileDialog = it.createFileDialog.copy(error = "Invalid file name"))
            }
            return
        }

        val targetParent = parentUri ?: _state.value.createFileDialog.parentUri ?: _state.value.breadcrumbs.lastOrNull()?.uri
        if (targetParent == null) {
            _state.update {
                it.copy(createFileDialog = it.createFileDialog.copy(error = "No parent folder selected"))
            }
            return
        }

        viewModelScope.launch {
            try {
                val createdUri = fileSystem.createFile(targetParent, trimmed, "text/plain")
                if (createdUri != null) {
                    closeCreateFileDialog()
                    refresh()
                    // Open newly created file in an editor tab!
                    selectFile(FileNode(uri = createdUri, name = trimmed, isDirectory = false))
                    _state.update { it.copy(userMessage = "Created $trimmed") }
                } else {
                    _state.update {
                        it.copy(createFileDialog = it.createFileDialog.copy(error = "Failed to create file (already exists?)"))
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(createFileDialog = it.createFileDialog.copy(error = e.message))
                }
            }
        }
    }

    fun openCreateFolderDialog(parentUri: Uri? = null) {
        val targetParent = parentUri ?: _state.value.breadcrumbs.lastOrNull()?.uri
        _state.update {
            it.copy(
                createFolderDialog = CreateFolderDialogState(
                    isOpen = true,
                    parentUri = targetParent,
                    initialName = ""
                )
            )
        }
    }

    fun closeCreateFolderDialog() {
        _state.update { it.copy(createFolderDialog = CreateFolderDialogState(isOpen = false)) }
    }

    fun createFolder(name: String, parentUri: Uri? = null) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed.contains("/") || trimmed.contains("\\")) {
            _state.update {
                it.copy(createFolderDialog = it.createFolderDialog.copy(error = "Invalid folder name"))
            }
            return
        }

        val targetParent = parentUri ?: _state.value.createFolderDialog.parentUri ?: _state.value.breadcrumbs.lastOrNull()?.uri
        if (targetParent == null) {
            _state.update {
                it.copy(createFolderDialog = it.createFolderDialog.copy(error = "No parent folder selected"))
            }
            return
        }

        viewModelScope.launch {
            try {
                val createdUri = fileSystem.createDirectory(targetParent, trimmed)
                if (createdUri != null) {
                    closeCreateFolderDialog()
                    refresh()
                    _state.update { it.copy(userMessage = "Created folder $trimmed") }
                } else {
                    _state.update {
                        it.copy(createFolderDialog = it.createFolderDialog.copy(error = "Failed to create folder"))
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(createFolderDialog = it.createFolderDialog.copy(error = e.message))
                }
            }
        }
    }

    fun openRenameDialog(node: FileNode) {
        _state.update {
            it.copy(
                renameDialog = RenameDialogState(
                    isOpen = true,
                    targetNode = node,
                    initialName = node.name
                )
            )
        }
    }

    fun closeRenameDialog() {
        _state.update { it.copy(renameDialog = RenameDialogState(isOpen = false)) }
    }

    fun renameItem(node: FileNode, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == node.name || trimmed.contains("/") || trimmed.contains("\\")) {
            _state.update {
                it.copy(renameDialog = it.renameDialog.copy(error = "Invalid or unchanged name"))
            }
            return
        }

        viewModelScope.launch {
            try {
                val renamedUri = fileSystem.rename(node.uri, trimmed)
                if (renamedUri != null) {
                    closeRenameDialog()

                    // Update any matching open tab(s)
                    _state.update { current ->
                        val updatedTabs = current.openTabs.map { tab ->
                            if (tab.uri == node.uri) {
                                tab.copy(uri = renamedUri, name = trimmed, relativePath = trimmed)
                            } else if (node.isDirectory && tab.uri.toString().startsWith(node.uri.toString())) {
                                val newChildUriStr = tab.uri.toString().replaceFirst(node.uri.toString(), renamedUri.toString())
                                tab.copy(uri = uriParser(newChildUriStr))
                            } else {
                                tab
                            }
                        }
                        val newActiveUri = if (current.activeTabUri == node.uri) renamedUri else current.activeTabUri
                        current.copy(
                            openTabs = updatedTabs,
                            activeTabUri = newActiveUri,
                            userMessage = "Renamed to $trimmed"
                        )
                    }

                    refresh()
                } else {
                    _state.update {
                        it.copy(renameDialog = it.renameDialog.copy(error = "Rename failed (already exists?)"))
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(renameDialog = it.renameDialog.copy(error = e.message))
                }
            }
        }
    }

    fun openDeleteDialog(node: FileNode) {
        _state.update {
            it.copy(deleteDialog = DeleteDialogState(isOpen = true, targetNode = node))
        }
    }

    fun closeDeleteDialog() {
        _state.update { it.copy(deleteDialog = DeleteDialogState(isOpen = false)) }
    }

    fun deleteItem(node: FileNode) {
        viewModelScope.launch {
            try {
                val deleted = fileSystem.deleteFile(node.uri)
                if (deleted) {
                    closeDeleteDialog()

                    // Close any tabs belonging to this file or folder
                    _state.update { current ->
                        val remaining = current.openTabs.filterNot { tab ->
                            tab.uri == node.uri || (node.isDirectory && tab.uri.toString().startsWith(node.uri.toString()))
                        }
                        val newActive = if (remaining.any { it.uri == current.activeTabUri }) {
                            current.activeTabUri
                        } else {
                            remaining.firstOrNull()?.uri
                        }
                        current.copy(
                            openTabs = remaining,
                            activeTabUri = newActive,
                            userMessage = "Deleted ${node.name}"
                        )
                    }

                    refresh()
                } else {
                    _state.update { it.copy(error = "Failed to delete ${node.name}") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Delete failed: ${e.message}") }
            }
        }
    }

    fun refresh() {
        val current = _state.value.breadcrumbs.lastOrNull()
        if (current != null) {
            loadDirectory(current.uri)
        }
    }

    fun clearUserMessage() {
        _state.update { it.copy(userMessage = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun closeFile() {
        closeActiveTab(force = false)
    }

    private fun loadDirectory(uri: Uri, isAlreadyLoading: Boolean = false) {
        viewModelScope.launch {
            try {
                if (!isAlreadyLoading) {
                    _state.update { it.copy(isLoading = true, error = null) }
                }
                val children = fileSystem.listChildren(uri)
                val filtered = children.filter { node ->
                    !PathSafety.shouldIgnore(node.name, node.isDirectory)
                }.sortedWith(compareBy<FileNode> { !it.isDirectory }.thenBy { it.name.lowercase() })
                _state.update { it.copy(fileTree = filtered, isLoading = false, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed to load files: ${e.message}") }
            }
        }
    }
}
