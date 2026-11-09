package com.koda.dev.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koda.dev.core.model.ChangeStatus
import com.koda.dev.core.model.PendingChange
import com.koda.dev.core.model.ToolCallData
import com.koda.dev.core.ui.glass.GlassDesign
import com.koda.dev.core.ui.glass.springPressClickable
import com.koda.dev.core.model.ToolNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A collapsible accordion container for tool calls executed within an assistant turn.
 * While actively executing (isLive == true), it stays expanded so the user sees live progress.
 * As soon as execution completes (isLive == false), it automatically folds into a compact pill,
 * keeping the chat screen clean while allowing on-demand inspection via a single tap.
 */
@Composable
fun ToolCallsAccordion(
    toolCalls: List<ToolCallData>,
    isLive: Boolean,
    messageId: String,
    modifier: Modifier = Modifier,
    pendingChanges: List<PendingChange> = emptyList(),
    onApproveChange: ((PendingChange) -> Unit)? = null,
    onRejectChange: ((PendingChange) -> Unit)? = null,
    onOpenFullReview: ((PendingChange?) -> Unit)? = null
) {
    if (toolCalls.isEmpty()) return

    // Expanded by default when live, collapsed when finished
    var isExpanded by rememberSaveable(messageId) { mutableStateOf(isLive) }
    var userToggled by remember(messageId) { mutableStateOf(false) }

    // Auto-collapse when task finishes, unless user has manually toggled it
    LaunchedEffect(isLive) {
        if (!userToggled) {
            isExpanded = isLive
        }
    }

    val displayToolCalls = remember(toolCalls) {
        deduplicateToolCallsForDisplay(toolCalls)
    }

    val toolCount = displayToolCalls.size
    val rawCount = toolCalls.size
    val toolLabel = remember(toolCount, rawCount) {
        when {
            rawCount == 1 -> "1 tool executed"
            toolCount == rawCount -> "$toolCount tools executed"
            else -> "$toolCount tools executed ($rawCount steps)"
        }
    }

    val distinctToolNames = remember(displayToolCalls) {
        displayToolCalls.map { ToolNames.normalize(it.name) }.distinct()
    }
    val namesSummary = remember(distinctToolNames) {
        distinctToolNames.joinToString(", ")
    }

    val pendingReviewCount = remember(toolCalls, pendingChanges) {
        pendingChanges.count { it.status == ChangeStatus.PENDING }
    }

    val hasFailure = remember(toolCalls) {
        toolCalls.any { tc ->
            tc.success == false || tc.result?.startsWith("Error:") == true
        }
    }

    val isRunning = isLive && toolCalls.any { it.result == null }

    Column(modifier = modifier.fillMaxWidth()) {
        // Summary Header Pill - Micro Glass Accordion
        val headerShape = RoundedCornerShape(12.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(headerShape)
                .then(
                    if (hasFailure) Modifier.background(Color(0x22F85149))
                    else Modifier.background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                )
                .border(
                    width = 0.75.dp,
                    brush = when {
                        hasFailure -> Brush.verticalGradient(listOf(Color(0x88F85149), Color(0x44F85149)))
                        pendingReviewCount > 0 -> Brush.verticalGradient(listOf(Color(0x88D29922), Color(0x44D29922)))
                        else -> GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f)
                    },
                    shape = headerShape
                )
                .springPressClickable {
                    userToggled = true
                    isExpanded = !isExpanded
                }
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Leading status indicator in frosted micro-disc
                val discShape = CircleShape
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(discShape)
                        .background(
                            when {
                                hasFailure -> Color(0x33F85149)
                                isRunning -> Color(0x3358A6FF)
                                else -> Color(0x2239D353)
                            }
                        )
                        .border(
                            0.75.dp,
                            when {
                                hasFailure -> Color(0x66F85149)
                                isRunning -> Color(0x6658A6FF)
                                else -> Color(0x4439D353)
                            },
                            discShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF58A6FF)
                        )
                    } else if (hasFailure) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Tool failure",
                            modifier = Modifier.size(13.dp),
                            tint = Color(0xFFFF7B72)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = Color(0xFF39D353)
                        )
                    }
                }

                // Title and Tool names preview
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isRunning) "Running tools..." else toolLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFF0F6FC)
                        )
                        if (pendingReviewCount > 0) {
                            val badgeShape = RoundedCornerShape(6.dp)
                            Box(
                                modifier = Modifier
                                    .clip(badgeShape)
                                    .background(Color(0x33D29922))
                                    .border(0.75.dp, Color(0x66D29922), badgeShape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$pendingReviewCount pending review",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color(0xFFE3B341)
                                )
                            }
                        }
                    }
                    if (namesSummary.isNotBlank()) {
                        Text(
                            text = namesSummary,
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

                // Expand/collapse arrow indicator in micro-glass disc
                val chevronDisc = CircleShape
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(chevronDisc)
                        .background(GlassDesign.microGlassBackground(topAlpha = 0.12f, bottomAlpha = 0.04f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF8B949E)
                    )
                }
            }
        }

        // Expanded Tool Cards
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(150)) + expandVertically(tween(200)),
            exit = fadeOut(tween(100)) + shrinkVertically(tween(150))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (tc in displayToolCalls) {
                    ToolCallCard(
                        toolCall = tc,
                        initiallyExpanded = false,
                        pendingChanges = pendingChanges,
                        onApproveChange = onApproveChange,
                        onRejectChange = onRejectChange,
                        onOpenFullReview = onOpenFullReview
                    )
                }
            }
        }
    }
}

/**
 * Deduplicates repetitive manage_task status checks for the same task in an accordion,
 * retaining only the latest status check per task while badging the number of checks made.
 */
fun deduplicateToolCallsForDisplay(toolCalls: List<ToolCallData>): List<ToolCallData> {
    if (toolCalls.size <= 1) return toolCalls

    val result = mutableListOf<ToolCallData>()
    val statusIndices = mutableMapOf<String, Int>()
    val statusCounts = mutableMapOf<String, Int>()

    for (tc in toolCalls) {
        val normName = ToolNames.normalize(tc.name)
        val isStatus = normName == ToolNames.MANAGE_TASK && tc.arguments.contains("\"status\"")
        val taskId = if (isStatus) {
            try {
                Json.parseToJsonElement(tc.arguments).jsonObject["task_id"]?.jsonPrimitive?.content
            } catch (_: Exception) { null }
        } else null

        if (isStatus && taskId != null) {
            val count = (statusCounts[taskId] ?: 0) + 1
            statusCounts[taskId] = count
            val existingIndex = statusIndices[taskId]
            if (existingIndex != null) {
                // Update the existing entry in place with the latest result and arguments
                val updatedArgs = try {
                    val obj = Json.parseToJsonElement(tc.arguments).jsonObject.toMutableMap()
                    obj["_check_count"] = JsonPrimitive(count)
                    JsonObject(obj).toString()
                } catch (_: Exception) {
                    tc.arguments
                }
                result[existingIndex] = tc.copy(arguments = updatedArgs)
            } else {
                statusIndices[taskId] = result.size
                result.add(tc)
            }
        } else {
            result.add(tc)
        }
    }
    return result
}
