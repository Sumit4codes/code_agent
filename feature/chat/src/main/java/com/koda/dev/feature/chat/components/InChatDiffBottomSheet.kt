package com.koda.dev.feature.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koda.dev.core.files.DiffEngine
import com.koda.dev.core.model.ChangeType
import com.koda.dev.core.model.DiffHunk
import com.koda.dev.core.model.DiffLineType
import com.koda.dev.core.model.PendingChange
import com.koda.dev.core.ui.theme.JetBrainsMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InChatDiffBottomSheet(
    pendingChanges: List<PendingChange>,
    initialSelectedChange: PendingChange? = null,
    onApprove: (PendingChange) -> Unit,
    onReject: (PendingChange) -> Unit,
    onApproveAll: () -> Unit,
    onRejectAll: () -> Unit,
    onDismiss: () -> Unit
) {
    if (pendingChanges.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedChangeId by remember(pendingChanges) {
        val initialId = initialSelectedChange?.id ?: pendingChanges.firstOrNull()?.id ?: ""
        mutableStateOf(if (pendingChanges.any { it.id == initialId }) initialId else pendingChanges.first().id)
    }

    val currentChange = pendingChanges.find { it.id == selectedChangeId } ?: pendingChanges.first()
    val currentIndex = pendingChanges.indexOf(currentChange)

    val hunks = remember(currentChange) {
        if (currentChange.changeType == ChangeType.EDIT) {
            DiffEngine.computeDiff(
                currentChange.originalContent ?: "",
                currentChange.proposedContent ?: "",
                currentChange.filePath
            )
        } else {
            emptyList()
        }
    }

    val (addedLines, deletedLines) = remember(currentChange, hunks) {
        when (currentChange.changeType) {
            ChangeType.CREATE -> {
                val lines = (currentChange.proposedContent ?: "").lines().filter { it.isNotEmpty() }.size
                lines to 0
            }
            ChangeType.DELETE -> {
                val lines = (currentChange.originalContent ?: "").lines().filter { it.isNotEmpty() }.size
                0 to lines
            }
            ChangeType.EDIT -> {
                var adds = 0
                var dels = 0
                for (hunk in hunks) {
                    for (line in hunk.lines) {
                        if (line.type == DiffLineType.ADDITION) adds++
                        if (line.type == DiffLineType.DELETION) dels++
                    }
                }
                adds to dels
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0D1117),
        contentColor = Color(0xFFF0F6FC),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color(0xFF30363D)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Proposed Changes",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF0F6FC)
                            )
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF21262D)
                        ) {
                            Text(
                                text = "${currentIndex + 1} of ${pendingChanges.size}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMono,
                                    fontSize = 11.sp
                                ),
                                color = Color(0xFF8B949E)
                            )
                        }
                    }
                    Text(
                        text = if (pendingChanges.size > 1) {
                            "${pendingChanges.size} files ready to review"
                        } else {
                            "Review diff before applying"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8B949E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Batch controls / Close
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (pendingChanges.size > 1) {
                        OutlinedButton(
                            onClick = {
                                onRejectAll()
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF85149)),
                            border = BorderStroke(1.dp, Color(0xFFF85149).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reject All", fontSize = 11.sp)
                        }

                        FilledTonalButton(
                            onClick = {
                                onApproveAll()
                                onDismiss()
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF238636),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Apply All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Sheet",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF30363D))

            // Multi-File Horizontal Carousel
            if (pendingChanges.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pendingChanges, key = { it.id }) { change ->
                        val isSelected = change.id == currentChange.id
                        val (chipBadge, chipColor) = when (change.changeType) {
                            ChangeType.EDIT -> "EDIT" to Color(0xFFD29922)
                            ChangeType.CREATE -> "NEW" to Color(0xFF2EA043)
                            ChangeType.DELETE -> "DEL" to Color(0xFFF85149)
                        }
                        val fileName = change.filePath.substringAfterLast('/')

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF1F242C) else Color(0xFF161B22),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF58A6FF) else Color(0xFF30363D)
                            ),
                            modifier = Modifier.clickable { selectedChangeId = change.id }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = chipColor.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = chipBadge,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMono,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        ),
                                        color = chipColor
                                    )
                                }
                                Text(
                                    text = fileName,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = JetBrainsMono,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isSelected) Color(0xFFF0F6FC) else Color(0xFF8B949E)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF21262D))
            }

            // Current File Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currentChange.filePath,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    color = Color(0xFF79C0FF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (addedLines > 0 || deletedLines > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (addedLines > 0) {
                                Text(
                                    text = "+$addedLines",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = Color(0xFF3FB950)
                                )
                            }
                            if (deletedLines > 0) {
                                Text(
                                    text = "-$deletedLines",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = Color(0xFFF85149)
                                )
                            }
                        }
                    }

                    val (badgeText, badgeColor) = when (currentChange.changeType) {
                        ChangeType.EDIT -> "MODIFIED" to Color(0xFFD29922)
                        ChangeType.CREATE -> "NEW FILE" to Color(0xFF2EA043)
                        ChangeType.DELETE -> "DELETED" to Color(0xFFF85149)
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = badgeColor
                        )
                    }
                }
            }

            // Diff Content Viewer (Full Vertical + Horizontal Scroll)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117))
            ) {
                when (currentChange.changeType) {
                    ChangeType.DELETE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "This file will be completely deleted.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = JetBrainsMono,
                                    color = Color(0xFFF85149)
                                )
                            )
                        }
                    }
                    ChangeType.CREATE -> {
                        val lines = (currentChange.proposedContent ?: "").lines()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 8.dp)
                        ) {
                            for ((i, line) in lines.withIndex()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0x1F2EA043))
                                        .padding(horizontal = 8.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${i + 1}".padStart(4),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = JetBrainsMono,
                                            fontSize = 11.sp
                                        ),
                                        color = Color(0xFF6E7681),
                                        modifier = Modifier.width(36.dp)
                                    )
                                    Text(
                                        text = "+",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = JetBrainsMono,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = Color(0xFF7EE787),
                                        modifier = Modifier.width(16.dp)
                                    )
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = JetBrainsMono,
                                            fontSize = 11.sp
                                        ),
                                        color = Color(0xFF7EE787),
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                    ChangeType.EDIT -> {
                        if (hunks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No differences detected.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = JetBrainsMono,
                                        color = Color(0xFF8B949E)
                                    )
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 8.dp)
                            ) {
                                for (hunk in hunks) {
                                    Text(
                                        text = "@@ -${hunk.oldStartLine},${hunk.oldLineCount} +${hunk.newStartLine},${hunk.newLineCount} @@",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMono,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color(0xFF79C0FF),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF161B22))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    for (line in hunk.lines) {
                                        val (bg, textColor, prefix) = when (line.type) {
                                            DiffLineType.ADDITION -> Triple(Color(0x1F2EA043), Color(0xFF7EE787), "+")
                                            DiffLineType.DELETION -> Triple(Color(0x2EFA4549), Color(0xFFFF7B72), "-")
                                            DiffLineType.CONTEXT -> Triple(Color.Transparent, Color(0xFFC9D1D9), " ")
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(bg)
                                                .padding(horizontal = 8.dp, vertical = 1.dp)
                                        ) {
                                            val oldNum = line.oldLineNumber?.toString()?.padStart(4) ?: "    "
                                            val newNum = line.newLineNumber?.toString()?.padStart(4) ?: "    "
                                            Text(
                                                text = "$oldNum $newNum",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = JetBrainsMono,
                                                    fontSize = 10.sp
                                                ),
                                                color = Color(0xFF6E7681),
                                                modifier = Modifier.width(64.dp)
                                            )
                                            Text(
                                                text = prefix,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = JetBrainsMono,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                ),
                                                color = textColor,
                                                modifier = Modifier.width(16.dp)
                                            )
                                            Text(
                                                text = line.content,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = JetBrainsMono,
                                                    fontSize = 11.sp
                                                ),
                                                color = textColor,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF30363D))

            // Sticky Bottom Action Bar for Current File
            Surface(
                color = Color(0xFF161B22),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pendingChanges.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentIndex > 0) {
                                        selectedChangeId = pendingChanges[currentIndex - 1].id
                                    }
                                },
                                enabled = currentIndex > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous file",
                                    tint = if (currentIndex > 0) Color(0xFFC9D1D9) else Color(0xFF484F58),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Text(
                                text = "${currentIndex + 1} / ${pendingChanges.size}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFFC9D1D9)
                            )

                            IconButton(
                                onClick = {
                                    if (currentIndex < pendingChanges.size - 1) {
                                        selectedChangeId = pendingChanges[currentIndex + 1].id
                                    }
                                },
                                enabled = currentIndex < pendingChanges.size - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next file",
                                    tint = if (currentIndex < pendingChanges.size - 1) Color(0xFFC9D1D9) else Color(0xFF484F58),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "1 change pending",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMono,
                                fontSize = 12.sp
                            ),
                            color = Color(0xFF8B949E)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                onReject(currentChange)
                                if (pendingChanges.size <= 1) {
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF7B72)),
                            border = BorderStroke(1.dp, Color(0xFFF85149).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reject", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                        }

                        FilledTonalButton(
                            onClick = {
                                onApprove(currentChange)
                                if (pendingChanges.size <= 1) {
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF238636),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Apply", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
