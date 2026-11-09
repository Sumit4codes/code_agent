package com.koda.dev.feature.chat.components

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
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
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
import com.koda.dev.core.model.ActiveToolExecution
import com.koda.dev.core.model.ToolExecutionStatus
import com.koda.dev.core.model.ToolNames
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
    val codeBg = Color(0xFF21262D)
    val annotatedContent = remember(streamingText, codeBg) {
        buildStreamingAnnotatedString(streamingText, codeBg)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Active runtime tool execution
        if (activeTool != null) {
            ActiveToolRuntimeCard(activeTool = activeTool)
        }

        // Streamed text or thinking animation
        if (streamingText.isNotBlank()) {
            Text(
                text = annotatedContent,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.5.sp,
                    lineHeight = 22.sp
                ),
                color = Color(0xFFF0F6FC)
            )
        } else if (activeTool == null) {
            ThinkingAnimation()
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

    val cardShape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
            .border(
                width = 1.dp,
                brush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.50f, dispersionAlpha = 0.20f),
                shape = cardShape
            )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header row with Icon, Name, and Status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val iconShape = RoundedCornerShape(8.dp)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(iconShape)
                        .background(GlassDesign.microGlassBackground(topAlpha = 0.22f, bottomAlpha = 0.08f))
                        .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.40f, bottomAlpha = 0.12f), iconShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = info.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF58A6FF)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFF0F6FC)
                    )
                    if (argPreview.isNotBlank()) {
                        Text(
                            text = argPreview,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFF8B949E),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Live status chip
                val statusShape = RoundedCornerShape(12.dp)
                when (activeTool.status) {
                    ToolExecutionStatus.RUNNING -> {
                        Box(
                            modifier = Modifier
                                .clip(statusShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.20f, bottomAlpha = 0.08f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.40f, bottomAlpha = 0.12f), statusShape)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = Color(0xFF58A6FF)
                                )
                                Text(
                                    text = "Running…",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color(0xFF58A6FF)
                                )
                            }
                        }
                    }
                    ToolExecutionStatus.SUCCESS -> {
                        Box(
                            modifier = Modifier
                                .clip(statusShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.20f, bottomAlpha = 0.08f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.40f, bottomAlpha = 0.12f), statusShape)
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
                                    text = if (activeTool.durationMs != null) "${activeTool.durationMs}ms" else "Done",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color(0xFF39D353)
                                )
                            }
                        }
                    }
                    ToolExecutionStatus.ERROR -> {
                        Box(
                            modifier = Modifier
                                .clip(statusShape)
                                .background(Color(0x22F85149))
                                .border(0.75.dp, Color(0x44F85149), statusShape)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color(0xFFFF7B72)
                                )
                                Text(
                                    text = "Failed",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color(0xFFFF7B72)
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
    ToolNames.WEB_SEARCH -> RuntimeToolInfo("Searching Web", Icons.Default.Public)
    ToolNames.FETCH_WEB_PAGE -> RuntimeToolInfo("Fetching Web Page", Icons.Default.Language)
    ToolNames.MANAGE_TASK -> RuntimeToolInfo("Managing Process", Icons.Default.Terminal)
    else -> RuntimeToolInfo(name.replace('_', ' ').replaceFirstChar { it.uppercase() }, Icons.Default.Terminal)
}

private fun formatRuntimeArguments(arguments: String): String {
    if (arguments.isBlank()) return ""
    return try {
        val json = Json.parseToJsonElement(arguments).jsonObject
        when {
            json.containsKey("action") -> "⚙️ ${json["action"]?.jsonPrimitive?.content ?: ""}${json["task_id"]?.jsonPrimitive?.content?.let { " ($it)" } ?: ""}"
            json.containsKey("command") || json.containsKey("CommandLine") || json.containsKey("cmd") -> {
                val cmd = json["command"]?.jsonPrimitive?.content
                    ?: json["CommandLine"]?.jsonPrimitive?.content
                    ?: json["cmd"]?.jsonPrimitive?.content
                    ?: ""
                "$ $cmd"
            }
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
        builder.append(" ▋")
        return builder.toAnnotatedString()
    }

    val monoStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = codeBg,
        fontSize = 13.sp
    )

    var cursor = 0
    val length = text.length

    while (cursor < length) {
        val fenceStart = text.indexOf("```", cursor)
        if (fenceStart == -1) {
            // No more code fences, parse inline code till end
            appendInlineCodeSpans(builder, text, cursor, length, monoStyle)
            break
        }

        // Parse inline code before the fence
        if (fenceStart > cursor) {
            appendInlineCodeSpans(builder, text, cursor, fenceStart, monoStyle)
        }

        val codeContentStart = fenceStart + 3
        val fenceEnd = text.indexOf("```", codeContentStart)

        if (fenceEnd == -1) {
            // Unclosed code fence (currently streaming inside code block)
            val nlIndex = text.indexOf('\n', codeContentStart)
            val actualStart = if (nlIndex != -1) nlIndex + 1 else codeContentStart
            if (actualStart < length) {
                builder.pushStyle(monoStyle)
                builder.append(text.substring(actualStart))
                builder.pop()
            }
            break
        } else {
            // Closed code fence
            val nlIndex = text.indexOf('\n', codeContentStart)
            val actualStart = if (nlIndex != -1 && nlIndex < fenceEnd) nlIndex + 1 else codeContentStart
            if (actualStart < fenceEnd) {
                builder.pushStyle(monoStyle)
                builder.append(text.substring(actualStart, fenceEnd))
                builder.pop()
            }
            cursor = fenceEnd + 3
        }
    }

    builder.append(" ▋")
    return builder.toAnnotatedString()
}

private fun appendInlineCodeSpans(
    builder: AnnotatedString.Builder,
    text: String,
    start: Int,
    end: Int,
    monoStyle: SpanStyle
) {
    var cursor = start
    while (cursor < end) {
        val backtickStart = text.indexOf('`', cursor)
        if (backtickStart == -1 || backtickStart >= end) {
            builder.append(text.substring(cursor, end))
            break
        }

        if (backtickStart > cursor) {
            builder.append(text.substring(cursor, backtickStart))
        }

        val backtickEnd = text.indexOf('`', backtickStart + 1)
        if (backtickEnd == -1 || backtickEnd >= end) {
            // Unclosed inline backtick
            builder.append(text.substring(backtickStart, end))
            break
        }

        builder.pushStyle(monoStyle)
        builder.append(text.substring(backtickStart + 1, backtickEnd))
        builder.pop()
        cursor = backtickEnd + 1
    }
}
