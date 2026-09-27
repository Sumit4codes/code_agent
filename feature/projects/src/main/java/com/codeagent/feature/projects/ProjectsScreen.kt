package com.codeagent.feature.projects

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeagent.core.model.Project
import java.io.File

fun hasFullStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
}

fun requestFullStoragePermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            context.startActivity(intent)
        }
    } else {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel = hiltViewModel(),
    onProjectSelected: ((Project) -> Unit)? = null,
    onChatWithProject: ((Project) -> Unit)? = null
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val scaffoldState = rememberTopAppBarState()

    var hasPermission by remember { mutableStateOf(hasFullStoragePermission(context)) }
    var showFolderDialog by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        hasPermission = hasFullStoragePermission(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CodeAgent") },
                actions = {
                    IconButton(
                        onClick = {
                            if (!hasPermission) {
                                requestFullStoragePermission(context)
                            } else {
                                showFolderDialog = true
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Open project folder")
                    }
                },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(scaffoldState)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Permission banner if full device access is not yet granted
            if (!hasPermission) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Full Storage Access Required",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "CodeAgent needs All Files Access so AI coding agents, terminals, and git tools can directly read and edit your projects.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { requestFullStoragePermission(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("Grant All Files Access")
                        }
                    }
                }
            }

            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
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
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(projects, key = { it.id }) { project ->
                        ProjectItem(
                            project = project,
                            onBrowse = { onProjectSelected?.invoke(project) },
                            onChat = { onChatWithProject?.invoke(project) ?: onProjectSelected?.invoke(project) },
                            onDelete = { viewModel.removeProject(project) }
                        )
                    }
                }
            }
        }
    }

    if (showFolderDialog) {
        DirectoryPickerDialog(
            onDismiss = { showFolderDialog = false },
            onFolderSelected = { selectedDir ->
                showFolderDialog = false
                viewModel.addProject(selectedDir.absolutePath, selectedDir.name)
            }
        )
    }
}

@Composable
private fun ProjectItem(
    project: Project,
    onBrowse: () -> Unit,
    onChat: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onChat,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            headlineContent = {
                Text(project.name, style = MaterialTheme.typography.titleMedium)
            },
            leadingContent = {
                FilledTonalIconButton(onClick = onChat) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "Chat with ${project.name}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            supportingContent = {
                Text(
                    project.treeUri.removePrefix("file://"),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Row {
                    IconButton(onClick = onBrowse) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = "Browse files",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete project",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun DirectoryPickerDialog(
    onDismiss: () -> Unit,
    onFolderSelected: (File) -> Unit
) {
    val defaultDir = remember {
        val ext = Environment.getExternalStorageDirectory()
        if (ext != null && ext.exists()) ext else File("/storage/emulated/0")
    }

    var currentDir by remember { mutableStateOf(defaultDir) }
    var pathInput by remember { mutableStateOf(defaultDir.absolutePath) }

    val subdirectories = remember(currentDir) {
        try {
            currentDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Project Folder") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Direct Path Input
                OutlinedTextField(
                    value = pathInput,
                    onValueChange = {
                        pathInput = it
                        val f = File(it)
                        if (f.exists() && f.isDirectory) {
                            currentDir = f
                        }
                    },
                    label = { Text("Folder Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                Spacer(Modifier.height(8.dp))

                // Quick jump shortcuts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val ext = Environment.getExternalStorageDirectory()
                    val docs = File(ext, "Documents")
                    val download = File(ext, "Download")

                    SuggestionChip(
                        onClick = {
                            currentDir = ext
                            pathInput = ext.absolutePath
                        },
                        label = { Text("Root", style = MaterialTheme.typography.labelSmall) }
                    )
                    if (docs.exists()) {
                        SuggestionChip(
                            onClick = {
                                currentDir = docs
                                pathInput = docs.absolutePath
                            },
                            label = { Text("Documents", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    if (download.exists()) {
                        SuggestionChip(
                            onClick = {
                                currentDir = download
                                pathInput = download.absolutePath
                            },
                            label = { Text("Download", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Subdirectories listing
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        // Parent directory item
                        val parent = currentDir.parentFile
                        if (parent != null && parent.canRead()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentDir = parent
                                            pathInput = parent.absolutePath
                                        }
                                        .padding(8.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go up")
                                    Spacer(Modifier.width(8.dp))
                                    Text(".. (Up)", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        if (subdirectories.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No subdirectories",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(subdirectories) { dir ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentDir = dir
                                            pathInput = dir.absolutePath
                                        }
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        dir.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = File(pathInput)
                    if (target.exists() && target.isDirectory) {
                        onFolderSelected(target)
                    } else if (currentDir.exists() && currentDir.isDirectory) {
                        onFolderSelected(currentDir)
                    }
                }
            ) {
                Text("Select Folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
