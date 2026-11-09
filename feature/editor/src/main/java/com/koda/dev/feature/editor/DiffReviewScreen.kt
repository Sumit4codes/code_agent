package com.koda.dev.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koda.dev.core.model.ChangeType
import com.koda.dev.core.model.DiffHunk
import com.koda.dev.core.model.DiffLine
import com.koda.dev.core.model.DiffLineType
import com.koda.dev.core.model.PendingChange
import com.koda.dev.core.ui.glass.GlassDesign
import com.koda.dev.core.ui.glass.springPressClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffReviewScreen(
    pendingChanges: List<PendingChange>,
    onApprove: (PendingChange) -> Unit,
    onReject: (PendingChange) -> Unit,
    onApproveAll: () -> Unit,
    onRejectAll: () -> Unit,
    onNavigateBack: (() -> Unit)? = null
) {
    val barShape = RoundedCornerShape(22.dp)

    Scaffold(
        containerColor = Color(0xFF0B0E14),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = barShape,
                        ambientColor = Color(0x33000000),
                        spotColor = Color(0x660F172A)
                    )
                    .clip(barShape)
                    .background(GlassDesign.liquidGlassBackground())
                    .border(
                        width = 1.dp,
                        brush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.65f, dispersionAlpha = 0.28f),
                        shape = barShape
                    )
            ) {
                // Internal top refractive sheen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(GlassDesign.internalGlassSheen())
                )

                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        if (onNavigateBack != null) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(GlassDesign.microGlassBackground(topAlpha = 0.18f, bottomAlpha = 0.06f))
                                    .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.38f, bottomAlpha = 0.10f), CircleShape)
                                    .springPressClickable(onClick = onNavigateBack),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(18.dp),
                                    tint = Color(0xFFF0F6FC)
                                )
                            }
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(GlassDesign.microGlassBackground(topAlpha = 0.20f, bottomAlpha = 0.08f))
                                    .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.40f, bottomAlpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Difference,
                                    contentDescription = null,
                                    tint = Color(0xFF58A6FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Pending Changes",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFFF0F6FC)
                                )
                                Text(
                                    text = "${pendingChanges.size} ${if (pendingChanges.size == 1) "file" else "files"} to review",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF8B949E)
                                )
                            }
                        }
                    },
                    actions = {
                        if (pendingChanges.isNotEmpty()) {
                            Row(
                                modifier = Modifier.padding(end = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Reject All
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                                        .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.28f, bottomAlpha = 0.08f), RoundedCornerShape(12.dp))
                                        .springPressClickable(onClick = onRejectAll)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "Reject All",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = Color(0xFFF85149)
                                    )
                                }

                                // Apply All
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF238636).copy(alpha = 0.35f))
                                        .border(0.75.dp, Color(0xFF39D353).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                        .springPressClickable(onClick = onApproveAll)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "Apply All",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFF39D353)
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (pendingChanges.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.20f, bottomAlpha = 0.08f))
                            .border(1.dp, GlassDesign.liquidGlassBorder(0.60f, 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF39D353),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        "No pending changes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFF0F6FC)
                    )
                    Text(
                        "All suggested changes have been reviewed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8B949E)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pendingChanges, key = { it.id }) { change ->
                    PendingChangeCard(
                        change = change,
                        onApprove = { onApprove(change) },
                        onReject = { onReject(change) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingChangeCard(
    change: PendingChange,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val cardShape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = cardShape,
                ambientColor = Color(0x33000000),
                spotColor = Color(0x660F172A)
            )
            .clip(cardShape)
            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
            .border(
                width = 0.75.dp,
                brush = GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f),
                shape = cardShape
            )
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: file path & change type badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF58A6FF)
                    )
                    Text(
                        text = change.filePath,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFF0F6FC),
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val (label, labelColor, bgTint) = when (change.changeType) {
                    ChangeType.EDIT -> Triple("EDIT", Color(0xFF79C0FF), Color(0x252F81F7))
                    ChangeType.CREATE -> Triple("CREATE", Color(0xFF39D353), Color(0x25238636))
                    ChangeType.DELETE -> Triple("DELETE", Color(0xFFF85149), Color(0x25DA3633))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgTint)
                        .border(0.5.dp, labelColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = labelColor
                    )
                }
            }

            // Diff container (dark code block)
            val codeShape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(codeShape)
                    .background(Color(0xFF0D1117))
                    .border(0.5.dp, Color(0xFF30363D), codeShape)
                    .padding(8.dp)
            ) {
                if (change.changeType == ChangeType.DELETE) {
                    Text(
                        "This file will be deleted.",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFFF85149),
                        modifier = Modifier.padding(4.dp)
                    )
                } else if (change.changeType == ChangeType.CREATE) {
                    val lines = (change.proposedContent ?: "").lines()
                    Column(modifier = Modifier.fillMaxWidth()) {
                        for ((i, line) in lines.take(50).withIndex()) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = Color(0xFF6E7681))) {
                                        append(String.format("%3d ", i + 1))
                                    }
                                    withStyle(SpanStyle(color = Color(0xFF39D353), fontFamily = FontFamily.Monospace)) {
                                        append("+ $line")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                            )
                        }
                    }
                } else {
                    val hunks = com.koda.dev.core.files.DiffEngine.computeDiff(
                        change.originalContent ?: "",
                        change.proposedContent ?: "",
                        change.filePath
                    )
                    if (hunks.isEmpty()) {
                        Text(
                            "No differences detected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8B949E),
                            modifier = Modifier.padding(4.dp)
                        )
                    } else {
                        DiffViewer(hunks = hunks, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(GlassDesign.microGlassBackground(topAlpha = 0.12f, bottomAlpha = 0.04f))
                        .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.25f, bottomAlpha = 0.08f), RoundedCornerShape(10.dp))
                        .springPressClickable(onClick = onReject)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        "Reject",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFFF85149)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF238636).copy(alpha = 0.35f))
                        .border(0.75.dp, Color(0xFF39D353).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .springPressClickable(onClick = onApprove)
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        "Apply",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF39D353)
                    )
                }
            }
        }
    }
}

@Composable
fun DiffViewer(
    hunks: List<DiffHunk>,
    modifier: Modifier = Modifier
) {
    val additionColor = Color(0xFF238636).copy(alpha = 0.20f)
    val deletionColor = Color(0xFFDA3633).copy(alpha = 0.20f)

    Column(modifier = modifier) {
        for (hunk in hunks) {
            Text(
                "@@ -${hunk.oldStartLine},${hunk.oldLineCount} +${hunk.newStartLine},${hunk.newLineCount} @@",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                color = Color(0xFF79C0FF),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
            )
            for (line in hunk.lines) {
                val bg = when (line.type) {
                    DiffLineType.ADDITION -> additionColor
                    DiffLineType.DELETION -> deletionColor
                    DiffLineType.CONTEXT -> Color.Transparent
                }
                val prefix = when (line.type) {
                    DiffLineType.ADDITION -> "+"
                    DiffLineType.DELETION -> "-"
                    DiffLineType.CONTEXT -> " "
                }
                val textColor = when (line.type) {
                    DiffLineType.ADDITION -> Color(0xFF39D353)
                    DiffLineType.DELETION -> Color(0xFFF85149)
                    DiffLineType.CONTEXT -> Color(0xFFC9D1D9)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        prefix,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp),
                        color = textColor,
                        modifier = Modifier.width(14.dp)
                    )
                    Text(
                        line.content,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp),
                        color = textColor
                    )
                }
            }
        }
    }
}
