package com.koda.dev.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koda.dev.core.model.ChangeStatus
import com.koda.dev.core.model.PendingChange
import com.koda.dev.core.model.ToolCallData
import com.koda.dev.core.model.ToolNames

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

    val toolCount = toolCalls.size
    val toolLabel = remember(toolCount) {
        if (toolCount == 1) "1 tool executed" else "$toolCount tools executed"
    }

    val distinctToolNames = remember(toolCalls) {
        toolCalls.map { ToolNames.normalize(it.name) }.distinct()
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
        // Summary Header Pill
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
            border = BorderStroke(
                1.dp,
                when {
                    hasFailure -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                    pendingReviewCount > 0 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    userToggled = true
                    isExpanded = !isExpanded
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Leading status indicator
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (hasFailure) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Tool failure",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Title and Tool names preview
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isRunning) "Running tools..." else toolLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (pendingReviewCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Text(
                                    text = "$pendingReviewCount pending review",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Expand/collapse arrow indicator
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                for (tc in toolCalls) {
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
