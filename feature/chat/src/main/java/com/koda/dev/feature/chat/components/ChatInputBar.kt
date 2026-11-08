package com.koda.dev.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koda.dev.core.agent.ContextUsageState
import com.koda.dev.core.model.ProviderConfig

/**
 * Modern unified capsule bottom input bar matching the reference design:
 * Single continuous dark capsule holding:
 * - Multi-line prompt field with placeholder: "Ask anything, @ to mention, / for actions"
 * - Bottom strip with:
 *   - "+" options button (Goal mode, prompt options)
 *   - Model selector pill: Opens registered AI providers from settings
 *   - Circular Context Usage meter gauge
 *   - Circular action button (blue arrow "➔" / red stop "■")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    isStreaming: Boolean,
    hasProject: Boolean,
    projectName: String,
    modelName: String,
    availableProviders: List<ProviderConfig> = emptyList(),
    activeProvider: ProviderConfig? = null,
    onSelectProvider: ((String) -> Unit)? = null,
    onSelectGoal: () -> Unit = {},
    onSteer: (() -> Unit)? = null,
    contextUsage: ContextUsageState = ContextUsageState(),
    onCompact: () -> Unit = {},
    onOpenDetails: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showProviderSheet by remember { mutableStateOf(false) }

    if (showOptionsSheet) {
        ChatActionOptionsSheet(
            onDismiss = { showOptionsSheet = false },
            onSelectGoal = {
                onSelectGoal()
                showOptionsSheet = false
            }
        )
    }

    if (showProviderSheet) {
        ProviderSelectorSheet(
            providers = availableProviders,
            activeProviderId = activeProvider?.id,
            onSelectProvider = { providerId ->
                onSelectProvider?.invoke(providerId)
            },
            onDismiss = { showProviderSheet = false }
        )
    }

    val glassShape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color(0x660D1117),
                        Color(0xD90D1117),
                        Color(0xF50D1117)
                    )
                )
            )
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Goal Mode active pill if user typed /goal
        Column(modifier = Modifier.fillMaxWidth()) {
            AnimatedVisibility(
                visible = inputText.trimStart().startsWith("/goal", ignoreCase = true),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val goalShape = RoundedCornerShape(12.dp)
                Row(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clip(goalShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xD91E2A38),
                                    Color(0xB315202E)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0x8058A6FF),
                                    Color(0x2658A6FF)
                                )
                            ),
                            shape = goalShape
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🎯 Goal Mode Active",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF79C0FF)
                    )
                    IconButton(
                        onClick = {
                            val stripped = inputText.trimStart()
                                .removePrefix("/goal")
                                .removePrefix("/Goal")
                                .removePrefix("/GOAL")
                                .trimStart()
                            onInputChange(stripped)
                        },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove Goal Mode",
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFF79C0FF)
                        )
                    }
                }
            }

            // Main Unified Liquid Glass Capsule Card (Liquid Glass Effect)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 20.dp,
                        shape = glassShape,
                        ambientColor = Color(0x80000000),
                        spotColor = Color(0xCC000000)
                    )
                    .clip(glassShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF242B38), // Frosted slate-glass top sheen
                                Color(0xFF191E28), // Deep optical glass core
                                Color(0xFF12151E)  // Liquid refraction base
                            )
                        )
                    )
                    .border(
                        width = 1.25.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color(0x80FFFFFF), // Bright specular highlight at top apex
                                Color(0x3379C0FF), // Refraction cool dispersion tint
                                Color(0x18FFFFFF), // Soft lateral rim
                                Color(0x0DFFFFFF)  // Translucent bottom rim
                            )
                        ),
                        shape = glassShape
                    )
            ) {
                // Top Specular Hairline Highlight (Liquid Glass signature edge)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 28.dp)
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
                        .padding(top = 12.dp, bottom = 8.dp, start = 12.dp, end = 12.dp)
                ) {
                    // Top Multi-line Prompt Input Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 28.dp, max = 140.dp)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = when {
                                    isStreaming -> "Steer agent or guide run..."
                                    hasProject -> "Ask anything, @ to mention, / for actions"
                                    else -> "Open a project to chat..."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.5.sp,
                                    lineHeight = 20.sp
                                ),
                                color = Color(0xFF8B949E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        BasicTextField(
                            value = inputText,
                            onValueChange = onInputChange,
                            textStyle = TextStyle(
                                color = Color(0xFFF0F6FC),
                                fontSize = 14.5.sp,
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(Color(0xFF2F81F7)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.size(8.dp))

                    // Bottom Capsule Controls Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left items: "+" Button and Model Chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // "+" Button (Chat actions / Goal mode) - Micro-glass frosted disc
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0x26FFFFFF),
                                                Color(0x0FFFFFFF)
                                            )
                                        )
                                    )
                                    .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
                                    .clickable { showOptionsSheet = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Options and tools",
                                    modifier = Modifier.size(17.dp),
                                    tint = Color(0xFFF0F6FC)
                                )
                            }

                            // Model Selector Chip (opens Registered AI list) - Liquid Frosted Pill
                            val displayModel = remember(activeProvider, modelName) {
                                activeProvider?.name?.ifBlank { activeProvider.model }
                                    ?: modelName.ifBlank { "Choose AI" }
                            }

                            val chipShape = RoundedCornerShape(12.dp)
                            Row(
                                modifier = Modifier
                                    .clip(chipShape)
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
                                        shape = chipShape
                                    )
                                    .clickable { showProviderSheet = true }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = displayModel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color(0xFFF0F6FC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Switch model",
                                    modifier = Modifier.size(13.dp),
                                    tint = Color(0xFFC9D1D9)
                                )
                            }
                        }

                        // Right items: Circular Context Meter Gauge & Action Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Circular Context Window Meter Gauge
                            ContextWindowButton(
                                contextUsage = contextUsage,
                                onCompact = onCompact,
                                onOpenDetails = onOpenDetails
                            )

                            // Circular Action Button (Send / Steer / Stop)
                            if (isStreaming) {
                                if (inputText.isNotBlank()) {
                                    // Steer button when user types mid-run
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFF388BFF), Color(0xFF1F6FEB))
                                                )
                                            )
                                            .border(0.75.dp, Color(0x66FFFFFF), CircleShape)
                                            .clickable { onSteer?.invoke() ?: onSend() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Steer agent",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White
                                        )
                                    }
                                } else {
                                    // Stop generation button
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFFFF5252), Color(0xFFD32F2F))
                                                )
                                            )
                                            .border(0.75.dp, Color(0x66FFFFFF), CircleShape)
                                            .clickable { onCancel() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Stop generation",
                                            modifier = Modifier.size(18.dp),
                                            tint = Color.White
                                        )
                                    }
                                }
                            } else {
                                val isEnabled = inputText.isNotBlank() && hasProject
                                val sendShape = CircleShape

                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .shadow(
                                            elevation = if (isEnabled) 8.dp else 0.dp,
                                            shape = sendShape,
                                            ambientColor = Color(0x661F6FEB),
                                            spotColor = Color(0x991F6FEB)
                                        )
                                        .clip(sendShape)
                                        .background(
                                            brush = if (isEnabled) {
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color(0xFF388BFF),
                                                        Color(0xFF1F6FEB)
                                                    )
                                                )
                                            } else {
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color(0x24FFFFFF),
                                                        Color(0x0FFFFFFF)
                                                    )
                                                )
                                            }
                                        )
                                        .border(
                                            width = 0.75.dp,
                                            brush = if (isEnabled) {
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color(0x80FFFFFF),
                                                        Color(0x26FFFFFF)
                                                    )
                                                )
                                            } else {
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color(0x26FFFFFF),
                                                        Color(0x0DFFFFFF)
                                                    )
                                                )
                                            },
                                            shape = sendShape
                                        )
                                        .clickable(enabled = isEnabled) { onSend() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Send message",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isEnabled) Color.White else Color(0xFF6E7681)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
