package com.codeagent.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.codeagent.core.model.ChangeType
import com.codeagent.core.model.DiffHunk
import com.codeagent.core.model.DiffLine
import com.codeagent.core.model.DiffLineType
import com.codeagent.core.model.PendingChange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffReviewScreen(
    pendingChanges: List<PendingChange>,
    onApprove: (PendingChange) -> Unit,
    onReject: (PendingChange) -> Unit,
    onApproveAll: () -> Unit,
    onRejectAll: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pending Changes (${pendingChanges.size})") },
                actions = {
                    if (pendingChanges.isNotEmpty()) {
                        TextButton(onClick = onApproveAll) {
                            Text("Apply All")
                        }
                        TextButton(onClick = onRejectAll) {
                            Text("Reject All")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (pendingChanges.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    "No pending changes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pendingChanges, key = { it.id }) { change ->
                    PendingChangeCard(
                        change = change,
                        onApprove = { onApprove(change) },
                        onReject = { onReject(change) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingChangeCard(
    change: PendingChange,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = change.filePath,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
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
                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Diff view
            if (change.changeType == ChangeType.DELETE) {
                Text(
                    "This file will be deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (change.changeType == ChangeType.CREATE) {
                Text(
                    "New file",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                val lines = (change.proposedContent ?: "").lines()
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    for ((i, line) in lines.take(50).withIndex()) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    append("${i + 1} ")
                                }
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)) {
                                    append("+ $line")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                        )
                    }
                }
            } else {
                // Show unified diff for edits
                val hunks = com.codeagent.core.files.DiffEngine.computeDiff(
                    change.originalContent ?: "",
                    change.proposedContent ?: "",
                    change.filePath
                )
                if (hunks.isEmpty()) {
                    Text("No differences detected.", style = MaterialTheme.typography.bodySmall)
                } else {
                    DiffViewer(hunks = hunks, modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onReject) {
                    Text("Reject")
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onApprove) {
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
fun DiffViewer(
    hunks: List<DiffHunk>,
    modifier: Modifier = Modifier
) {
    val additionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val deletionColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)

    Column(modifier = modifier) {
        for (hunk in hunks) {
            Text(
                "@@ -${hunk.oldStartLine},${hunk.oldLineCount} +${hunk.newStartLine},${hunk.newLineCount} @@",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
            for (line in hunk.lines) {
                val bg = when (line.type) {
                    DiffLineType.ADDITION -> additionColor
                    DiffLineType.DELETION -> deletionColor
                    DiffLineType.CONTEXT -> androidx.compose.ui.graphics.Color.Transparent
                }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .padding(horizontal = 4.dp, vertical = 0.5.dp)
                ) {
                    Text(
                        prefix,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = textColor,
                        modifier = Modifier.width(12.dp)
                    )
                    Text(
                        line.content,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = textColor
                    )
                }
            }
        }
    }
}
