package com.codeagent.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeagent.core.files.FileNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String = "",
    viewModel: EditorViewModel = hiltViewModel(),
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(projectId) {
        if (projectId.isNotBlank() && projectId != "none") {
            viewModel.openProjectById(projectId)
        }
    }

    Scaffold(
        topBar = {
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
                title = { Text(state.projectName.ifBlank { "Files" }) }
            )
        }
    ) { padding ->
        if (state.projectUri == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Open a project to browse files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                // File tree pane
                Surface(
                    modifier = Modifier.fillMaxWidth(0.38f).fillMaxHeight(),
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Breadcrumbs bar
                        if (state.breadcrumbs.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(state.breadcrumbs) { crumb ->
                                    val isLast = crumb == state.breadcrumbs.last()
                                    Text(
                                        text = crumb.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.clickable { viewModel.navigateToBreadcrumb(crumb) }
                                    )
                                    if (!isLast) {
                                        Text(" / ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                            HorizontalDivider()
                        }

                        if (state.error != null) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    state.error ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                if (state.breadcrumbs.size > 1) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.navigateUp() }
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Up",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("..", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }

                                items(state.fileTree, key = { it.uri.toString() }) { node ->
                                    FileItemRow(
                                        node = node,
                                        isSelected = state.selectedFile?.uri == node.uri,
                                        onSelect = {
                                            if (node.isDirectory) {
                                                viewModel.navigateInto(node)
                                            } else {
                                                viewModel.selectFile(node)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                VerticalDivider()

                // Code viewer pane
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
                                CircularProgressIndicator()
                            }
                        }
                        state.fileContent != null && state.selectedFile != null -> {
                            CodeViewer(
                                fileName = state.selectedFile!!.name,
                                content = state.fileContent!!
                            )
                        }
                        state.error != null && state.selectedFile != null -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    state.error ?: "Error reading file",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Select a file to view",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (node.isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (node.isDirectory)
                MaterialTheme.colorScheme.primary
            else if (isSelected)
                MaterialTheme.colorScheme.tertiary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            node.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        if (node.isDirectory) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun CodeViewer(fileName: String, content: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 1.dp) {
            Text(
                fileName,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall
            )
        }
        HorizontalDivider()
        val lines = content.lines()
        val horizontalScrollState = rememberScrollState()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScrollState)
        ) {
            items(lines.size) { index ->
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        "${index + 1}",
                        modifier = Modifier.width(36.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        highlightSyntax(lines[index]),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }
    }
}

private fun highlightSyntax(line: String): AnnotatedString {
    val keywordRegex = Regex("\\b(val|var|fun|class|object|interface|package|import|return|if|else|when|for|while|try|catch|finally|throw|new|public|private|protected|internal|override|suspend|data|sealed|inline|const|null|true|false|this|super|def|let|function|async|await)\\b")
    val stringRegex = Regex("\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'")
    val commentRegex = Regex("//.*|#.*")
    val numberRegex = Regex("\\b\\d+(\\.\\d+)?([fFlL])?\\b")

    val keywordColor = Color(0xFFCE93D8)
    val stringColor = Color(0xFFA5D6A7)
    val commentColor = Color(0xFF90A4AE)
    val numberColor = Color(0xFF81D4FA)

    return buildAnnotatedString {
        append(line)
        for (m in keywordRegex.findAll(line)) {
            addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
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
