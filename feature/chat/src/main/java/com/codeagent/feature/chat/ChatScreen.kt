package com.codeagent.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeagent.core.files.DiffEngine
import com.codeagent.core.model.ChangeType
import com.codeagent.core.model.DiffLineType
import com.codeagent.core.model.Message
import com.codeagent.core.model.MessageRole
import com.codeagent.core.model.PendingChange
import com.codeagent.core.model.ToolCallData
import com.codeagent.core.ui.components.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    projectId: String = "",
    viewModel: ChatViewModel = hiltViewModel(),
    onReviewDiffs: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    val scaffoldState = rememberTopAppBarState()

    LaunchedEffect(projectId) {
        if (projectId.isNotBlank() && projectId != "none" && state.projectId != projectId) {
            viewModel.openProjectById(projectId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Chat${if (state.projectName.isNotBlank()) " · ${state.projectName}" else ""}")
                },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(scaffoldState)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message the agent…") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp)
                    )
                    if (state.isStreaming) {
                        FilledIconButton(onClick = { viewModel.cancelGeneration() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop")
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank() && state.projectUri != null
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Pending changes banner
            if (state.pendingChanges.isNotEmpty()) {
                PendingChangesBanner(
                    changes = state.pendingChanges,
                    onApproveAll = { viewModel.approveAllChanges() },
                    onRejectAll = { viewModel.rejectAllChanges() },
                    onApprove = { viewModel.approveChange(it) },
                    onReject = { viewModel.rejectChange(it) },
                    onReviewDiffs = onReviewDiffs
                )
            }

            // Error banner
            if (state.error != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") } }
                ) {
                    Text(state.error ?: "")
                }
            }

            // Messages area
            if (state.messages.isEmpty() && !state.isStreaming) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (state.projectUri == null) "Open a project to start chatting"
                        else "Start a conversation",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(message)
                    }
                    if (state.isStreaming) {
                        item {
                            if (state.streamingText.isNotBlank()) {
                                MessageBubble(
                                    Message(
                                        id = "streaming_response",
                                        sessionId = state.sessionId ?: "",
                                        role = MessageRole.ASSISTANT,
                                        content = state.streamingText,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            } else {
                                Text(
                                    "Thinking…",
                                    style = MaterialTheme.typography.bodySmall,
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
private fun PendingChangesBanner(
    changes: List<PendingChange>,
    onApproveAll: () -> Unit,
    onRejectAll: () -> Unit,
    onApprove: (PendingChange) -> Unit,
    onReject: (PendingChange) -> Unit,
    onReviewDiffs: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${changes.size} pending change${if (changes.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onReviewDiffs != null) {
                        TextButton(onClick = onReviewDiffs) { Text("Review") }
                    }
                    TextButton(onClick = onApproveAll) { Text("Apply All") }
                    TextButton(onClick = onRejectAll) { Text("Reject All") }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                for (change in changes) {
                    PendingChangeRow(change, onApprove, onReject)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PendingChangeRow(
    change: PendingChange,
    onApprove: (PendingChange) -> Unit,
    onReject: (PendingChange) -> Unit
) {
    var showDiff by remember { mutableStateOf(false) }

    val label = when (change.changeType) {
        ChangeType.EDIT -> "EDIT"
        ChangeType.CREATE -> "CREATE"
        ChangeType.DELETE -> "DELETE"
    }
    val color = when (change.changeType) {
        ChangeType.EDIT -> MaterialTheme.colorScheme.tertiary
        ChangeType.CREATE -> MaterialTheme.colorScheme.primary
        ChangeType.DELETE -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showDiff = !showDiff },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    change.filePath,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(
                    onClick = { onApprove(change) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Apply", modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { onReject(change) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Reject", modifier = Modifier.size(16.dp))
                }
            }

            if (showDiff) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))

                when (change.changeType) {
                    ChangeType.DELETE -> {
                        Text(
                            "This file will be deleted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    ChangeType.CREATE -> {
                        val lines = (change.proposedContent ?: "").lines().take(30)
                        Column {
                            for ((i, line) in lines.withIndex()) {
                                Text(
                                    "${i + 1} + $line",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    ChangeType.EDIT -> {
                        val hunks = DiffEngine.computeDiff(
                            change.originalContent ?: "",
                            change.proposedContent ?: "",
                            change.filePath
                        )
                        Column {
                            for (hunk in hunks.take(3)) {
                                Text(
                                    "@@ -${hunk.oldStartLine},${hunk.oldLineCount} +${hunk.newStartLine},${hunk.newLineCount} @@",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                for (line in hunk.lines.take(20)) {
                                    val prefix = when (line.type) {
                                        DiffLineType.ADDITION -> "+"
                                        DiffLineType.DELETION -> "-"
                                        DiffLineType.CONTEXT -> " "
                                    }
                                    val textColor = when (line.type) {
                                        DiffLineType.ADDITION -> MaterialTheme.colorScheme.primary
                                        DiffLineType.DELETION -> MaterialTheme.colorScheme.error
                                        DiffLineType.CONTEXT -> MaterialTheme.colorScheme.onSurface
                                    }
                                    Text(
                                        "$prefix ${line.content}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val isUser = message.role == MessageRole.USER
    val isTool = message.role == MessageRole.TOOL
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val color = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        isTool -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = color,
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(if (isUser) 0.85f else 0.95f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isTool) {
                    Text(
                        "🔧 Tool result",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    MarkdownText(content = message.content)
                }
            }
        }
        if (message.toolCalls.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            for (tc in message.toolCalls) {
                ToolCallChip(toolCall = tc)
            }
        }
    }
}

@Composable
private fun ToolCallChip(toolCall: ToolCallData) {
    AssistChip(
        onClick = { },
        label = { Text("🔧 ${toolCall.name}", maxLines = 1) },
        modifier = Modifier.widthIn(max = 280.dp)
    )
}
