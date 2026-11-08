package com.koda.dev.feature.chat.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        fraction >= 0.90f -> Color(0xFFF85149)
        fraction >= 0.75f -> Color(0xFFD29922) // Amber warning
        else -> Color(0xFF58A6FF)              // Cyan/blue accent
    }

    val pillShape = RoundedCornerShape(12.dp)

    Box(modifier = modifier) {
        // Frosted Glass Meter Pill
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x2EFFFFFF),
                            Color(0x12FFFFFF)
                        )
                    )
                )
                .border(
                    width = 0.75.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0x55FFFFFF),
                            Color(0x18FFFFFF)
                        )
                    ),
                    shape = pillShape
                )
                .clickable { showPopover = true }
                .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Circular arc ring gauge
            Canvas(modifier = Modifier.size(13.dp)) {
                val stroke = 1.75.dp.toPx()
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
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = ringColor
            )
        }

        // Popover dropdown menu in Liquid Glass
        val popoverShape = RoundedCornerShape(18.dp)

        DropdownMenu(
            expanded = showPopover,
            onDismissRequest = { showPopover = false },
            shape = popoverShape,
            containerColor = Color.Transparent,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            border = null,
            modifier = Modifier
                .width(270.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = popoverShape,
                    ambientColor = Color(0x80000000),
                    spotColor = Color(0xCC000000)
                )
                .clip(popoverShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF222836),
                            Color(0xFF171B24),
                            Color(0xFF10141C)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0x80FFFFFF),
                            Color(0x2679C0FF),
                            Color(0x10FFFFFF)
                        )
                    ),
                    shape = popoverShape
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Text(
                    text = "Context Window",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = Color(0xFFF0F6FC)
                )

                Text(
                    text = "$usedFormatted / $maxFormatted tokens (%.1f%%)".format(contextUsage.percentage),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    color = ringColor
                )

                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ringColor,
                    trackColor = ringColor.copy(alpha = 0.2f),
                )

                Text(
                    text = "$remainingFormatted tokens remaining",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = Color(0xFF8B949E)
                )

                HorizontalDivider(
                    color = Color(0x26FFFFFF),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8B949E)
                    )
                    Text(
                        text = ContextEstimator.formatTokenCount(contextUsage.messagesTokens),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE6EDF3)
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tools & System",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8B949E)
                    )
                    Text(
                        text = ContextEstimator.formatTokenCount(contextUsage.toolsTokens + contextUsage.systemTokens),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE6EDF3)
                        )
                    )
                }

                HorizontalDivider(
                    color = Color(0x26FFFFFF),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Details button
                    val actionPillShape = RoundedCornerShape(10.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(actionPillShape)
                            .background(Color(0x1FFFFFFF))
                            .border(0.5.dp, Color(0x33FFFFFF), actionPillShape)
                            .clickable {
                                showPopover = false
                                onOpenDetails()
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Details",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = Color(0xFFF0F6FC)
                        )
                    }

                    // Compact button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(actionPillShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF388BFF), Color(0xFF1F6FEB))
                                )
                            )
                            .border(0.75.dp, Color(0x66FFFFFF), actionPillShape)
                            .clickable {
                                showPopover = false
                                onCompact()
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Compact",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
