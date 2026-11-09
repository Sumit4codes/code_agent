package com.koda.dev.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.koda.dev.core.terminal.TaskStatusInfo
import com.koda.dev.core.terminal.TaskSummary
import com.koda.dev.core.ui.glass.springPressClickable

@Composable
fun ActiveTasksStrip(
    tasks: List<TaskSummary>,
    onKillTask: (String) -> Unit,
    onSendInput: ((String, String) -> Unit)? = null,
    onGetTaskDetails: ((String) -> TaskStatusInfo?)? = null,
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) return

    var selectedTask by remember { mutableStateOf<TaskSummary?>(null) }
    val hasStalled = remember(tasks) { tasks.any { it.isStalled } }

    val stripShape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .shadow(
                elevation = 8.dp,
                shape = stripShape,
                ambientColor = Color(0x30000000),
                spotColor = Color(0x500F172A)
            )
            .clip(stripShape)
            .background(GlassDesign.microGlassBackground(topAlpha = 0.18f, bottomAlpha = 0.08f))
            .border(
                width = 1.dp,
                brush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.50f, dispersionAlpha = 0.20f),
                shape = stripShape
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "BACKGROUND TASKS (${tasks.size}/5)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (hasStalled) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "STALLED TASK DETECTED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Chips list
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(tasks, key = { it.taskId }) { task ->
                    TaskChip(
                        task = task,
                        onClick = { selectedTask = task },
                        onKill = { onKillTask(task.taskId) }
                    )
                }
            }
        }
    }

    selectedTask?.let { task ->
        TaskDetailsDialog(
            task = task,
            details = onGetTaskDetails?.invoke(task.taskId),
            onKill = {
                onKillTask(task.taskId)
                selectedTask = null
            },
            onSendInput = { input ->
                onSendInput?.invoke(task.taskId, input)
            },
            onDismiss = { selectedTask = null }
        )
    }
}

@Composable
private fun TaskChip(
    task: TaskSummary,
    onClick: () -> Unit,
    onKill: () -> Unit
) {
    val isStalled = task.isStalled
    val chipShape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .clip(chipShape)
            .then(
                if (isStalled) {
                    Modifier.background(Color(0x22F85149))
                } else {
                    Modifier.background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                }
            )
            .then(
                if (isStalled) {
                    Modifier.border(0.75.dp, GlassDesign.liquidGlassBorder(0.60f, 0.25f), chipShape)
                } else {
                    Modifier.border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.08f), chipShape)
                }
            )
            .springPressClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isStalled) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Stalled",
                    tint = Color(0xFFFF7B72),
                    modifier = Modifier.size(12.dp)
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = Color(0xFF58A6FF)
                )
            }

            Text(
                text = task.taskId,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isStalled) Color(0xFFFF7B72) else Color(0xFFF0F6FC)
            )

            Text(
                text = task.command.take(20) + if (task.command.length > 20) "…" else "",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFF8B949E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isStalled) Color(0x33F85149) else Color(0x22FFFFFF))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isStalled) "${task.silenceSeconds}s silent" else "${task.runtimeSeconds}s",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isStalled) Color(0xFFFF7B72) else Color(0xFF8B949E)
                )
            }

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .springPressClickable(onClick = onKill),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Kill Task",
                    tint = if (isStalled) Color(0xFFFF7B72) else Color(0xFF8B949E),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun TaskDetailsDialog(
    task: TaskSummary,
    details: TaskStatusInfo?,
    onKill: () -> Unit,
    onSendInput: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputMessage by remember { mutableStateOf("") }
    val output = details?.recentOutput ?: "(No output captured yet)"
    val dialogShape = RoundedCornerShape(24.dp)

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
                    ambientColor = Color(0x40000000),
                    spotColor = Color(0x800F172A)
                )
                .clip(dialogShape)
                .background(GlassDesign.liquidGlassBackground())
                .border(
                    width = 1.dp,
                    brush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.70f, dispersionAlpha = 0.35f),
                    shape = dialogShape
                )
        ) {
            // Internal Top Refraction Sheen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(GlassDesign.internalGlassSheen())
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.22f, bottomAlpha = 0.08f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.45f, bottomAlpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = Color(0xFF58A6FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "${task.taskId} Details",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF0F6FC)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (task.isStalled) Color(0x33F85149) else Color(0x33238636))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (task.isStalled) "STALLED" else "RUNNING",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (task.isStalled) Color(0xFFFF7B72) else Color(0xFF39D353)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f), CircleShape)
                            .springPressClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Command display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x22000000))
                        .border(0.75.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "$ ${task.command}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF58A6FF)
                    )
                }

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Runtime: ${task.runtimeSeconds}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B949E)
                    )
                    Text(
                        text = "Silence: ${task.silenceSeconds}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (task.isStalled) Color(0xFFFF7B72) else Color(0xFF8B949E)
                    )
                }

                // Output window
                Text(
                    text = "Recent Terminal Output:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFC9D1D9)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33000000))
                        .border(0.75.dp, Color(0x26FFFFFF), RoundedCornerShape(10.dp))
                ) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = output,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFFF0F6FC),
                        modifier = Modifier
                            .padding(10.dp)
                            .verticalScroll(scrollState)
                    )
                }

                // Send input to stdin
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputMessage,
                        onValueChange = { inputMessage = it },
                        placeholder = { Text("Send stdin input…", fontSize = 11.sp, color = Color(0xFF6E7681)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0x22000000),
                            unfocusedContainerColor = Color(0x18000000),
                            focusedBorderColor = Color(0xFF58A6FF),
                            unfocusedBorderColor = Color(0x20FFFFFF),
                            focusedTextColor = Color(0xFFF0F6FC),
                            unfocusedTextColor = Color(0xFFF0F6FC)
                        )
                    )
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.18f, bottomAlpha = 0.06f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f), CircleShape)
                            .springPressClickable(enabled = inputMessage.isNotEmpty()) {
                                onSendInput(inputMessage + "\n")
                                inputMessage = ""
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send stdin",
                            tint = if (inputMessage.isNotEmpty()) Color(0xFF58A6FF) else Color(0xFF6E7681),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Action Buttons (Close + Kill Task)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val closeShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(closeShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.10f), closeShape)
                            .springPressClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Close",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFC9D1D9)
                        )
                    }

                    val killShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(killShape)
                            .background(Brush.horizontalGradient(listOf(Color(0xFFDA3633), Color(0xFFF85149))))
                            .border(1.dp, GlassDesign.liquidGlassBorder(0.70f, 0.35f), killShape)
                            .springPressClickable { onKill() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Kill Task",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
