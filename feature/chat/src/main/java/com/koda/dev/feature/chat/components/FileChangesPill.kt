package com.koda.dev.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koda.dev.core.model.PendingChange
import com.koda.dev.core.ui.glass.springPressClickable

/**
 * Modern file changes diff pill matching the reference design:
 * "1 file changed  +103 -22  ›   [ Review ]"
 */
@Composable
fun FileChangesPill(
    pendingChanges: List<PendingChange>,
    onOpenFullReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pendingChanges.isEmpty()) return

    val stats = remember(pendingChanges) {
        calculateDiffStats(pendingChanges)
    }

    val fileLabel = remember(stats.filesCount) {
        if (stats.filesCount == 1) "1 file changed" else "${stats.filesCount} files changed"
    }

    val pillShape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(pillShape)
            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
            .border(
                width = 1.dp,
                brush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.50f, dispersionAlpha = 0.20f),
                shape = pillShape
            )
            .springPressClickable { onOpenFullReview() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left info: "1 file changed  +103 -22  ›"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = fileLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFFF0F6FC)
                )

                if (stats.additions > 0) {
                    Text(
                        text = "+${stats.additions}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF3FB950)
                    )
                }

                if (stats.deletions > 0) {
                    Text(
                        text = "-${stats.deletions}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFFF85149)
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF6E7681)
                )
            }

            // Right button: "[ Review ]"
            val reviewBtnShape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .clip(reviewBtnShape)
                    .background(GlassDesign.microGlassBackground(topAlpha = 0.22f, bottomAlpha = 0.08f))
                    .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.40f, bottomAlpha = 0.12f), reviewBtnShape)
                    .springPressClickable { onOpenFullReview() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RateReview,
                        contentDescription = "Review diffs",
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF58A6FF)
                    )
                    Text(
                        text = "Review",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color(0xFFF0F6FC)
                    )
                }
            }
        }
    }
}
