package com.codeagent.feature.projects

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeagent.core.model.Project

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel = hiltViewModel(),
    onProjectSelected: ((Project) -> Unit)? = null,
    onChatWithProject: ((Project) -> Unit)? = null
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val scaffoldState = rememberTopAppBarState()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = extractProjectName(uri)
            viewModel.addProject(uri, name)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CodeAgent") },
                actions = {
                    IconButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "Open project folder")
                    }
                },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(scaffoldState)
            )
        }
    ) { padding ->
        if (projects.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No projects yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap + to open a project folder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectItem(
                        project = project,
                        onBrowse = { onProjectSelected?.invoke(project) },
                        onChat = { onChatWithProject?.invoke(project) ?: onProjectSelected?.invoke(project) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectItem(
    project: Project,
    onBrowse: () -> Unit,
    onChat: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = { Text(project.name) },
            leadingContent = {
                Icon(Icons.Default.Folder, contentDescription = null)
            },
            supportingContent = {
                Text(
                    project.treeUri.removePrefix("content://com.android.externalstorage.documents/tree/"),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = {
                Row {
                    IconButton(onClick = onBrowse) {
                        Icon(Icons.Default.Folder, contentDescription = "Browse files")
                    }
                    IconButton(onClick = onChat) {
                        Icon(Icons.Default.Chat, contentDescription = "Chat")
                    }
                }
            }
        )
    }
}

private fun extractProjectName(uri: Uri): String {
    val path = uri.path ?: return "Project"
    // path is like /tree/primary:Documents
    val segments = path.split("/")
    val lastSegment = segments.lastOrNull() ?: return "Project"
    // Decode URI encoding and extract name after the colon (e.g., "primary:Documents" → "Documents")
    val decoded = lastSegment.replace("%20", " ").replace("%3A", ":")
    val colonIndex = decoded.indexOf(':')
    return if (colonIndex >= 0) decoded.substring(colonIndex + 1) else decoded
}
