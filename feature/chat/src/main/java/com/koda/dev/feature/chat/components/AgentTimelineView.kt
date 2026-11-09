package com.koda.dev.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koda.dev.core.model.ToolCallData

/**
 * Modern collapsible agent activity timeline matching the reference design.
 * Features:
 * - Top duration header: "Worked for 1m ⌄" or "Worked for 8m ›"
 * - Collapsed by default when execution completes, expanded while running live.
 * - Hierarchical activity steps: "Ran check git status ⌄", "Install APK on device finished ›".
 * - Embedded dark terminal block for command inputs and outputs.
 */
@Composable
fun AgentTimelineView(
    toolCalls: List<ToolCallData>,
    isLive: Boolean,
    messageId: String,
    modifier: Modifier = Modifier,
    projectName: String = "code_agent"
) {
    if (toolCalls.isEmpty()) return

    // Auto-collapse on completion, auto-expand while actively executing
    var isExpanded by rememberSaveable(messageId) { mutableStateOf(isLive) }
    var userToggled by remember(messageId) { mutableStateOf(false) }

    LaunchedEffect(isLive) {
        if (!userToggled) {
            isExpanded = isLive
        }
    }

    val actions = remember(toolCalls, projectName) {
        val deduped = deduplicateToolCallsForDisplay(toolCalls)
        deduped.map { toHumanAction(it, "~/code_agent") }
    }

    val totalDurationMs = remember(toolCalls) {
        toolCalls.mapNotNull { it.durationMs }.sum().takeIf { it > 0L }
            ?: (toolCalls.size * 2500L) // graceful fallback
    }

    val workedLabel = remember(totalDurationMs, isLive) {
        if (isLive) "Working..." else formatWorkedDuration(totalDurationMs)
    }

    val isRunning = isLive && toolCalls.any { it.result == null }
    val hasFailure = remember(toolCalls) {
        toolCalls.any { it.success == false || it.result?.startsWith("Error:") == true }
    }

    // Keep track of which individual action terminal views are expanded
    val expandedActions = remember(messageId) { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Top Timeline Header ("Worked for 1m ⌄")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    userToggled = true
                    isExpanded = !isExpanded
                }
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.75.dp,
                    color = Color(0xFF2F81F7)
                )
            } else if (hasFailure) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Failed step",
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFFF85149)
                )
            }

            Text(
                text = workedLabel,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFF8B949E)
            )

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse timeline" else "Expand timeline",
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF8B949E)
            )
        }

        // Expanded Action Steps Tree
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 4.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Group actions by category if there are several
                val grouped = remember(actions) {
                    actions.groupBy { it.category }
                }

                for ((category, categoryActions) in grouped) {
                    var categoryExpanded by remember(messageId, category) { mutableStateOf(true) }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Category Header (e.g. "Ran commands ⌄")
                        if (grouped.size > 1 || categoryActions.size > 2) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { categoryExpanded = !categoryExpanded }
                                    .padding(vertical = 3.dp)
                            ) {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color(0xFF8B949E)
                                )
                                Icon(
                                    imageVector = if (categoryExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF6E7681)
                                )
                            }
                        }

                        // Child Action Items
                        AnimatedVisibility(
                            visible = categoryExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = if (grouped.size > 1 || categoryActions.size > 2) 10.dp else 0.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                for (action in categoryActions) {
                                    val isActionExpanded = expandedActions[action.id] ?: false

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Step Row: "Ran check git status ⌄"
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable {
                                                    expandedActions[action.id] = !isActionExpanded
                                                }
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = action.title,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isActionExpanded) FontWeight.Medium else FontWeight.Normal
                                                ),
                                                color = if (action.isError) Color(0xFFF85149) else Color(0xFF8B949E)
                                            )
                                            Icon(
                                                imageVector = if (isActionExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp),
                                                tint = Color(0xFF6E7681)
                                            )
                                        }

                                        // Detailed Output / Terminal Box when step is expanded
                                        AnimatedVisibility(
                                            visible = isActionExpanded,
                                            enter = fadeIn() + expandVertically(),
                                            exit = fadeOut() + shrinkVertically()
                                        ) {
                                            TerminalOutputCard(action = action)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Embedded dark terminal card showing the command line prompt and output.
 */
@Composable
private fun TerminalOutputCard(
    action: HumanAction,
    modifier: Modifier = Modifier
) {
    val horizontalScroll = rememberScrollState()
    val cardShape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(cardShape)
            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
            .border(
                width = 0.75.dp,
                brush = GlassDesign.microGlassBorder(topAlpha = 0.32f, bottomAlpha = 0.08f),
                shape = cardShape
            )
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScroll),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Prompt line: ~/.../code_agent $ git status
            if (action.isTerminal && !action.commandLine.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "~/.../code_agent $ ",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF8B949E)
                    )
                    Text(
                        text = action.commandLine,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFFF0F6FC)
                    )
                }
            }

            // Command output
            if (!action.output.isNullOrBlank()) {
                Text(
                    text = action.output.trim(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    ),
                    color = if (action.isError) Color(0xFFF85149) else Color(0xFFC9D1D9)
                )
            } else {
                Text(
                    text = "(No output or completed silently)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = Color(0xFF6E7681)
                )
            }
        }
    }
}
