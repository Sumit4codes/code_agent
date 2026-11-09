package com.koda.dev.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koda.dev.core.model.ChangeStatus
import com.koda.dev.core.model.ChangeType
import com.koda.dev.core.model.PendingChange
import com.koda.dev.core.model.ToolCallData
import com.koda.dev.core.model.ToolNames
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ToolCallCard(
    toolCall: ToolCallData,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    pendingChanges: List<PendingChange> = emptyList(),
    onApproveChange: ((PendingChange) -> Unit)? = null,
    onRejectChange: ((PendingChange) -> Unit)? = null,
    onOpenFullReview: ((PendingChange?) -> Unit)? = null
) {
    val normName = remember(toolCall.name) { ToolNames.normalize(toolCall.name) }
    val isFileTool = remember(normName) {
        normName == ToolNames.PROPOSE_FILE_EDIT ||
            normName == ToolNames.CREATE_FILE ||
            normName == ToolNames.DELETE_FILE ||
            normName == "edit_file" ||
            normName == "write_file"
    }

    if (isFileTool) {
        val (filePath, extractedOldContent, extractedNewContent) = remember(toolCall.arguments) {
            try {
                val json = Json.parseToJsonElement(toolCall.arguments).jsonObject
                val path = json["path"]?.jsonPrimitive?.content
                    ?: json["Path"]?.jsonPrimitive?.content
                    ?: json["file_path"]?.jsonPrimitive?.content
                    ?: ""
                val old = json["old_content"]?.jsonPrimitive?.content
                    ?: json["target"]?.jsonPrimitive?.content
                    ?: json["Target"]?.jsonPrimitive?.content
                val new = json["content"]?.jsonPrimitive?.content
                    ?: json["new_content"]?.jsonPrimitive?.content
                    ?: json["replacement"]?.jsonPrimitive?.content
                    ?: json["Replacement"]?.jsonPrimitive?.content
                Triple(path, old, new)
            } catch (_: Exception) {
                Triple("", null, null)
            }
        }

        // Determine immutable historical change type from the tool call itself
        val historicalChangeType = remember(normName, extractedOldContent, toolCall.arguments) {
            when {
                normName == ToolNames.CREATE_FILE -> ChangeType.CREATE
                normName == ToolNames.DELETE_FILE -> ChangeType.DELETE
                normName == ToolNames.PROPOSE_FILE_EDIT || normName == "edit_file" -> ChangeType.EDIT
                normName == "write_file" -> {
                    try {
                        val json = Json.parseToJsonElement(toolCall.arguments).jsonObject
                        if (json.containsKey("old_content") || json.containsKey("target") || json.containsKey("Target")) {
                            ChangeType.EDIT
                        } else {
                            ChangeType.CREATE
                        }
                    } catch (_: Exception) {
                        ChangeType.CREATE
                    }
                }
                else -> ChangeType.EDIT
            }
        }

        // Only match a pending change if it corresponds to the SAME file AND the SAME change type!
        // This prevents an active edit change from converting a historical create_file card into an edit card.
        val matchingPendingChange = remember(pendingChanges, filePath, historicalChangeType) {
            pendingChanges.find { change ->
                val pathMatches = change.filePath == filePath ||
                    change.filePath.endsWith(filePath) ||
                    (filePath.isNotBlank() && filePath.endsWith(change.filePath))
                pathMatches && change.changeType == historicalChangeType
            }
        }

        val originalContent = remember(historicalChangeType, extractedOldContent, matchingPendingChange) {
            when (historicalChangeType) {
                ChangeType.CREATE -> null
                ChangeType.DELETE -> matchingPendingChange?.originalContent ?: extractedOldContent
                ChangeType.EDIT -> matchingPendingChange?.originalContent ?: extractedOldContent
            }
        }

        val proposedContent = remember(historicalChangeType, extractedNewContent, matchingPendingChange) {
            when (historicalChangeType) {
                ChangeType.CREATE -> extractedNewContent ?: matchingPendingChange?.proposedContent
                ChangeType.DELETE -> null
                ChangeType.EDIT -> matchingPendingChange?.proposedContent ?: extractedNewContent
            }
        }

        val statusOverride = remember(matchingPendingChange, toolCall.result, toolCall.success) {
            when {
                matchingPendingChange != null -> matchingPendingChange.status
                toolCall.result?.contains("reject", ignoreCase = true) == true -> ChangeStatus.REJECTED
                toolCall.success == true || toolCall.result != null -> ChangeStatus.APPLIED
                else -> ChangeStatus.APPLIED
            }
        }

        InlineDiffCard(
            filePath = filePath.ifBlank { matchingPendingChange?.filePath ?: "modified file" },
            changeType = historicalChangeType,
            originalContent = originalContent,
            proposedContent = proposedContent,
            pendingChange = matchingPendingChange,
            statusOverride = statusOverride,
            onApprove = onApproveChange,
            onReject = onRejectChange,
            onOpenFullReview = onOpenFullReview,
            initiallyExpanded = statusOverride == ChangeStatus.PENDING,
            modifier = modifier
        )
        return
    }

    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val info = remember(toolCall.name) { getToolInfo(toolCall.name) }
    val argSummary = remember(toolCall.arguments) { extractArgSummary(toolCall.arguments) }

    val cardShape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
            .border(
                width = 1.dp,
                brush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.45f, dispersionAlpha = 0.18f),
                shape = cardShape
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Icon badge
                val iconShape = RoundedCornerShape(8.dp)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(iconShape)
                        .background(GlassDesign.microGlassBackground(topAlpha = 0.20f, bottomAlpha = 0.08f))
                        .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.40f, bottomAlpha = 0.12f), iconShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = info.icon,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = Color(0xFF58A6FF)
                    )
                }

                // Title and argument preview
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFF0F6FC)
                    )
                    if (argSummary.isNotBlank()) {
                        Text(
                            text = argSummary,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFF8B949E),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Completion status
                val statusShape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .clip(statusShape)
                        .background(GlassDesign.microGlassBackground(topAlpha = 0.18f, bottomAlpha = 0.06f))
                        .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f), statusShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFF39D353)
                        )
                        Text(
                            text = "Tool",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFF39D353)
                        )
                    }
                }

                // Expand icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded details
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    val isCommand = ToolNames.normalize(toolCall.name) == ToolNames.EXECUTE_COMMAND ||
                        toolCall.name in listOf("run_command", "bash", "exec_command", "execute_command")

                    if (isCommand) {
                        val cmd = try {
                            val json = Json.parseToJsonElement(toolCall.arguments).jsonObject
                            json["command"]?.jsonPrimitive?.content
                                ?: json["CommandLine"]?.jsonPrimitive?.content
                                ?: json["cmd"]?.jsonPrimitive?.content
                                ?: toolCall.arguments
                        } catch (_: Exception) {
                            toolCall.arguments
                        }
                        CodeBlockSection(
                            title = "Command Line",
                            content = "$ $cmd"
                        )
                    } else if (toolCall.arguments.isNotBlank()) {
                        CodeBlockSection(
                            title = "Arguments",
                            content = toolCall.arguments
                        )
                    }

                    val callResult = toolCall.result
                    if (callResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CodeBlockSection(
                            title = if (isCommand) "Terminal Output" else "Result",
                            content = callResult
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockSection(
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var showFullContent by remember(content) { mutableStateOf(false) }

    val rawContent = content.trim()
    val lines = remember(rawContent) { rawContent.lines() }
    val isTruncatedNeeded = remember(lines, rawContent) {
        lines.size > 10 || rawContent.length > 600
    }
    val displayContent = remember(rawContent, lines, isTruncatedNeeded, showFullContent) {
        if (!isTruncatedNeeded || showFullContent) {
            rawContent
        } else {
            if (lines.size > 10) lines.take(10).joinToString("\n") else rawContent.take(600)
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        clipboardManager.setText(AnnotatedString(content))
                        copied = true
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Done else Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(12.dp),
                    tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (copied) "Copied" else "Copy",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (showFullContent) Modifier.heightIn(max = 400.dp)
                    else Modifier.heightIn(max = 240.dp)
                )
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = displayContent,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (isTruncatedNeeded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { showFullContent = !showFullContent }
                    .padding(top = 4.dp, bottom = 2.dp, start = 2.dp, end = 2.dp)
            ) {
                Text(
                    text = if (showFullContent) "Show less" else "Show more",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    ),
                    color = Color(0xFF58A6FF)
                )
                Icon(
                    imageVector = if (showFullContent) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (showFullContent) "Show less" else "Show more",
                    modifier = Modifier.size(13.dp),
                    tint = Color(0xFF58A6FF)
                )
                if (!showFullContent && lines.size > 10) {
                    Text(
                        text = "(${lines.size - 10} more lines)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class ToolInfo(val title: String, val icon: ImageVector)

private fun getToolInfo(name: String): ToolInfo = when (ToolNames.normalize(name)) {
    ToolNames.LIST_FILES -> ToolInfo("List Files", Icons.Default.FolderOpen)
    ToolNames.READ_FILE -> ToolInfo("Read File", Icons.Default.Description)
    ToolNames.SEARCH_CODE -> ToolInfo("Search Code", Icons.Default.Search)
    ToolNames.GET_FILE_SUMMARY -> ToolInfo("File Summary", Icons.Default.Info)
    ToolNames.PROPOSE_FILE_EDIT -> ToolInfo("Propose Edit", Icons.Default.Edit)
    ToolNames.CREATE_FILE -> ToolInfo("Create File", Icons.AutoMirrored.Filled.NoteAdd)
    ToolNames.RENAME_FILE -> ToolInfo("Rename File", Icons.Default.DriveFileRenameOutline)
    ToolNames.DELETE_FILE -> ToolInfo("Delete File", Icons.Outlined.Delete)
    ToolNames.EXECUTE_COMMAND -> ToolInfo("Run Command", Icons.Default.Terminal)
    ToolNames.WEB_SEARCH -> ToolInfo("Web Search", Icons.Default.Public)
    ToolNames.FETCH_WEB_PAGE -> ToolInfo("Fetch Web Page", Icons.Default.Language)
    ToolNames.MANAGE_TASK -> ToolInfo("Manage Task", Icons.Default.Terminal)
    else -> ToolInfo(name.replace('_', ' ').replaceFirstChar { it.uppercase() }, Icons.Default.Terminal)
}

private fun extractArgSummary(arguments: String): String {
    if (arguments.isBlank()) return ""
    return try {
        val json = Json.parseToJsonElement(arguments).jsonObject
        when {
            json.containsKey("action") -> {
                val action = json["action"]?.jsonPrimitive?.content ?: ""
                val taskId = json["task_id"]?.jsonPrimitive?.content?.let { " ($it)" } ?: ""
                val checkCount = json["_check_count"]?.jsonPrimitive?.content?.let { " · $it checks" } ?: ""
                "⚙️ $action$taskId$checkCount"
            }
            json.containsKey("command") || json.containsKey("CommandLine") || json.containsKey("cmd") -> {
                val cmd = json["command"]?.jsonPrimitive?.content
                    ?: json["CommandLine"]?.jsonPrimitive?.content
                    ?: json["cmd"]?.jsonPrimitive?.content
                    ?: ""
                "$ $cmd"
            }
            json.containsKey("query") -> "🔍 \"${json["query"]?.jsonPrimitive?.content ?: ""}\""
            json.containsKey("url") -> "🌐 ${json["url"]?.jsonPrimitive?.content ?: ""}"
            json.containsKey("path") -> json["path"]?.jsonPrimitive?.content ?: ""
            json.containsKey("dirPath") -> json["dirPath"]?.jsonPrimitive?.content ?: ""
            json.containsKey("oldPath") && json.containsKey("newPath") ->
                "${json["oldPath"]?.jsonPrimitive?.content} → ${json["newPath"]?.jsonPrimitive?.content}"
            else -> json.entries.take(2).joinToString(", ") { "${it.key}: ${it.value.jsonPrimitive.content}" }
        }
    } catch (_: Exception) {
        arguments.take(50)
    }
}
