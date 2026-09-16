package com.codeagent.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeagent.core.files.FileNode
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String = "",
    viewModel: EditorViewModel = hiltViewModel(),
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var wrapLines by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var userSplitPreference by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(projectId) {
        if (projectId.isNotBlank() && projectId != "none") {
            viewModel.openProjectById(projectId)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalMaxWidth = maxWidth
        val isWideScreen = totalMaxWidth >= 600.dp
        // If user explicitly toggled split view, respect preference; otherwise default to split on wide, full-screen code on phone
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
                        onCopyContent = {
                            state.fileContent?.let { content ->
                                clipboardManager.setText(AnnotatedString(content))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Copied ${state.selectedFile?.name ?: "file"} to clipboard")
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
                    // Split View: Compact sidebar + Code Viewer
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        val sidebarWidth = if (isWideScreen) 260.dp else (totalMaxWidth * 0.32f)
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
                                onCloseDrawer = null,
                                showDrawerHeader = false
                            )
                        }

                        VerticalDivider()

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            CodeViewerPane(
                                state = state,
                                wrapLines = wrapLines,
                                onOpenDrawer = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                onCloseFile = {
                                    viewModel.closeFile()
                                }
                            )
                        }
                    }
                } else {
                    // Full Screen View (Mobile First Default):
                    // If file is selected, show CodeViewer in 100% full width!
                    // If no file is selected, show FileExplorer in 100% full width!
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        if (state.selectedFile != null) {
                            CodeViewerPane(
                                state = state,
                                wrapLines = wrapLines,
                                onOpenDrawer = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                onCloseFile = {
                                    viewModel.closeFile()
                                }
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
                                onCloseDrawer = null,
                                showDrawerHeader = false
                            )
                        }
                    }
                }
            }
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
    onCopyContent: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        title = {
            Column {
                Text(
                    text = state.selectedFile?.name ?: state.projectName.ifBlank { "Files" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = if (state.selectedFile != null) {
                    state.breadcrumbs.joinToString(" / ") { it.name }
                } else {
                    state.breadcrumbs.lastOrNull()?.name ?: ""
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            // If viewing code in full-width mode, provide quick button to browse/switch files
            if (state.selectedFile != null && !isSplitView) {
                IconButton(onClick = onToggleDrawer) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Browse files"
                    )
                }
            }
            if (state.selectedFile != null) {
                // Word wrap toggle
                IconButton(onClick = onToggleWrapLines) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.WrapText,
                        contentDescription = if (wrapLines) "Disable wrap" else "Enable wrap",
                        tint = if (wrapLines) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Copy file content
                IconButton(onClick = onCopyContent) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code"
                    )
                }
            }
            // Toggle split view
            IconButton(onClick = onToggleSplitView) {
                Icon(
                    imageVector = if (isSplitView) Icons.Default.Fullscreen else Icons.Default.VerticalSplit,
                    contentDescription = if (isSplitView) "Full screen code" else "Split view",
                    tint = if (isSplitView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun FileExplorerPane(
    state: EditorState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigateInto: (FileNode) -> Unit,
    onNavigateUp: () -> Unit,
    onBreadcrumbClick: (Breadcrumb) -> Unit,
    onFileSelect: (FileNode) -> Unit,
    onCloseDrawer: (() -> Unit)?,
    showDrawerHeader: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (showDrawerHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = state.projectName.ifBlank { "Project Files" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (onCloseDrawer != null) {
                    IconButton(onClick = onCloseDrawer, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close explorer")
                    }
                }
            }
            HorizontalDivider()
        }

        // Quick filter search box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Filter files...", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = RoundedCornerShape(12.dp)
        )

        // Breadcrumbs row
        if (state.breadcrumbs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(state.breadcrumbs) { crumb ->
                    val isLast = crumb == state.breadcrumbs.last()
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isLast) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { onBreadcrumbClick(crumb) }
                    ) {
                        Text(
                            text = crumb.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
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
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Up",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(10.dp))
                                val parentName = state.breadcrumbs.getOrNull(state.breadcrumbs.size - 2)?.name ?: "parent"
                                Text(
                                    text = ".. (Up to $parentName)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        HorizontalDivider()
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
                            isSelected = state.selectedFile?.uri == node.uri,
                            onSelect = {
                                if (node.isDirectory) {
                                    onNavigateInto(node)
                                } else {
                                    onFileSelect(node)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileItemRow(
    node: FileNode,
    isSelected: Boolean = false,
    onSelect: () -> Unit
) {
    val (icon, iconTint) = getFileIconAndColor(node.name, node.isDirectory)
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
            if (node.isDirectory) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun CodeViewerPane(
    state: EditorState,
    wrapLines: Boolean,
    onOpenDrawer: () -> Unit,
    onCloseFile: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        tonalElevation = 0.dp
    ) {
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Text(
                            "Loading file...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            state.fileContent != null && state.selectedFile != null -> {
                CodeViewer(
                    fileName = state.selectedFile.name,
                    fileSize = state.selectedFile.size,
                    content = state.fileContent,
                    wrapLines = wrapLines,
                    onCloseFile = onCloseFile
                )
            }
            state.error != null && state.selectedFile != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = state.error ?: "Error reading file",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(onClick = onOpenDrawer) {
                            Text("Choose Another File")
                        }
                    }
                }
            }
            else -> {
                // Empty state when no file is selected
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            "No file selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Select a file from the explorer to view its contents with syntax highlighting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        FilledTonalButton(
                            onClick = onOpenDrawer,
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Browse Files")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CodeViewer(
    fileName: String,
    fileSize: Long? = null,
    content: String,
    wrapLines: Boolean = false,
    onCloseFile: (() -> Unit)? = null
) {
    val lines = remember(content) { content.lines() }
    val lineCount = lines.size
    val gutterDigits = maxOf(2, lineCount.toString().length)
    val gutterWidth = (gutterDigits * 10 + 16).dp
    val language = remember(fileName) { getFileLanguage(fileName) }
    val formattedSize = remember(fileSize) { formatFileSize(fileSize) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Code Metadata Header bar
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Language badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = language,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Line count badge
                    Text(
                        text = "$lineCount lines",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // File size badge
                    if (formattedSize.isNotBlank()) {
                        Text(
                            text = "· $formattedSize",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (onCloseFile != null) {
                    IconButton(onClick = onCloseFile, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close file",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        HorizontalDivider()

        val horizontalScrollState = rememberScrollState()

        val scrollModifier = if (wrapLines) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScrollState)
        }

        LazyColumn(
            modifier = scrollModifier
        ) {
            items(lines.size) { index ->
                val lineText = lines[index]
                val annotatedLine = remember(lineText) { highlightSyntax(lineText) }

                Row(
                    modifier = Modifier.padding(vertical = 1.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Line number gutter
                    Text(
                        text = "${index + 1}",
                        modifier = Modifier
                            .width(gutterWidth)
                            .padding(end = 8.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.outline
                    )

                    // Gutter separator line
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    )

                    Spacer(Modifier.width(8.dp))

                    // Code text
                    Text(
                        text = annotatedLine,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        ),
                        modifier = if (wrapLines) Modifier.fillMaxWidth().padding(end = 8.dp) else Modifier
                    )
                }
            }
        }
    }
}

private fun highlightSyntax(line: String): AnnotatedString {
    val keywordRegex = Regex("\\b(val|var|fun|class|object|interface|package|import|return|if|else|when|for|while|try|catch|finally|throw|new|public|private|protected|internal|override|suspend|data|sealed|inline|const|null|true|false|this|super|def|let|function|async|await|struct|typedef|enum|int|float|double|char|void|bool|unsigned|long|static|extern|auto|sizeof|nullptr)\\b")
    val directiveRegex = Regex("(#include|#define|#ifdef|#ifndef|#endif|#pragma|@[A-Za-z0-9_]+)")
    val stringRegex = Regex("\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'")
    val commentRegex = Regex("//.*|/\\*.*?\\*/|#.*")
    val numberRegex = Regex("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?([fFlL])?)\\b")

    val keywordColor = Color(0xFFCE93D8)
    val directiveColor = Color(0xFFFFB74D)
    val stringColor = Color(0xFFA5D6A7)
    val commentColor = Color(0xFF90A4AE)
    val numberColor = Color(0xFF81D4FA)

    return buildAnnotatedString {
        append(line)
        for (m in keywordRegex.findAll(line)) {
            addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
        }
        for (m in directiveRegex.findAll(line)) {
            addStyle(SpanStyle(color = directiveColor, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
        }
        for (m in numberRegex.findAll(line)) {
            addStyle(SpanStyle(color = numberColor), m.range.first, m.range.last + 1)
        }
        for (m in stringRegex.findAll(line)) {
            addStyle(SpanStyle(color = stringColor), m.range.first, m.range.last + 1)
        }
        for (m in commentRegex.findAll(line)) {
            addStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
        }
    }
}

@Composable
private fun getFileIconAndColor(fileName: String, isDirectory: Boolean): Pair<ImageVector, Color> {
    if (isDirectory) {
        return Icons.Default.Folder to MaterialTheme.colorScheme.primary
    }
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts" -> Icons.Default.Code to MaterialTheme.colorScheme.tertiary
        "java", "c", "cpp", "h", "hpp", "py", "js", "ts" -> Icons.Default.Code to MaterialTheme.colorScheme.primary
        "md", "txt" -> Icons.Default.Description to MaterialTheme.colorScheme.secondary
        "json", "xml", "yaml", "yml", "gradle" -> Icons.Default.Settings to MaterialTheme.colorScheme.tertiary
        else -> Icons.Default.Description to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun getFileLanguage(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts" -> "Kotlin"
        "java" -> "Java"
        "c", "h" -> "C"
        "cpp", "hpp", "cc", "cxx" -> "C++"
        "py" -> "Python"
        "js", "mjs", "cjs" -> "JavaScript"
        "ts", "tsx" -> "TypeScript"
        "json" -> "JSON"
        "xml", "html", "svg" -> "XML/HTML"
        "md", "markdown" -> "Markdown"
        "sh", "bash" -> "Shell"
        "sql" -> "SQL"
        "yml", "yaml" -> "YAML"
        "gradle" -> "Gradle"
        "txt" -> "Text"
        else -> ext.uppercase().ifBlank { "Plain Text" }
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
