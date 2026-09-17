package com.codeagent.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeagent.core.model.ToolCallData
import com.codeagent.core.model.ToolNames
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ToolCallCard(
    toolCall: ToolCallData,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val info = remember(toolCall.name) { getToolInfo(toolCall.name) }
    val argSummary = remember(toolCall.arguments) { extractArgSummary(toolCall.arguments) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
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
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = info.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Title and argument preview
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (argSummary.isNotBlank()) {
                        Text(
                            text = argSummary,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Completion status
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tool",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
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

                    val isCommand = ToolNames.normalize(toolCall.name) == ToolNames.EXECUTE_COMMAND

                    if (isCommand) {
                        val cmd = try {
                            val json = Json.parseToJsonElement(toolCall.arguments).jsonObject
                            json["command"]?.jsonPrimitive?.content ?: toolCall.arguments
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
fun ToolResultCard(
    content: String,
    toolCallId: String? = null,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "Tool Output",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (isExpanded) "Hide details" else "Show details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    CodeBlockSection(
                        title = if (toolCallId != null) "Result ($toolCallId)" else "Result",
                        content = content
                    )
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
                .heightIn(max = 240.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
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
    else -> ToolInfo(name.replace('_', ' ').replaceFirstChar { it.uppercase() }, Icons.Default.Terminal)
}

private fun extractArgSummary(arguments: String): String {
    if (arguments.isBlank()) return ""
    return try {
        val json = Json.parseToJsonElement(arguments).jsonObject
        when {
            json.containsKey("command") -> "$ ${json["command"]?.jsonPrimitive?.content ?: ""}"
            json.containsKey("path") -> json["path"]?.jsonPrimitive?.content ?: ""
            json.containsKey("query") -> "\"${json["query"]?.jsonPrimitive?.content ?: ""}\""
            json.containsKey("dirPath") -> json["dirPath"]?.jsonPrimitive?.content ?: ""
            json.containsKey("oldPath") && json.containsKey("newPath") ->
                "${json["oldPath"]?.jsonPrimitive?.content} → ${json["newPath"]?.jsonPrimitive?.content}"
            else -> json.entries.take(2).joinToString(", ") { "${it.key}: ${it.value.jsonPrimitive.content}" }
        }
    } catch (_: Exception) {
        arguments.take(50)
    }
}
