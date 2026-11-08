package com.koda.dev.feature.chat.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.koda.dev.core.agent.ContextEstimator
import com.koda.dev.core.agent.ContextUsageState

/**
 * Context window & token usage dialog styled with Liquid Glass effect.
 */
@Composable
fun ContextUsageDialog(
    contextUsage: ContextUsageState,
    modelName: String,
    onDismiss: () -> Unit,
    onCompact: () -> Unit
) {
    val fraction = contextUsage.fraction
    val usedFormatted = ContextEstimator.formatTokenCount(contextUsage.usedTokens)
    val maxFormatted = ContextEstimator.formatTokenCount(contextUsage.maxTokens)
    val remainingFormatted = ContextEstimator.formatTokenCount(contextUsage.remainingTokens)

    val ringColor = when {
        fraction >= 0.90f -> Color(0xFFF85149)
        fraction >= 0.75f -> Color(0xFFD29922) // Amber warning
        else -> Color(0xFF58A6FF)              // Cyan/blue accent
    }

    val dialogShape = RoundedCornerShape(26.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .shadow(
                    elevation = 24.dp,
                    shape = dialogShape,
                    ambientColor = Color(0x80000000),
                    spotColor = Color(0xCC000000)
                )
                .clip(dialogShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF222836), // Frosted slate top sheen
                            Color(0xFF171B24), // Optical core
                            Color(0xFF10141C)  // Deep liquid glass base
                        )
                    )
                )
                .border(
                    width = 1.25.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0x80FFFFFF), // Bright specular highlight at top curve
                            Color(0x3379C0FF), // Dispersion tint
                            Color(0x18FFFFFF),
                            Color(0x08FFFFFF)
                        )
                    ),
                    shape = dialogShape
                )
        ) {
            // Top specular hairline highlight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 32.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0x33FFFFFF),
                                Color(0x80FFFFFF),
                                Color(0x33FFFFFF),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // Header with Title and Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Context Window",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = Color(0xFFF0F6FC)
                        )
                        Text(
                            text = "Model: $modelName",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp
                            ),
                            color = Color(0xFF58A6FF)
                        )
                    }

                    // Frosted Close Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x26FFFFFF), Color(0x0FFFFFFF))
                                )
                            )
                            .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFC9D1D9)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Meter Card: Prominent Frosted Container
                    val meterCardShape = RoundedCornerShape(16.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(meterCardShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x2BFFFFFF), Color(0x10FFFFFF))
                                )
                            )
                            .border(0.75.dp, Color(0x33FFFFFF), meterCardShape)
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$usedFormatted / $maxFormatted tokens",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = Color(0xFFF0F6FC)
                                )
                                Text(
                                    text = "%.1f%%".format(contextUsage.percentage),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    ),
                                    color = ringColor
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = ringColor,
                                trackColor = ringColor.copy(alpha = 0.2f),
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = "$remainingFormatted tokens available before context limit",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = Color(0xFF8B949E)
                            )
                        }
                    }

                    // Breakdown section
                    Text(
                        text = "Prompt Breakdown",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        ),
                        color = Color(0xFFF0F6FC)
                    )

                    val breakdownCardShape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(breakdownCardShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF))
                                )
                            )
                            .border(0.5.dp, Color(0x26FFFFFF), breakdownCardShape)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BreakdownItem(
                                label = "Messages (History & Input)",
                                tokens = contextUsage.messagesTokens,
                                total = contextUsage.usedTokens
                            )
                            HorizontalDivider(color = Color(0x1AFFFFFF))
                            BreakdownItem(
                                label = "Tools & Function Schemas",
                                tokens = contextUsage.toolsTokens,
                                total = contextUsage.usedTokens
                            )
                            HorizontalDivider(color = Color(0x1AFFFFFF))
                            BreakdownItem(
                                label = "System Instructions & Rules",
                                tokens = contextUsage.systemTokens,
                                total = contextUsage.usedTokens
                            )
                            HorizontalDivider(color = Color(0x1AFFFFFF))
                            BreakdownItem(
                                label = "Metadata & Safety Reserve",
                                tokens = contextUsage.metaTokens,
                                total = contextUsage.usedTokens
                            )
                        }
                    }

                    // Last request usage if available
                    val lastIn = contextUsage.lastInputTokens
                    val lastOut = contextUsage.lastOutputTokens
                    if (lastIn != null || lastOut != null) {
                        Text(
                            text = "Last Completed Request",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            ),
                            color = Color(0xFFF0F6FC)
                        )

                        val lastReqShape = RoundedCornerShape(14.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(lastReqShape)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF))
                                    )
                                )
                                .border(0.5.dp, Color(0x26FFFFFF), lastReqShape)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (lastIn != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Prompt Tokens",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF8B949E)
                                        )
                                        Text(
                                            text = ContextEstimator.formatTokenCount(lastIn),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFFE6EDF3)
                                            )
                                        )
                                    }
                                }
                                if (lastIn != null && lastOut != null) {
                                    HorizontalDivider(color = Color(0x1AFFFFFF))
                                }
                                if (lastOut != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Completion Tokens",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF8B949E)
                                        )
                                        Text(
                                            text = ContextEstimator.formatTokenCount(lastOut),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFFE6EDF3)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val btnShape = RoundedCornerShape(12.dp)

                        // Close button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(btnShape)
                                .background(Color(0x1FFFFFFF))
                                .border(0.5.dp, Color(0x33FFFFFF), btnShape)
                                .clickable { onDismiss() }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Close",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = Color(0xFFF0F6FC)
                            )
                        }

                        // Compact History button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(
                                    elevation = 6.dp,
                                    shape = btnShape,
                                    ambientColor = Color(0x401F6FEB),
                                    spotColor = Color(0x601F6FEB)
                                )
                                .clip(btnShape)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF388BFF), Color(0xFF1F6FEB))
                                    )
                                )
                                .border(0.75.dp, Color(0x80FFFFFF), btnShape)
                                .clickable {
                                    onDismiss()
                                    onCompact()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Compact History",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownItem(label: String, tokens: Int, total: Int) {
    val pct = if (total > 0) (tokens.toFloat() / total * 100f) else 0f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8B949E),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${ContextEstimator.formatTokenCount(tokens)} (%.0f%%)".format(pct),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE6EDF3)
            )
        )
    }
}
