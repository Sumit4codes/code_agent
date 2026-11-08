package com.koda.dev.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koda.dev.core.agent.ContextEstimator
import com.koda.dev.core.agent.ContextUsageState

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

    val colorScheme = MaterialTheme.colorScheme
    val ringColor = when {
        fraction >= 0.90f -> colorScheme.error
        fraction >= 0.75f -> Color(0xFFFFA000)
        else -> colorScheme.primary
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Context Window & Token Usage",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header: Model and Window Bar
                Column {
                    Text(
                        text = "Active Model: $modelName",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$usedFormatted / $maxFormatted tokens (%.1f%%)".format(contextUsage.percentage),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ringColor
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = ringColor,
                        trackColor = ringColor.copy(alpha = 0.2f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$remainingFormatted tokens available before context limit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                // Breakdown section
                Text(
                    text = "Prompt Breakdown",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )

                BreakdownItem(
                    label = "Messages (History & User Input)",
                    tokens = contextUsage.messagesTokens,
                    total = contextUsage.usedTokens
                )

                BreakdownItem(
                    label = "Tools & Function Schemas",
                    tokens = contextUsage.toolsTokens,
                    total = contextUsage.usedTokens
                )

                BreakdownItem(
                    label = "System Instructions & Rules",
                    tokens = contextUsage.systemTokens,
                    total = contextUsage.usedTokens
                )

                BreakdownItem(
                    label = "Metadata & Safety Reserve",
                    tokens = contextUsage.metaTokens,
                    total = contextUsage.usedTokens
                )

                // Last request usage if available
                val lastIn = contextUsage.lastInputTokens
                val lastOut = contextUsage.lastOutputTokens
                if (lastIn != null || lastOut != null) {
                    HorizontalDivider()

                    Text(
                        text = "Last Completed Request",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )

                    if (lastIn != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Prompt Tokens",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = ContextEstimator.formatTokenCount(lastIn),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }

                    if (lastOut != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Completion Tokens",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = ContextEstimator.formatTokenCount(lastOut),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onCompact()
                }
            ) {
                Text("Compact History")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${ContextEstimator.formatTokenCount(tokens)} (%.0f%%)".format(pct),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
        )
    }
}
