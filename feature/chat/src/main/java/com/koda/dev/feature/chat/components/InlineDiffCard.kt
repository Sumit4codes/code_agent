package com.koda.dev.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.koda.dev.core.model.ChangeStatus
import com.koda.dev.core.model.ChangeType
import com.koda.dev.core.model.DiffHunk
import com.koda.dev.core.model.DiffLineType
import com.koda.dev.core.model.PendingChange
import com.koda.dev.core.ui.theme.JetBrainsMono

/**
 * Rich inline diff card displayed directly inside the chat timeline when the agent
 * creates, writes, or edits a file.
 */
@Composable
fun InlineDiffCard(
    filePath: String,
    changeType: ChangeType,
    originalContent: String?,
    proposedContent: String?,
    pendingChange: PendingChange? = null,
    statusOverride: ChangeStatus? = null,
    onApprove: ((PendingChange) -> Unit)? = null,
    onReject: ((PendingChange) -> Unit)? = null,
    onOpenFullReview: ((PendingChange?) -> Unit)? = null,
    initiallyExpanded: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    val hunks = remember(originalContent, proposedContent, filePath) {
        if (changeType == ChangeType.EDIT && originalContent != null && proposedContent != null) {
            DiffEngine.computeDiff(originalContent, proposedContent, filePath)
        } else {
            emptyList()
        }
    }

    // Compute additions and deletions count
    val (additions, deletions) = remember(hunks, changeType, proposedContent, originalContent) {
        when (changeType) {
            ChangeType.CREATE -> (proposedContent?.lines()?.size ?: 0) to 0
            ChangeType.DELETE -> 0 to (originalContent?.lines()?.size ?: 0)
            ChangeType.EDIT -> {
                var adds = 0
                var dels = 0
                for (hunk in hunks) {
                    for (line in hunk.lines) {
                        when (line.type) {
                            DiffLineType.ADDITION -> adds++
                            DiffLineType.DELETION -> dels++
                            DiffLineType.CONTEXT -> {}
                        }
                    }
                }
                adds to dels
            }
        }
    }

    val (badgeText, badgeColor) = when (changeType) {
        ChangeType.EDIT -> "EDIT" to Color(0xFFD29922)      // Amber/Gold
        ChangeType.CREATE -> "CREATE" to Color(0xFF2EA043)  // Green
        ChangeType.DELETE -> "DELETE" to Color(0xFFF85149)  // Red
    }

    val status = statusOverride ?: pendingChange?.status ?: ChangeStatus.PENDING

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF161B22),
        border = BorderStroke(1.dp, Color(0xFF30363D)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Change Type Badge
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

                // File Path
                Text(
                    text = filePath,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    color = Color(0xFFF0F6FC),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Addition / Deletion Stats
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (additions > 0) {
                        Text(
                            text = "+$additions",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF3FB950)
                        )
                    }
                    if (deletions > 0) {
                        Text(
                            text = "-$deletions",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFFF85149)
                        )
                    }
                }

                // Status Chip (if not pending)
                if (status == ChangeStatus.APPROVED || status == ChangeStatus.APPLIED) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF238636).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Applied",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color(0xFF3FB950)
                        )
                    }
                } else if (status == ChangeStatus.REJECTED) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFDA3633).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Rejected",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color(0xFFF85149)
                        )
                    }
                }

                // Open in full bottom sheet button
                if (onOpenFullReview != null) {
                    IconButton(
                        onClick = { onOpenFullReview(pendingChange) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Expand diff in sheet",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF8B949E)
                        )
                    }
                }

                // Expand/Collapse Chevron
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse diff" else "Expand diff",
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF8B949E)
                )
            }

            // Expanded Diff Viewport
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF21262D))
                    Spacer(modifier = Modifier.height(6.dp))

                    // Diff Body
                    when (changeType) {
                        ChangeType.DELETE -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF341416), RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "This file will be deleted.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = JetBrainsMono,
                                        color = Color(0xFFF85149)
                                    )
                                )
                            }
                        }
                        ChangeType.CREATE -> {
                            val lines = remember(proposedContent) { proposedContent?.lines() ?: emptyList() }
                            InlineHunkLines(
                                lines = lines.mapIndexed { idx, line ->
                                    com.koda.dev.core.model.DiffLine(
                                        type = DiffLineType.ADDITION,
                                        content = line,
                                        oldLineNumber = null,
                                        newLineNumber = idx + 1
                                    )
                                },
                                maxLinesToShow = 35
                            )
                        }
                        ChangeType.EDIT -> {
                            if (hunks.isEmpty()) {
                                Text(
                                    text = "No differences detected.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = JetBrainsMono,
                                        color = Color(0xFF8B949E)
                                    ),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (hunk in hunks) {
                                        InlineHunkSection(hunk = hunk)
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Action Bar (if still pending and handlers provided)
                    if (status == ChangeStatus.PENDING && pendingChange != null && (onApprove != null || onReject != null)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFF21262D))
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onReject != null) {
                                OutlinedButton(
                                    onClick = { onReject(pendingChange) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFFF85149)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFF85149).copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reject", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            if (onApprove != null) {
                                FilledTonalButton(
                                    onClick = { onApprove(pendingChange) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color(0xFF238636),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Apply", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun InlineHunkSection(
    hunk: DiffHunk,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Hunk Header Range
        Surface(
            color = Color(0xFF1F242C),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "@@ -${hunk.oldStartLine},${hunk.oldLineCount} +${hunk.newStartLine},${hunk.newLineCount} @@",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color(0xFF79C0FF),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        // Hunk Lines
        InlineHunkLines(lines = hunk.lines, maxLinesToShow = 30)
    }
}

@Composable
private fun InlineHunkLines(
    lines: List<com.koda.dev.core.model.DiffLine>,
    maxLinesToShow: Int = 30,
    modifier: Modifier = Modifier
) {
    var expandedAll by remember { mutableStateOf(lines.size <= maxLinesToShow) }
    val displayedLines = if (expandedAll) lines else lines.take(maxLinesToShow)

    Surface(
        color = Color(0xFF0D1117),
        shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
        border = BorderStroke(1.dp, Color(0xFF21262D)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            for (line in displayedLines) {
                val (bgColor, textColor, prefix) = when (line.type) {
                    DiffLineType.ADDITION -> Triple(Color(0x1F2EA043), Color(0xFF7EE787), "+")
                    DiffLineType.DELETION -> Triple(Color(0x2EFA4549), Color(0xFFFF7B72), "-")
                    DiffLineType.CONTEXT -> Triple(Color.Transparent, Color(0xFFC9D1D9), " ")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Line numbers
                    val lineNum = (line.newLineNumber ?: line.oldLineNumber)?.toString() ?: ""
                    Text(
                        text = lineNum.padStart(3),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMono,
                            fontSize = 10.sp
                        ),
                        color = Color(0xFF484F58),
                        modifier = Modifier.width(26.dp)
                    )

                    // Diff Marker
                    Text(
                        text = prefix,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = textColor,
                        modifier = Modifier.width(14.dp)
                    )

                    // Line Content
                    Text(
                        text = line.content,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp
                        ),
                        color = textColor
                    )
                }
            }

            if (!expandedAll && lines.size > maxLinesToShow) {
                val remaining = lines.size - maxLinesToShow
                Text(
                    text = "… Show $remaining more lines",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    ),
                    color = Color(0xFF58A6FF),
                    modifier = Modifier
                        .clickable { expandedAll = true }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}
