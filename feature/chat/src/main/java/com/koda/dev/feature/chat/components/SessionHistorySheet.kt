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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koda.dev.core.model.ChatSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bottom sheet displaying previous chat sessions for the current project,
 * styled with the Apple-inspired Liquid Glass optical pipeline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistorySheet(
    sessions: List<ChatSession>,
    currentSessionId: String?,
    projectName: String,
    onDismiss: () -> Unit,
    onSelectSession: (ChatSession) -> Unit,
    onNewSession: () -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
    backdropState: BackdropState? = null,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    var sessionToRename by remember { mutableStateOf<ChatSession?>(null) }
    var sessionToDelete by remember { mutableStateOf<ChatSession?>(null) }
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        shape = sheetShape,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 20.dp,
                    shape = sheetShape,
                    ambientColor = Color(0x33000000),
                    spotColor = Color(0x660F172A)
                )
                .liquidGlassBackdrop(
                    backdropState = backdropState,
                    shape = sheetShape,
                    blurRadius = 24.dp,
                    tintColor = Color(0xC410141E),
                    specularBorder = GlassDesign.liquidGlassBorder(
                        topSpecularAlpha = 0.65f,
                        dispersionAlpha = 0.28f
                    ),
                    borderWidth = 1.dp
                )
        ) {
            // Internal Top Refraction Sheen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(GlassDesign.internalGlassSheen())
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Frosted Drag Handle (Micro-glass affordance)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 14.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x40FFFFFF))
                )

                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Frosted History Icon Disc
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.20f, bottomAlpha = 0.08f))
                                .border(
                                    width = 0.75.dp,
                                    brush = GlassDesign.microGlassBorder(topAlpha = 0.40f, bottomAlpha = 0.12f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFF58A6FF)
                            )
                        }
                        Column {
                            Text(
                                text = "Chat History",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFFF0F6FC)
                            )
                            if (projectName.isNotBlank()) {
                                Text(
                                    text = projectName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF8B949E)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                            .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
                            .springPressClickable { onDismiss() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close history",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Start New Chat Action Pill
                val newChatShape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(newChatShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0x332F81F7),
                                    Color(0x22238636)
                                )
                            )
                        )
                        .border(
                            width = 0.75.dp,
                            brush = GlassDesign.liquidGlassBorder(
                                topSpecularAlpha = 0.50f,
                                dispersionAlpha = 0.20f
                            ),
                            shape = newChatShape
                        )
                        .springPressClickable {
                            onNewSession()
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF79C0FF)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start New Chat",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFF0F6FC)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Subtle Optical Edge Separator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0x18FFFFFF),
                                    Color(0x28FFFFFF),
                                    Color(0x18FFFFFF),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Sessions List
                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No previous sessions for this project",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF8B949E),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        text = "${sessions.size} Session${if (sessions.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B949E),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessions, key = { it.id }) { session ->
                            val isSelected = session.id == currentSessionId
                            SessionHistoryItem(
                                session = session,
                                isSelected = isSelected,
                                onSelect = {
                                    onSelectSession(session)
                                    onDismiss()
                                },
                                onRename = { sessionToRename = session },
                                onDelete = { sessionToDelete = session }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    // Rename Dialog
    sessionToRename?.let { session ->
        var renameText by remember(session.title) { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = {
                Text(
                    text = "Rename Chat Session",
                    color = Color(0xFFF0F6FC),
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Session Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenameSession(session.id, renameText.trim())
                            sessionToRename = null
                        }
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text("Save", color = Color(0xFF58A6FF))
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text("Cancel", color = Color(0xFF8B949E))
                }
            },
            containerColor = Color(0xFF161B22),
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Delete Confirmation Dialog
    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Text(
                    text = "Delete Chat Session?",
                    color = Color(0xFFF0F6FC),
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "This will permanently delete this conversation and its message history.",
                    color = Color(0xFF8B949E)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSession(session.id)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel", color = Color(0xFF8B949E))
                }
            },
            containerColor = Color(0xFF161B22),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun SessionHistoryItem(
    session: ChatSession,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val formattedTime = remember(session.updatedAt) { formatSessionTime(session.updatedAt) }
    val itemShape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(itemShape)
            .background(
                if (isSelected) {
                    GlassDesign.microGlassBackground(topAlpha = 0.22f, bottomAlpha = 0.10f)
                } else {
                    GlassDesign.microGlassBackground(topAlpha = 0.12f, bottomAlpha = 0.04f)
                }
            )
            .border(
                width = if (isSelected) 1.dp else 0.75.dp,
                brush = if (isSelected) {
                    Brush.horizontalGradient(
                        listOf(
                            Color(0x9958A6FF),
                            Color(0x442F81F7)
                        )
                    )
                } else {
                    GlassDesign.microGlassBorder(topAlpha = 0.25f, bottomAlpha = 0.08f)
                },
                shape = itemShape
            )
            .springPressClickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) Color(0xFF58A6FF) else Color(0xFF8B949E)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = Color(0xFFF0F6FC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x332F81F7))
                                .border(0.5.dp, Color(0x6658A6FF), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF79C0FF)
                            )
                        }
                    }
                }
                if (formattedTime.isNotBlank()) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B949E)
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Session options",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF8B949E)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = Color(0xFF1C222C),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = Color(0xFFF0F6FC)) },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF8B949E))
                        },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

private fun formatSessionTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val oneMinute = 60 * 1000L
    val oneHour = 60 * oneMinute
    val oneDay = 24 * oneHour
    val oneWeek = 7 * oneDay

    return when {
        diff < oneMinute -> "Just now"
        diff < oneHour -> "${(diff / oneMinute).coerceAtLeast(1)}m ago"
        diff < oneDay -> "${(diff / oneHour).coerceAtLeast(1)}h ago"
        diff < 2 * oneDay -> "Yesterday"
        diff < oneWeek -> "${diff / oneDay}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
