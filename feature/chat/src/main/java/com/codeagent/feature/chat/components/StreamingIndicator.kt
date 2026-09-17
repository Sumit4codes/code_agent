package com.codeagent.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeagent.core.model.ActiveToolExecution
import com.codeagent.core.model.ToolExecutionStatus
import com.codeagent.core.model.ToolNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun StreamingBubble(
    streamingText: String,
    activeTool: ActiveToolExecution? = null,
    modelName: String = "",
    modifier: Modifier = Modifier
) {
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)
    val annotatedContent = remember(streamingText, codeBg) {
        buildStreamingAnnotatedString(streamingText, codeBg)
    }

    Surface(
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 4.dp,
            bottomEnd = 16.dp
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "CodeAgent",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (modelName.isNotBlank()) {
                    Text(
                        text = "· $modelName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active runtime tool execution
            if (activeTool != null) {
                ActiveToolRuntimeCard(activeTool = activeTool)
                if (streamingText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Streamed text or thinking animation
            if (streamingText.isNotBlank()) {
                Text(
                    text = annotatedContent,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else if (activeTool == null) {
                ThinkingAnimation()
            }
        }
    }
}

@Composable
fun ActiveToolRuntimeCard(
    activeTool: ActiveToolExecution,
    modifier: Modifier = Modifier
) {
    val info = remember(activeTool.name) { getRuntimeToolInfo(activeTool.name) }
    val argPreview = remember(activeTool.arguments) { formatRuntimeArguments(activeTool.arguments) }
    val scrollState = rememberScrollState()

    // Auto-scroll output console to bottom when new chunks arrive
    LaunchedEffect(activeTool.output) {
        if (activeTool.output.isNotEmpty()) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header row with Icon, Name, and Status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = info.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (argPreview.isNotBlank()) {
                        Text(
                            text = argPreview,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Live status chip
                when (activeTool.status) {
                    ToolExecutionStatus.RUNNING -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Running…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    ToolExecutionStatus.SUCCESS -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
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
                                    text = if (activeTool.durationMs != null) "${activeTool.durationMs}ms" else "Done",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    ToolExecutionStatus.ERROR -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Failed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Real-time Console output box if output exists
            if (activeTool.output.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(8.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = activeTool.output,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private data class RuntimeToolInfo(val title: String, val icon: ImageVector)

private fun getRuntimeToolInfo(name: String): RuntimeToolInfo = when (ToolNames.normalize(name)) {
    ToolNames.EXECUTE_COMMAND -> RuntimeToolInfo("Executing Command", Icons.Default.Terminal)
    ToolNames.LIST_FILES -> RuntimeToolInfo("Listing Files", Icons.Default.FolderOpen)
    ToolNames.READ_FILE -> RuntimeToolInfo("Reading File", Icons.Default.Description)
    ToolNames.SEARCH_CODE -> RuntimeToolInfo("Searching Code", Icons.Default.Search)
    ToolNames.GET_FILE_SUMMARY -> RuntimeToolInfo("Reading Summary", Icons.Default.Info)
    ToolNames.PROPOSE_FILE_EDIT -> RuntimeToolInfo("Proposing Edit", Icons.Default.Edit)
    ToolNames.CREATE_FILE -> RuntimeToolInfo("Creating File", Icons.AutoMirrored.Filled.NoteAdd)
    ToolNames.RENAME_FILE -> RuntimeToolInfo("Renaming File", Icons.Default.DriveFileRenameOutline)
    ToolNames.DELETE_FILE -> RuntimeToolInfo("Deleting File", Icons.Outlined.Delete)
    else -> RuntimeToolInfo(name.replace('_', ' ').replaceFirstChar { it.uppercase() }, Icons.Default.Terminal)
}

private fun formatRuntimeArguments(arguments: String): String {
    if (arguments.isBlank()) return ""
    return try {
        val json = Json.parseToJsonElement(arguments).jsonObject
        when {
            json.containsKey("command") -> "$ ${json["command"]?.jsonPrimitive?.content ?: ""}"
            json.containsKey("path") -> json["path"]?.jsonPrimitive?.content ?: ""
            json.containsKey("query") -> "\"${json["query"]?.jsonPrimitive?.content ?: ""}\""
            else -> json.entries.take(1).joinToString { "${it.key}: ${it.value.jsonPrimitive.content}" }
        }
    } catch (_: Exception) {
        arguments.take(40)
    }
}

@Composable
fun ThinkingAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "thinking_dots")
    val dot1Scale by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2Scale by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3Scale by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = "Thinking",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .scale(dot1Scale)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .scale(dot2Scale)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .scale(dot3Scale)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

private fun buildStreamingAnnotatedString(
    text: String,
    codeBg: Color
): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")

    val builder = AnnotatedString.Builder()
    if (!text.contains("```") && !text.contains('`')) {
        builder.append(text)
    } else {
        val fenceParts = text.split("```")
        for (i in fenceParts.indices) {
            if (i % 2 == 1) {
                // Inside ``` code block
                val block = fenceParts[i]
                val nlIndex = block.indexOf('\n')
                val code = if (nlIndex != -1) block.substring(nlIndex + 1) else block
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBg,
                        fontSize = 13.sp
                    )
                )
                builder.append(code)
                builder.pop()
            } else {
                // Regular text: check inline `code`
                val inlineParts = fenceParts[i].split("`")
                for (j in inlineParts.indices) {
                    if (j % 2 == 1) {
                        builder.pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBg,
                                fontSize = 13.sp
                            )
                        )
                        builder.append(inlineParts[j])
                        builder.pop()
                    } else {
                        builder.append(inlineParts[j])
                    }
                }
            }
        }
    }
    builder.append(" ▋")
    return builder.toAnnotatedString()
}
