package com.koda.dev.feature.chat.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koda.dev.core.agent.ContextEstimator
import com.koda.dev.core.agent.ContextUsageState

@Composable
fun ContextWindowButton(
    contextUsage: ContextUsageState,
    onCompact: () -> Unit = {},
    onOpenDetails: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPopover by remember { mutableStateOf(false) }

    val fraction = contextUsage.fraction
    val usedFormatted = ContextEstimator.formatTokenCount(contextUsage.usedTokens)
    val maxFormatted = ContextEstimator.formatTokenCount(contextUsage.maxTokens)
    val remainingFormatted = ContextEstimator.formatTokenCount(contextUsage.remainingTokens)

    val colorScheme = MaterialTheme.colorScheme
    val ringColor = when {
        fraction >= 0.90f -> colorScheme.error
        fraction >= 0.75f -> Color(0xFFFFA000) // Amber warning
        else -> colorScheme.primary
    }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
            modifier = Modifier.clickable { showPopover = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Circular arc ring gauge
                Canvas(modifier = Modifier.size(14.dp)) {
                    val stroke = 2.dp.toPx()
                    val inset = stroke / 2
                    val bounds = Size(size.width - stroke, size.height - stroke)

                    // Track
                    drawArc(
                        color = ringColor.copy(alpha = 0.25f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = bounds,
                        style = Stroke(stroke)
                    )

                    // Active progress arc
                    if (fraction > 0f) {
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * fraction,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = bounds,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                }

                Text(
                    text = "$usedFormatted/$maxFormatted",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = ringColor
                )
            }
        }

        // Popover dropdown menu
        DropdownMenu(
            expanded = showPopover,
            onDismissRequest = { showPopover = false },
            modifier = Modifier.width(260.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Context Window",
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = "$usedFormatted / $maxFormatted tokens (%.1f%%)".format(contextUsage.percentage),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ringColor
                )

                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = ringColor,
                    trackColor = ringColor.copy(alpha = 0.2f),
                )

                Text(
                    text = "$remainingFormatted tokens remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = ContextEstimator.formatTokenCount(contextUsage.messagesTokens),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tools & System",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = ContextEstimator.formatTokenCount(contextUsage.toolsTokens + contextUsage.systemTokens),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            showPopover = false
                            onOpenDetails()
                        }
                    ) {
                        Text("Details")
                    }

                    TextButton(
                        onClick = {
                            showPopover = false
                            onCompact()
                        }
                    ) {
                        Text("Compact")
                    }
                }
            }
        }
    }
}
