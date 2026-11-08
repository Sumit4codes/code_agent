package com.koda.dev.feature.editor

import android.net.Uri
import com.koda.dev.core.files.FileNode

data class Breadcrumb(val name: String, val uri: Uri)

data class EditorTab(
    val uri: Uri,
    val name: String,
    val relativePath: String = name,
    val initialContent: String = "",
    val currentContent: String = "",
    val isDirty: Boolean = currentContent != initialContent,
    val isReadOnly: Boolean = false,
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList()
) {
    val lineCount: Int
        get() = maxOf(1, currentContent.lines().size)
}

data class CreateFileDialogState(
    val isOpen: Boolean = false,
    val parentUri: Uri? = null,
    val initialName: String = "",
    val error: String? = null
)

data class CreateFolderDialogState(
    val isOpen: Boolean = false,
    val parentUri: Uri? = null,
    val initialName: String = "",
    val error: String? = null
)

data class RenameDialogState(
    val isOpen: Boolean = false,
    val targetNode: FileNode? = null,
    val initialName: String = "",
    val error: String? = null
)

data class DeleteDialogState(
    val isOpen: Boolean = false,
    val targetNode: FileNode? = null
)

data class UnsavedTabDialogState(
    val isOpen: Boolean = false,
    val tabToClose: EditorTab? = null
)

data class EditorState(
    val projectUri: Uri? = null,
    val projectName: String = "",
    val breadcrumbs: List<Breadcrumb> = emptyList(),
    val fileTree: List<FileNode> = emptyList(),
    val openTabs: List<EditorTab> = emptyList(),
    val activeTabUri: Uri? = null,
    val createFileDialog: CreateFileDialogState = CreateFileDialogState(),
    val createFolderDialog: CreateFolderDialogState = CreateFolderDialogState(),
    val renameDialog: RenameDialogState = RenameDialogState(),
    val deleteDialog: DeleteDialogState = DeleteDialogState(),
    val unsavedTabDialog: UnsavedTabDialogState = UnsavedTabDialogState(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val userMessage: String? = null
) {
    val activeTab: EditorTab?
        get() = openTabs.find { it.uri == activeTabUri } ?: openTabs.firstOrNull()

    // Backward compatibility with previous single-file view references
    val selectedFile: FileNode?
        get() = activeTab?.let { tab ->
            FileNode(
                uri = tab.uri,
                name = tab.name,
                isDirectory = false
            )
        }

    val fileContent: String?
        get() = activeTab?.currentContent
}
