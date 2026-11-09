package com.koda.dev.feature.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.koda.dev.core.files.FileNode
import com.koda.dev.core.ui.glass.GlassDesign
import com.koda.dev.core.ui.glass.springPressClickable
import com.koda.dev.feature.terminal.WorkspaceTerminalSheet
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String = "",
    viewModel: EditorViewModel = hiltViewModel(),
    onNavigateBack: (() -> Unit)? = null,
    onOpenTerminal: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var wrapLines by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var userSplitPreference by remember { mutableStateOf<Boolean?>(null) }
    var showTerminalSheet by remember { mutableStateOf(false) }
    var terminalInitialCommand by remember { mutableStateOf<String?>(null) }

    // Surface user notifications in snackbar
    LaunchedEffect(state.userMessage) {
        val msg = state.userMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    // Load project on start
    LaunchedEffect(projectId) {
        if (projectId.isNotBlank() && projectId != "none") {
            viewModel.openProjectById(projectId)
        }
    }

    // Back handling: close drawer if open, otherwise check for unsaved active tab
    BackHandler {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else if (state.activeTab?.isDirty == true) {
            viewModel.closeActiveTab(force = false)
        } else if (state.openTabs.isNotEmpty()) {
            viewModel.closeActiveTab(force = true)
        } else if (onNavigateBack != null) {
            onNavigateBack()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalMaxWidth = maxWidth
        val isWideScreen = totalMaxWidth >= 600.dp
        val isSplitView = userSplitPreference ?: isWideScreen

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !isSplitView,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier
                        .widthIn(max = minOf(320.dp, totalMaxWidth * 0.85f))
                        .fillMaxHeight(),
                    drawerContainerColor = MaterialTheme.colorScheme.surface
                ) {
                    FileExplorerPane(
                        state = state,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onNavigateInto = { viewModel.navigateInto(it) },
                        onNavigateUp = { viewModel.navigateUp() },
                        onBreadcrumbClick = { viewModel.navigateToBreadcrumb(it) },
                        onFileSelect = { file ->
                            viewModel.selectFile(file)
                            coroutineScope.launch { drawerState.close() }
                        },
                        onNewFileClick = { parentUri -> viewModel.openCreateFileDialog(parentUri) },
                        onNewFolderClick = { parentUri -> viewModel.openCreateFolderDialog(parentUri) },
                        onRenameClick = { node -> viewModel.openRenameDialog(node) },
                        onDeleteClick = { node -> viewModel.openDeleteDialog(node) },
                        onRefreshClick = { viewModel.refresh() },
                        onCopyPath = { path ->
                            clipboardManager.setText(AnnotatedString(path))
                            coroutineScope.launch { snackbarHostState.showSnackbar("Copied path to clipboard") }
                        },
                        onCloseDrawer = {
                            coroutineScope.launch { drawerState.close() }
                        },
                        showDrawerHeader = true
                    )
                }
            }
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    EditorTopAppBar(
                        state = state,
                        isSplitView = isSplitView,
                        wrapLines = wrapLines,
                        onNavigateBack = onNavigateBack,
                        onToggleDrawer = {
                            coroutineScope.launch {
                                if (drawerState.isOpen) drawerState.close() else drawerState.open()
                            }
                        },
                        onToggleSplitView = {
                            userSplitPreference = !isSplitView
                        },
                        onToggleWrapLines = {
                            wrapLines = !wrapLines
                        },
                        onSave = {
                            viewModel.saveActiveFile()
                        },
                        onRunInTerminal = {
                            val activeTab = state.activeTab
                            if (activeTab != null) {
                                if (activeTab.isDirty) {
                                    viewModel.saveActiveFile()
                                }
                                val cmd = buildRunCommand(activeTab.name, activeTab.relativePath)
                                terminalInitialCommand = cmd
                                showTerminalSheet = true
                            }
                        },
                        onOpenTerminal = {
                            terminalInitialCommand = null
                            showTerminalSheet = true
                        },
                        onCopyContent = {
                            state.activeTab?.currentContent?.let { content ->
                                clipboardManager.setText(AnnotatedString(content))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Copied ${state.activeTab?.name ?: "file"} to clipboard")
                                }
                            }
                        }
                    )
                }
            ) { padding ->
                if (state.projectUri == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Open a project to browse files",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (isSplitView) {
                    // Split View: Explorer Sidebar + Code Editor
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .consumeWindowInsets(padding)
                            .imePadding()
                    ) {
                        val sidebarWidth = if (isWideScreen) 270.dp else (totalMaxWidth * 0.42f).coerceIn(180.dp, 300.dp)
                        Surface(
                            modifier = Modifier
                                .width(sidebarWidth)
                                .fillMaxHeight(),
                            tonalElevation = 1.dp
                        ) {
                            FileExplorerPane(
                                state = state,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                onNavigateInto = { viewModel.navigateInto(it) },
                                onNavigateUp = { viewModel.navigateUp() },
                                onBreadcrumbClick = { viewModel.navigateToBreadcrumb(it) },
                                onFileSelect = { viewModel.selectFile(it) },
                                onNewFileClick = { parentUri -> viewModel.openCreateFileDialog(parentUri) },
                                onNewFolderClick = { parentUri -> viewModel.openCreateFolderDialog(parentUri) },
                                onRenameClick = { node -> viewModel.openRenameDialog(node) },
                                onDeleteClick = { node -> viewModel.openDeleteDialog(node) },
                                onRefreshClick = { viewModel.refresh() },
                                onCopyPath = { path ->
                                    clipboardManager.setText(AnnotatedString(path))
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Copied path to clipboard") }
                                },
                                onCloseDrawer = null,
                                showDrawerHeader = false
                            )
                        }

                        VerticalDivider(thickness = 0.5.dp, color = Color(0xFF30363D))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            EditorCanvasPane(
                                state = state,
                                wrapLines = wrapLines,
                                onContentChange = { viewModel.updateActiveContent(it) },
                                onSave = { viewModel.saveActiveFile() },
                                onUndo = { viewModel.undoActiveTab() },
                                onRedo = { viewModel.redoActiveTab() },
                                onSelectTab = { viewModel.switchTab(it.uri) },
                                onCloseTab = { viewModel.closeTab(it.uri) },
                                onOpenDrawer = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                onNewFile = { viewModel.openCreateFileDialog() }
                            )
                        }
                    }
                } else {
                    // Mobile View (Full Screen):
                    // If tabs are open, show EditorCanvas in full width.
                    // If no tabs are open, show FileExplorer in full width.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .consumeWindowInsets(padding)
                            .imePadding()
                    ) {
                        if (state.openTabs.isNotEmpty()) {
                            EditorCanvasPane(
                                state = state,
                                wrapLines = wrapLines,
                                onContentChange = { viewModel.updateActiveContent(it) },
                                onSave = { viewModel.saveActiveFile() },
                                onUndo = { viewModel.undoActiveTab() },
                                onRedo = { viewModel.redoActiveTab() },
                                onSelectTab = { viewModel.switchTab(it.uri) },
                                onCloseTab = { viewModel.closeTab(it.uri) },
                                onOpenDrawer = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                onNewFile = { viewModel.openCreateFileDialog() }
                            )
                        } else {
                            FileExplorerPane(
                                state = state,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                onNavigateInto = { viewModel.navigateInto(it) },
                                onNavigateUp = { viewModel.navigateUp() },
                                onBreadcrumbClick = { viewModel.navigateToBreadcrumb(it) },
                                onFileSelect = { viewModel.selectFile(it) },
                                onNewFileClick = { parentUri -> viewModel.openCreateFileDialog(parentUri) },
                                onNewFolderClick = { parentUri -> viewModel.openCreateFolderDialog(parentUri) },
                                onRenameClick = { node -> viewModel.openRenameDialog(node) },
                                onDeleteClick = { node -> viewModel.openDeleteDialog(node) },
                                onRefreshClick = { viewModel.refresh() },
                                onCopyPath = { path ->
                                    clipboardManager.setText(AnnotatedString(path))
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Copied path to clipboard") }
                                },
                                onCloseDrawer = null,
                                showDrawerHeader = false
                            )
                        }
                    }
                }
            }
        }

        // --- Dialogs ---
        CreateFileDialog(
            state = state.createFileDialog,
            onDismiss = { viewModel.closeCreateFileDialog() },
            onCreate = { fileName -> viewModel.createFile(fileName) }
        )

        CreateFolderDialog(
            state = state.createFolderDialog,
            onDismiss = { viewModel.closeCreateFolderDialog() },
            onCreate = { folderName -> viewModel.createFolder(folderName) }
        )

        RenameDialog(
            state = state.renameDialog,
            onDismiss = { viewModel.closeRenameDialog() },
            onRename = { node, newName -> viewModel.renameItem(node, newName) }
        )

        DeleteConfirmationDialog(
            state = state.deleteDialog,
            onDismiss = { viewModel.closeDeleteDialog() },
            onConfirmDelete = { node -> viewModel.deleteItem(node) }
        )

        UnsavedChangesDialog(
            state = state.unsavedTabDialog,
            onDismiss = { viewModel.dismissUnsavedTabDialog() },
            onDiscard = { viewModel.confirmDiscardAndCloseTab() },
            onSaveAndClose = { viewModel.confirmSaveAndCloseTab() }
        )

        if (showTerminalSheet) {
            WorkspaceTerminalSheet(
                projectId = projectId.ifBlank { null },
                projectName = state.projectName.ifBlank { null },
                initialCommand = terminalInitialCommand,
                onDismiss = {
                    showTerminalSheet = false
                    terminalInitialCommand = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopAppBar(
    state: EditorState,
    isSplitView: Boolean,
    wrapLines: Boolean,
    onNavigateBack: (() -> Unit)?,
    onToggleDrawer: () -> Unit,
    onToggleSplitView: () -> Unit,
    onToggleWrapLines: () -> Unit,
    onSave: () -> Unit,
    onRunInTerminal: () -> Unit,
    onOpenTerminal: () -> Unit,
    onCopyContent: () -> Unit
) {
    val activeTab = state.activeTab
    val barShape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(
                elevation = 16.dp,
                shape = barShape,
                ambientColor = Color(0x33000000),
                spotColor = Color(0x660F172A)
            )
            .clip(barShape)
            .background(GlassDesign.liquidGlassBackground())
            .border(
                width = 1.dp,
                brush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.65f, dispersionAlpha = 0.28f),
                shape = barShape
            )
    ) {
        // Internal top refractive sheen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(GlassDesign.internalGlassSheen())
        )

        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = {
                if (onNavigateBack != null) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.18f, bottomAlpha = 0.06f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.38f, bottomAlpha = 0.10f), CircleShape)
                            .springPressClickable(onClick = onNavigateBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFFF0F6FC)
                        )
                    }
                }
            },
            title = {
                val currentTitle = activeTab?.name ?: state.projectName.ifBlank { "Files" }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Frosted Code/Folder Disc
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.20f, bottomAlpha = 0.08f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.40f, bottomAlpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (activeTab != null) Icons.Default.Code else Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color(0xFF58A6FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                fontFamily = if (activeTab != null) FontFamily.Monospace else FontFamily.Default,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (activeTab?.isDirty == true) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(GlassDesign.microGlassBackground(topAlpha = 0.25f, bottomAlpha = 0.10f))
                                        .border(0.75.dp, GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.50f, dispersionAlpha = 0.20f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .background(Color(0xFF39D353), CircleShape)
                                        )
                                        Text(
                                            text = "MODIFIED",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF39D353)
                                        )
                                    }
                                }
                            }
                        }

                        val subtitle = activeTab?.relativePath ?: state.breadcrumbs.joinToString(" / ") { it.name }
                        if (subtitle.isNotBlank() && subtitle != currentTitle) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            actions = {
                Row(
                    modifier = Modifier.padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Save Button (Active when file is open)
                    if (activeTab != null) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = if (activeTab.isDirty) 0.28f else 0.14f, bottomAlpha = 0.05f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = if (activeTab.isDirty) 0.60f else 0.30f, bottomAlpha = 0.10f), CircleShape)
                                .springPressClickable(onClick = onSave),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save file",
                                tint = if (activeTab.isDirty) Color(0xFF39D353) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Run in Terminal Button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.18f, bottomAlpha = 0.06f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.40f, bottomAlpha = 0.12f), CircleShape)
                                .springPressClickable(onClick = onRunInTerminal),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run in Terminal",
                                tint = Color(0xFF58A6FF),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Browse files drawer toggle (when code full-width)
                    if (state.openTabs.isNotEmpty() && !isSplitView) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.10f), CircleShape)
                                .springPressClickable(onClick = onToggleDrawer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Browse Files",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Open Terminal Sheet
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.18f, bottomAlpha = 0.06f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.40f, bottomAlpha = 0.12f), CircleShape)
                            .springPressClickable(onClick = onOpenTerminal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Open Terminal",
                            tint = Color(0xFF39D353),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Word wrap toggle
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = if (wrapLines) 0.28f else 0.14f, bottomAlpha = 0.05f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = if (wrapLines) 0.50f else 0.30f, bottomAlpha = 0.10f), CircleShape)
                            .springPressClickable(onClick = onToggleWrapLines),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.WrapText,
                            contentDescription = "Toggle line wrap",
                            tint = if (wrapLines) Color(0xFF58A6FF) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Split View toggle
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = if (isSplitView) 0.28f else 0.14f, bottomAlpha = 0.05f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = if (isSplitView) 0.50f else 0.30f, bottomAlpha = 0.10f), CircleShape)
                            .springPressClickable(onClick = onToggleSplitView),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerticalSplit,
                            contentDescription = "Toggle Split View",
                            tint = if (isSplitView) Color(0xFF58A6FF) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Copy content
                    if (activeTab != null) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.10f), CircleShape)
                                .springPressClickable(onClick = onCopyContent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy file content",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun EditorCanvasPane(
    state: EditorState,
    wrapLines: Boolean,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSelectTab: (EditorTab) -> Unit,
    onCloseTab: (EditorTab) -> Unit,
    onOpenDrawer: () -> Unit,
    onNewFile: () -> Unit
) {
    val activeTab = state.activeTab
    var editorInsertCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    if (activeTab == null) {
        // Empty State: No active file
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(GlassDesign.microGlassBackground(topAlpha = 0.22f, bottomAlpha = 0.08f))
                        .border(1.dp, GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.65f, dispersionAlpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(34.dp)
                    )
                }
                Text(
                    "No file open",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Select a file from the explorer to edit, or create a new file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.12f), RoundedCornerShape(12.dp))
                            .springPressClickable(onClick = onOpenDrawer)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFF0F6FC))
                            Text("Browse Files", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = Color(0xFFF0F6FC))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF1F6FEB), Color(0xFF388BFD))))
                            .border(0.75.dp, GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.70f, dispersionAlpha = 0.30f), RoundedCornerShape(12.dp))
                            .springPressClickable(onClick = onNewFile)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Text("New File", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        }
                    }
                }
            }
        }
    } else {
        // Multi-Tab bar + Interactive Code Editor + Programming Accessory Toolbar
        Column(modifier = Modifier.fillMaxSize()) {
            EditorTabBar(
                tabs = state.openTabs,
                activeTabUri = state.activeTabUri,
                onSelectTab = onSelectTab,
                onCloseTab = onCloseTab
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                InteractiveCodeEditor(
                    tab = activeTab,
                    wrapLines = wrapLines,
                    onContentChange = onContentChange,
                    onSave = onSave,
                    onCloseFile = { onCloseTab(activeTab) },
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onRegisterInsertHandle = { editorInsertCallback = it }
                )
            }

            // Bottom Programming Accessory Toolbar
            ProgrammingAccessoryBar(
                isDirty = activeTab.isDirty,
                onSave = onSave,
                onUndo = onUndo,
                onRedo = onRedo,
                canUndo = activeTab.undoStack.isNotEmpty(),
                canRedo = activeTab.redoStack.isNotEmpty(),
                onInsertSymbol = { sym -> editorInsertCallback?.invoke(sym) },
                onIndent = { editorInsertCallback?.invoke("    ") }
            )
        }
    }
}

