package com.koda.dev.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.koda.dev.core.files.FileNode
import com.koda.dev.core.ui.glass.GlassDesign
import com.koda.dev.core.ui.glass.springPressClickable

@Composable
fun CreateFileDialog(
    state: CreateFileDialogState,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    if (!state.isOpen) return
    var fileName by remember { mutableStateOf(state.initialName) }
    val extensions = listOf(".kt", ".py", ".js", ".ts", ".json", ".md", ".sh", ".c")
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
            // Internal top refraction sheen
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with frosted avatar disc + title + close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                                contentDescription = null,
                                tint = Color(0xFF58A6FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Create New File",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF0F6FC)
                            )
                            Text(
                                text = "Enter file name with extension",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B949E)
                            )
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

                // Text Input
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name", color = Color(0xFF8B949E)) },
                    placeholder = { Text("e.g. Main.kt, utils.py", color = Color(0xFF484F58)) },
                    singleLine = true,
                    isError = state.error != null,
                    supportingText = state.error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0x33000000),
                        unfocusedContainerColor = Color(0x22000000),
                        focusedBorderColor = Color(0xFF58A6FF),
                        unfocusedBorderColor = Color(0x26FFFFFF),
                        focusedTextColor = Color(0xFFF0F6FC),
                        unfocusedTextColor = Color(0xFFF0F6FC)
                    )
                )

                // Quick Extension Helper Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    extensions.forEach { ext ->
                        val chipShape = RoundedCornerShape(8.dp)
                        Box(
                            modifier = Modifier
                                .clip(chipShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f), chipShape)
                            .springPressClickable {
                                val base = fileName.substringBeforeLast('.')
                                fileName = "$base$ext"
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ext,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF58A6FF)
                            )
                        }
                    }
                }

                // Action Buttons (Cancel + Create)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val cancelShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(cancelShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.10f), cancelShape)
                            .springPressClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFC9D1D9)
                        )
                    }

                    val createShape = RoundedCornerShape(12.dp)
                    val isValid = fileName.isNotBlank()
                    val createBg = if (isValid) {
                        Brush.horizontalGradient(listOf(Color(0xFF1F6FEB), Color(0xFF388BFD)))
                    } else {
                        Brush.horizontalGradient(listOf(Color(0x331F6FEB), Color(0x33388BFD)))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(createShape)
                            .background(createBg)
                            .border(
                                width = 1.dp,
                                brush = if (isValid) GlassDesign.liquidGlassBorder(0.70f, 0.35f) else GlassDesign.microGlassBorder(0.20f, 0.06f),
                                shape = createShape
                            )
                            .springPressClickable(enabled = isValid) { onCreate(fileName) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isValid) Color.White else Color(0x66FFFFFF)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreateFolderDialog(
    state: CreateFolderDialogState,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    if (!state.isOpen) return
    var folderName by remember { mutableStateOf(state.initialName) }
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
            // Internal top refraction sheen
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with frosted avatar disc + title + close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = Color(0xFF58A6FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Create New Folder",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF0F6FC)
                            )
                            Text(
                                text = "Enter directory name",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B949E)
                            )
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

                // Text Input
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name", color = Color(0xFF8B949E)) },
                    placeholder = { Text("e.g. src, models, scripts", color = Color(0xFF484F58)) },
                    singleLine = true,
                    isError = state.error != null,
                    supportingText = state.error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0x33000000),
                        unfocusedContainerColor = Color(0x22000000),
                        focusedBorderColor = Color(0xFF58A6FF),
                        unfocusedBorderColor = Color(0x26FFFFFF),
                        focusedTextColor = Color(0xFFF0F6FC),
                        unfocusedTextColor = Color(0xFFF0F6FC)
                    )
                )

                // Action Buttons (Cancel + Create)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val cancelShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(cancelShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.10f), cancelShape)
                            .springPressClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFC9D1D9)
                        )
                    }

                    val createShape = RoundedCornerShape(12.dp)
                    val isValid = folderName.isNotBlank()
                    val createBg = if (isValid) {
                        Brush.horizontalGradient(listOf(Color(0xFF1F6FEB), Color(0xFF388BFD)))
                    } else {
                        Brush.horizontalGradient(listOf(Color(0x331F6FEB), Color(0x33388BFD)))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(createShape)
                            .background(createBg)
                            .border(
                                width = 1.dp,
                                brush = if (isValid) GlassDesign.liquidGlassBorder(0.70f, 0.35f) else GlassDesign.microGlassBorder(0.20f, 0.06f),
                                shape = createShape
                            )
                            .springPressClickable(enabled = isValid) { onCreate(folderName) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isValid) Color.White else Color(0x66FFFFFF)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RenameDialog(
    state: RenameDialogState,
    onDismiss: () -> Unit,
    onRename: (FileNode, String) -> Unit
) {
    val node = state.targetNode ?: return
    if (!state.isOpen) return
    var newName by remember { mutableStateOf(state.initialName) }
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
            // Internal top refraction sheen
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with frosted avatar disc + title + close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                imageVector = Icons.Default.DriveFileRenameOutline,
                                contentDescription = null,
                                tint = Color(0xFF58A6FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = if (node.isDirectory) "Rename Folder" else "Rename File",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF0F6FC)
                            )
                            Text(
                                text = "Current: ${node.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B949E),
                                maxLines = 1
                            )
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

                // Text Input
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name", color = Color(0xFF8B949E)) },
                    singleLine = true,
                    isError = state.error != null,
                    supportingText = state.error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0x33000000),
                        unfocusedContainerColor = Color(0x22000000),
                        focusedBorderColor = Color(0xFF58A6FF),
                        unfocusedBorderColor = Color(0x26FFFFFF),
                        focusedTextColor = Color(0xFFF0F6FC),
                        unfocusedTextColor = Color(0xFFF0F6FC)
                    )
                )

                // Action Buttons (Cancel + Rename)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val cancelShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(cancelShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.10f), cancelShape)
                            .springPressClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFC9D1D9)
                        )
                    }

                    val renameShape = RoundedCornerShape(12.dp)
                    val isValid = newName.isNotBlank() && newName != node.name
                    val renameBg = if (isValid) {
                        Brush.horizontalGradient(listOf(Color(0xFF1F6FEB), Color(0xFF388BFD)))
                    } else {
                        Brush.horizontalGradient(listOf(Color(0x331F6FEB), Color(0x33388BFD)))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(renameShape)
                            .background(renameBg)
                            .border(
                                width = 1.dp,
                                brush = if (isValid) GlassDesign.liquidGlassBorder(0.70f, 0.35f) else GlassDesign.microGlassBorder(0.20f, 0.06f),
                                shape = renameShape
                            )
                            .springPressClickable(enabled = isValid) { onRename(node, newName) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Rename",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isValid) Color.White else Color(0x66FFFFFF)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    state: DeleteDialogState,
    onDismiss: () -> Unit,
    onConfirmDelete: (FileNode) -> Unit
) {
    val node = state.targetNode ?: return
    if (!state.isOpen) return
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
            // Internal top refraction sheen
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with frosted danger disc + title + close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x26F85149))
                                .border(0.75.dp, GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.60f, dispersionAlpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFFFF7B72),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = if (node.isDirectory) "Delete Folder?" else "Delete File?",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFFF7B72)
                            )
                            Text(
                                text = "Action cannot be undone",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B949E)
                            )
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

                // Warning Text in frosted container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1AF85149))
                        .border(0.75.dp, Color(0x33F85149), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = if (node.isDirectory) {
                            "Are you sure you want to permanently delete \"${node.name}\" and all of its contents?"
                        } else {
                            "Are you sure you want to permanently delete \"${node.name}\"?"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFF0F6FC),
                        lineHeight = 20.sp
                    )
                }

                // Action Buttons (Cancel + Delete)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val cancelShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(cancelShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.10f), cancelShape)
                            .springPressClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFC9D1D9)
                        )
                    }

                    val deleteShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(deleteShape)
                            .background(Brush.horizontalGradient(listOf(Color(0xFFDA3633), Color(0xFFF85149))))
                            .border(1.dp, GlassDesign.liquidGlassBorder(0.70f, 0.35f), deleteShape)
                            .springPressClickable { onConfirmDelete(node) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UnsavedChangesDialog(
    state: UnsavedTabDialogState,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
    onSaveAndClose: () -> Unit
) {
    val tab = state.tabToClose ?: return
    if (!state.isOpen) return
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
            // Internal top refraction sheen
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with frosted warning disc + title + close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x26E95420))
                                .border(0.75.dp, GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.60f, dispersionAlpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Unsaved Changes",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF0F6FC)
                            )
                            Text(
                                text = "Changes will be lost if not saved",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B949E)
                            )
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

                // Description in frosted container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassDesign.microGlassBackground(topAlpha = 0.10f, bottomAlpha = 0.04f))
                        .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.25f, bottomAlpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Do you want to save changes to \"${tab.name}\" before closing?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFF0F6FC),
                        lineHeight = 20.sp
                    )
                }

                // Action Buttons (Discard + Cancel + Save)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val discardShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(discardShape)
                            .background(Color(0x22F85149))
                            .border(0.75.dp, Color(0x44F85149), discardShape)
                            .springPressClickable(onClick = onDiscard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Discard",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFFF7B72)
                        )
                    }

                    val cancelShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(cancelShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.10f), cancelShape)
                            .springPressClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFC9D1D9)
                        )
                    }

                    val saveShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp)
                            .clip(saveShape)
                            .background(Brush.horizontalGradient(listOf(Color(0xFF238636), Color(0xFF2EA043))))
                            .border(1.dp, GlassDesign.liquidGlassBorder(0.70f, 0.35f), saveShape)
                            .springPressClickable(onClick = onSaveAndClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
