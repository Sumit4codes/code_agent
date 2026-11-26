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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.koda.dev.core.ui.glass.BackdropState
import com.koda.dev.core.ui.glass.GlassDesign
import com.koda.dev.core.ui.glass.captureBackdrop
import com.koda.dev.core.ui.glass.liquidGlassBackdrop
import com.koda.dev.core.ui.glass.rememberBackdropState
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
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }
    val backdropState = rememberBackdropState()
    val density = LocalDensity.current

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

    val handleBackNavigation = {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else if (state.activeTab?.isDirty == true) {
            viewModel.closeActiveTab(force = false)
        } else if (onNavigateBack != null) {
            onNavigateBack()
        }
    }

    // Back handling: close drawer -> prompt on dirty tab -> exit to Projects
    BackHandler(onBack = { handleBackNavigation() })

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isWideScreen = screenWidth >= 600.dp
    val isSplitView = userSplitPreference ?: isWideScreen

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !isSplitView,
            drawerContent = {
                val drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                ModalDrawerSheet(
                    modifier = Modifier
                        .widthIn(max = minOf(320.dp, screenWidth * 0.85f))
                        .fillMaxHeight()
                        .shadow(elevation = 20.dp, shape = drawerShape)
                        .clip(drawerShape)
                        .background(GlassDesign.liquidGlassBackground(topAlpha = 0.96f, midAlpha = 0.94f, bottomAlpha = 0.90f))
                        .border(1.dp, GlassDesign.liquidGlassBorder(0.65f, 0.25f), drawerShape),
                    drawerContainerColor = Color.Transparent,
                    drawerShape = drawerShape
                ) {
                    val shouldComposeExplorer = !isSplitView && (
                        state.openTabs.isNotEmpty() ||
                        drawerState.isOpen ||
                        drawerState.isAnimationRunning ||
                        drawerState.targetValue == DrawerValue.Open
                    )
                    if (shouldComposeExplorer) {
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
                            showDrawerHeader = true,
                            modifier = Modifier.statusBarsPadding().navigationBarsPadding()
                        )
                    }
                }
            }
        ) {
            Scaffold(
                containerColor = Color(0xFF0B0E14),
                contentWindowInsets = WindowInsets.navigationBars,
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                val defaultTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 74.dp
                val topClearance = if (topBarHeightPx > 0f) {
                    with(density) { topBarHeightPx.toDp() }
                } else {
                    defaultTopInset
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = padding.calculateBottomPadding())
                            .consumeWindowInsets(PaddingValues(bottom = padding.calculateBottomPadding()))
                            .imePadding()
                            .captureBackdrop(backdropState)
                    ) {
                        if (state.projectUri == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = topClearance),
                                contentAlignment = Alignment.Center
                            ) {
                                if (projectId.isNotBlank() && projectId != "none") {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.5.dp,
                                        color = Color(0xFF58A6FF)
                                    )
                                } else {
                                    Text(
                                        "Open a project to browse files",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF8B949E)
                                    )
                                }
                            }
                        } else if (isSplitView) {
                            // Split View: Explorer Sidebar + Code Editor
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = topClearance)
                            ) {
                                val sidebarWidth = if (isWideScreen) 270.dp else (screenWidth * 0.42f).coerceIn(180.dp, 300.dp)
                                Box(
                                    modifier = Modifier
                                        .width(sidebarWidth)
                                        .fillMaxHeight()
                                        .background(Color(0xFF0D1117))
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
                                        isSplitView = true,
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
                                    .padding(top = topClearance)
                            ) {
                                if (state.openTabs.isNotEmpty()) {
                                    EditorCanvasPane(
                                        state = state,
                                        wrapLines = wrapLines,
                                        isSplitView = false,
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

                    // Floating Liquid Glass Top App Bar
                    EditorTopAppBar(
                        state = state,
                        isSplitView = isSplitView,
                        wrapLines = wrapLines,
                        backdropState = backdropState,
                        onNavigateBack = if (onNavigateBack != null) ({ handleBackNavigation() }) else null,
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
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .onGloballyPositioned { coords ->
                                topBarHeightPx = coords.size.height.toFloat()
                            }
                    )
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
    backdropState: BackdropState?,
    onNavigateBack: (() -> Unit)?,
    onToggleDrawer: () -> Unit,
    onToggleSplitView: () -> Unit,
    onToggleWrapLines: () -> Unit,
    onSave: () -> Unit,
    onRunInTerminal: () -> Unit,
    onOpenTerminal: () -> Unit,
    onCopyContent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTab = state.activeTab
    val barShape = RoundedCornerShape(22.dp)
    var moreMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(
                elevation = 16.dp,
                shape = barShape,
                ambientColor = Color(0x33000000),
                spotColor = Color(0x660F172A)
            )
            .liquidGlassBackdrop(
                backdropState = backdropState,
                shape = barShape,
                blurRadius = 24.dp,
                tintColor = Color(0xC410141E),
                specularBorder = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.65f, dispersionAlpha = 0.28f),
                borderWidth = 1.dp
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
                            .padding(start = 6.dp)
                            .size(40.dp)
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
                    modifier = Modifier.fillMaxWidth(),
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

                    Column(
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                fontFamily = if (activeTab != null) FontFamily.Monospace else FontFamily.Default,
                                color = Color(0xFFF0F6FC),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (activeTab?.isDirty == true) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF39D353))
                                )
                            }
                        }

                        val subtitle = activeTab?.relativePath ?: state.breadcrumbs.joinToString(" / ") { it.name }
                        if (subtitle.isNotBlank() && subtitle != currentTitle) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = Color(0xFF8B949E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            actions = {
                Row(
                    modifier = Modifier.padding(end = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Run in Terminal Button (Save lives prominently in ProgrammingAccessoryBar + More menu)
                    if (activeTab != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
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
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // More Options Dropdown (...)
                    Box {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = if (moreMenuExpanded) 0.28f else 0.14f, bottomAlpha = 0.05f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = if (moreMenuExpanded) 0.50f else 0.30f, bottomAlpha = 0.10f), CircleShape)
                                .springPressClickable { moreMenuExpanded = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = if (moreMenuExpanded) Color(0xFF58A6FF) else Color(0xFFC9D1D9),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = moreMenuExpanded,
                            onDismissRequest = { moreMenuExpanded = false },
                            containerColor = Color(0xFF161B22),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                brush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.50f, dispersionAlpha = 0.20f)
                            )
                        ) {
                            if (activeTab != null) {
                                DropdownMenuItem(
                                    text = { Text("Save File", color = Color(0xFFF0F6FC)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Save,
                                            contentDescription = null,
                                            tint = if (activeTab.isDirty) Color(0xFF39D353) else Color(0xFF8B949E),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        moreMenuExpanded = false
                                        onSave()
                                    }
                                )
                            }

                            // File Explorer Drawer toggle
                            DropdownMenuItem(
                                text = { Text("File Explorer", color = Color(0xFFF0F6FC)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = Color(0xFF58A6FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    moreMenuExpanded = false
                                    onToggleDrawer()
                                }
                            )

                            // Open Workspace Terminal Sheet
                            DropdownMenuItem(
                                text = { Text("Open Terminal", color = Color(0xFFF0F6FC)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Terminal,
                                        contentDescription = null,
                                        tint = Color(0xFF39D353),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    moreMenuExpanded = false
                                    onOpenTerminal()
                                }
                            )

                            HorizontalDivider(
                                color = Color(0xFF30363D),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            // Word Wrap toggle
                            DropdownMenuItem(
                                text = { Text("Word Wrap", color = Color(0xFFF0F6FC)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.WrapText,
                                        contentDescription = null,
                                        tint = if (wrapLines) Color(0xFF58A6FF) else Color(0xFF8B949E),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = if (wrapLines) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = Color(0xFF58A6FF),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    moreMenuExpanded = false
                                    onToggleWrapLines()
                                }
                            )

                            // Split View toggle
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isSplitView) "Single Pane View" else "Split Explorer View",
                                        color = Color(0xFFF0F6FC)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.VerticalSplit,
                                        contentDescription = null,
                                        tint = if (isSplitView) Color(0xFF58A6FF) else Color(0xFF8B949E),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = if (isSplitView) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = Color(0xFF58A6FF),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    moreMenuExpanded = false
                                    onToggleSplitView()
                                }
                            )

                            // Copy file content (if file open)
                            if (activeTab != null) {
                                HorizontalDivider(
                                    color = Color(0xFF30363D),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy File Content", color = Color(0xFFF0F6FC)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            tint = Color(0xFF8B949E),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        moreMenuExpanded = false
                                        onCopyContent()
                                    }
                                )
                            }
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
    isSplitView: Boolean = false,
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
                .padding(20.dp),
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
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF0F6FC)
                )
                Text(
                    if (isSplitView) "Select a file from the explorer, or create a new file."
                    else "Select a file from the explorer to edit, or create a new file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8B949E),
                    textAlign = TextAlign.Center
                )
                BoxWithConstraints {
                    val isNarrow = maxWidth < 270.dp
                    if (isSplitView) {
                        // In split view, the File Explorer is already pinned on screen; show prominent primary New File button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFF1F6FEB), Color(0xFF388BFD))))
                                .border(0.75.dp, GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.70f, dispersionAlpha = 0.30f), RoundedCornerShape(12.dp))
                                .springPressClickable(onClick = onNewFile)
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Text(
                                    "New File",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    } else if (isNarrow) {
                        // Narrow width in single-pane mode: stack buttons vertically
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF1F6FEB), Color(0xFF388BFD))))
                                    .border(0.75.dp, GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.70f, dispersionAlpha = 0.30f), RoundedCornerShape(12.dp))
                                    .springPressClickable(onClick = onNewFile)
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                    Text(
                                        "New File",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                                    .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.12f), RoundedCornerShape(12.dp))
                                    .springPressClickable(onClick = onOpenDrawer)
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFF0F6FC))
                                    Text(
                                        "Browse Files",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                        color = Color(0xFFF0F6FC),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    } else {
                        // Wide width: horizontal Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                    Text(
                                        "Browse Files",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                        color = Color(0xFFF0F6FC),
                                        maxLines = 1,
                                        softWrap = false
                                    )
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
                                    Text(
                                        "New File",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {
        // Unified Explorer Header Row (merges drawer title/close with +File, +Folder, Refresh)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF10141E))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (showDrawerHeader) state.projectName.ifBlank { "EXPLORER" } else "EXPLORER",
                    style = if (showDrawerHeader) {
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    },
                    color = if (showDrawerHeader) Color(0xFFF0F6FC) else Color(0xFF8B949E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExplorerHeaderActionButton(
                        icon = Icons.AutoMirrored.Filled.NoteAdd,
                        contentDescription = "New File",
                        tint = Color(0xFF58A6FF),
                        onClick = { onNewFileClick(currentFolderUri) }
                    )
                    ExplorerHeaderActionButton(
                        icon = Icons.Default.CreateNewFolder,
                        contentDescription = "New Folder",
                        tint = Color(0xFF58A6FF),
                        onClick = { onNewFolderClick(currentFolderUri) }
                    )
                    ExplorerHeaderActionButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFFC9D1D9),
                        onClick = onRefreshClick
                    )
                    if (showDrawerHeader && onCloseDrawer != null) {
                        ExplorerHeaderActionButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Close Drawer",
                            tint = Color(0xFF8B949E),
                            onClick = onCloseDrawer
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF21262D))

        // Compact Obsidian Glass Search Capsule
        val searchShape = RoundedCornerShape(12.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .heightIn(min = 38.dp)
                .clip(searchShape)
                .background(Color(0xFF161B22))
                .border(0.75.dp, Color(0xFF30363D), searchShape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF8B949E)
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Filter files...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6E7681)
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFF0F6FC)),
                    cursorBrush = SolidColor(Color(0xFF58A6FF)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (searchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onSearchQueryChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF8B949E)
                    )
                }
            }
        }

        // Breadcrumbs
        if (state.breadcrumbs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(state.breadcrumbs) { crumb ->
                    val isLast = crumb == state.breadcrumbs.last()
                    val crumbShape = RoundedCornerShape(8.dp)
                    Box(
                        modifier = Modifier
                            .clip(crumbShape)
                            .background(
                                if (isLast) Color(0xFF1F6FEB).copy(alpha = 0.25f)
                                else Color(0xFF161B22)
                            )
                            .border(
                                width = 0.5.dp,
                                color = if (isLast) Color(0xFF58A6FF).copy(alpha = 0.45f) else Color(0xFF30363D),
                                shape = crumbShape
                            )
                            .clickable { onBreadcrumbClick(crumb) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = crumb.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (isLast) Color(0xFF79C0FF) else Color(0xFF8B949E)
                        )
                    }
                    if (!isLast) {
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF484F58),
                            modifier = Modifier.padding(horizontal = 5.dp)
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF21262D))
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
                    color = Color(0xFFF85149),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val filteredTree = remember(state.fileTree, searchQuery) {
                if (searchQuery.isBlank()) state.fileTree
                else state.fileTree.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                if (state.breadcrumbs.size > 1 && searchQuery.isBlank()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateUp() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Up",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF58A6FF)
                            )
                            Spacer(Modifier.width(8.dp))
                            val parentName = state.breadcrumbs.getOrNull(state.breadcrumbs.size - 2)?.name ?: "parent"
                            Text(
                                text = ".. ($parentName)",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color(0xFF58A6FF)
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF21262D))
                    }
                }

                if (state.isLoading && filteredTree.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF58A6FF)
                            )
                        }
                    }
                } else if (filteredTree.isEmpty()) {
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
                                color = Color(0xFF8B949E)
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

@Composable
private fun ExplorerHeaderActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.05f))
            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f), CircleShape)
            .springPressClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = tint
        )
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
        Color(0xFF1F6FEB).copy(alpha = 0.16f)
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = onSelect,
                onLongClick = { showMenu = true }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = iconTint
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isSelected) Color(0xFF79C0FF) else Color(0xFFF0F6FC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val nodeSize = node.size
                if (!node.isDirectory && nodeSize != null && nodeSize > 0L) {
                    Text(
                        text = formatFileSize(nodeSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B949E)
                    )
                }
            }

            // Kebab Menu for Context Actions
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options for ${node.name}",
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF8B949E)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = Color(0xFF161B22),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        brush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.45f, dispersionAlpha = 0.20f)
                    )
                ) {
                    if (node.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("New File Here", color = Color(0xFFF0F6FC)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, tint = Color(0xFF58A6FF)) },
                            onClick = {
                                showMenu = false
                                onNewFileInside()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New Folder Here", color = Color(0xFFF0F6FC)) },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = Color(0xFF58A6FF)) },
                            onClick = {
                                showMenu = false
                                onNewFolderInside()
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Rename", color = Color(0xFFF0F6FC)) },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = Color(0xFFC9D1D9)) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Copy Name", color = Color(0xFFF0F6FC)) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFFC9D1D9)) },
                        onClick = {
                            showMenu = false
                            onCopyPath()
                        }
                    )

                    HorizontalDivider(color = Color(0xFF21262D))

                    DropdownMenuItem(
                        text = { Text("Delete", color = Color(0xFFF85149)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF85149)) },
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

private fun getFileIconAndColor(fileName: String, isDirectory: Boolean): Pair<ImageVector, Color> {
    if (isDirectory) {
        return Icons.Default.Folder to Color(0xFF58A6FF)
    }
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts" -> Icons.Default.Code to Color(0xFF7F52FF)
        "java" -> Icons.Default.Code to Color(0xFFE76F00)
        "py" -> Icons.Default.Code to Color(0xFF3572A5)
        "js", "mjs", "cjs" -> Icons.Default.Code to Color(0xFFF1E05A)
        "ts", "tsx" -> Icons.Default.Code to Color(0xFF2B7489)
        "c", "h" -> Icons.Default.Code to Color(0xFF8B949E)
        "cpp", "hpp" -> Icons.Default.Code to Color(0xFFF34B7D)
        "md", "txt" -> Icons.Default.Description to Color(0xFFA371F7)
        "json", "xml", "yaml", "yml", "gradle" -> Icons.Default.Settings to Color(0xFF79C0FF)
        else -> Icons.Default.Description to Color(0xFF8B949E)
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