@Composable
fun FileExplorerPane(
    state: EditorState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigateInto: (FileNode) -> Unit,
    onNavigateUp: () -> Unit,
    onBreadcrumbClick: (Breadcrumb) -> Unit,
    onFileSelect: (FileNode) -> Unit,
    onNewFileClick: (Uri?) -> Unit,
    onNewFolderClick: (Uri?) -> Unit,
    onRenameClick: (FileNode) -> Unit,
    onDeleteClick: (FileNode) -> Unit,
    onRefreshClick: () -> Unit,
    onCopyPath: (String) -> Unit,
    onCloseDrawer: (() -> Unit)?,
    showDrawerHeader: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentFolderUri = state.breadcrumbs.lastOrNull()?.uri

    Column(modifier = modifier.fillMaxSize()) {
        if (showDrawerHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = state.projectName.ifBlank { "Project Explorer" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (onCloseDrawer != null) {
                    IconButton(onClick = onCloseDrawer) {
                        Icon(Icons.Default.Close, contentDescription = "Close Drawer")
                    }
                }
            }
            HorizontalDivider()
        }

        // Explorer Action Toolbar (+ File, + Folder, Refresh)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassDesign.liquidGlassBackground(topAlpha = 0.85f, midAlpha = 0.80f, bottomAlpha = 0.75f))
                .border(width = 0.5.dp, brush = GlassDesign.liquidGlassBorder(0.40f, 0.15f), shape = androidx.compose.ui.graphics.RectangleShape)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "EXPLORER",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = Color(0xFF8B949E)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.05f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f), CircleShape)
                            .springPressClickable { onNewFileClick(currentFolderUri) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                            contentDescription = "New File",
                            modifier = Modifier.size(15.dp),
                            tint = Color(0xFF58A6FF)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.05f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f), CircleShape)
                            .springPressClickable { onNewFolderClick(currentFolderUri) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "New Folder",
                            modifier = Modifier.size(15.dp),
                            tint = Color(0xFF58A6FF)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.05f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f), CircleShape)
                            .springPressClickable { onRefreshClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(15.dp),
                            tint = Color(0xFFC9D1D9)
                        )
                    }
                }
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Filter files...", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )

        // Breadcrumbs
        if (state.breadcrumbs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(state.breadcrumbs) { crumb ->
                    val isLast = crumb == state.breadcrumbs.last()
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isLast) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { onBreadcrumbClick(crumb) }
                    ) {
                        Text(
                            text = crumb.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    if (!isLast) {
                        Text(
                            " / ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp)
        }

        if (state.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val filteredTree = remember(state.fileTree, searchQuery) {
                if (searchQuery.isBlank()) state.fileTree
                else state.fileTree.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.breadcrumbs.size > 1 && searchQuery.isBlank()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateUp() },
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Up",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                val parentName = state.breadcrumbs.getOrNull(state.breadcrumbs.size - 2)?.name ?: "parent"
                                Text(
                                    text = ".. ($parentName)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }

                if (filteredTree.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No files match \"$searchQuery\"" else "Folder is empty",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredTree, key = { it.uri.toString() }) { node ->
                        FileItemRow(
                            node = node,
                            isSelected = state.activeTabUri == node.uri,
                            onSelect = {
                                if (node.isDirectory) {
                                    onNavigateInto(node)
                                } else {
                                    onFileSelect(node)
                                }
                            },
                            onNewFileInside = { onNewFileClick(node.uri) },
                            onNewFolderInside = { onNewFolderClick(node.uri) },
                            onRename = { onRenameClick(node) },
                            onDelete = { onDeleteClick(node) },
                            onCopyPath = { onCopyPath(node.name) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    node: FileNode,
    isSelected: Boolean = false,
    onSelect: () -> Unit,
    onNewFileInside: () -> Unit = {},
    onNewFolderInside: () -> Unit = {},
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCopyPath: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val (icon, iconTint) = getFileIconAndColor(node.name, node.isDirectory)
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onSelect,
                onLongClick = { showMenu = true }
            ),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = iconTint
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val nodeSize = node.size
                if (!node.isDirectory && nodeSize != null && nodeSize > 0L) {
                    Text(
                        text = formatFileSize(nodeSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Kebab Menu for Context Actions
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options for ${node.name}",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (node.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("New File Here") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onNewFileInside()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New Folder Here") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onNewFolderInside()
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Copy Name") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onCopyPath()
                        }
                    )

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

fun buildRunCommand(fileName: String, relativePath: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val cleanPath = relativePath.trim()
    return when (ext) {
        "py" -> "python3 \"$cleanPath\""
        "js" -> "node \"$cleanPath\""
        "ts" -> "node \"$cleanPath\""
        "sh", "bash" -> "bash \"$cleanPath\""
        "kt", "kts" -> "kotlinc -script \"$cleanPath\""
        "c" -> "gcc \"$cleanPath\" -o /tmp/run_out && /tmp/run_out"
        "cpp", "cc" -> "g++ \"$cleanPath\" -o /tmp/run_out && /tmp/run_out"
        "go" -> "go run \"$cleanPath\""
        "rs" -> "cargo run"
        else -> "cat \"$cleanPath\""
    }
}

@Composable
private fun getFileIconAndColor(fileName: String, isDirectory: Boolean): Pair<ImageVector, Color> {
    if (isDirectory) {
        return Icons.Default.Folder to MaterialTheme.colorScheme.primary
    }
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts" -> Icons.Default.Code to Color(0xFF7F52FF)
        "java" -> Icons.Default.Code to Color(0xFFE76F00)
        "py" -> Icons.Default.Code to Color(0xFF3572A5)
        "js", "mjs", "cjs" -> Icons.Default.Code to Color(0xFFF1E05A)
        "ts", "tsx" -> Icons.Default.Code to Color(0xFF2B7489)
        "c", "h" -> Icons.Default.Code to Color(0xFF555555)
        "cpp", "hpp" -> Icons.Default.Code to Color(0xFFF34B7D)
        "md", "txt" -> Icons.Default.Description to MaterialTheme.colorScheme.secondary
        "json", "xml", "yaml", "yml", "gradle" -> Icons.Default.Settings to MaterialTheme.colorScheme.tertiary
        else -> Icons.Default.Description to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return ""
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024.0 && unitIndex < units.size - 1) {
        size /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) "${size.toLong()} B" else String.format(Locale.US, "%.1f %s", size, units[unitIndex])
}
