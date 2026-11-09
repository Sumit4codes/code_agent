package com.koda.dev.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koda.dev.core.ui.glass.GlassDesign
import com.koda.dev.core.ui.glass.springPressClickable

@Composable
fun EditorTabBar(
    tabs: List<EditorTab>,
    activeTabUri: android.net.Uri?,
    onSelectTab: (EditorTab) -> Unit,
    onCloseTab: (EditorTab) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(GlassDesign.liquidGlassBackground(topAlpha = 0.92f, midAlpha = 0.88f, bottomAlpha = 0.82f))
            .border(
                width = 0.5.dp,
                brush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.35f, dispersionAlpha = 0.15f),
                shape = RectangleShape
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEach { tab ->
                val isSelected = tab.uri == activeTabUri
                val tabShape = RoundedCornerShape(10.dp)

                val tabBackground = if (isSelected) {
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1F6FEB).copy(alpha = 0.35f),
                            Color(0xFF0F172A).copy(alpha = 0.65f)
                        )
                    )
                } else {
                    GlassDesign.microGlassBackground(topAlpha = 0.10f, bottomAlpha = 0.04f)
                }

                val tabBorder = if (isSelected) {
                    GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.65f, dispersionAlpha = 0.30f)
                } else {
                    GlassDesign.microGlassBorder(topAlpha = 0.25f, bottomAlpha = 0.08f)
                }

                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(tabShape)
                        .background(tabBackground)
                        .border(1.dp, tabBorder, tabShape)
                        .springPressClickable { onSelectTab(tab) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // File title
                        Text(
                            text = tab.name,
                            color = if (isSelected) Color(0xFFF0F6FC) else Color(0xFF8B949E),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Unsaved changes indicator
                        if (tab.isDirty) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF39D353), CircleShape)
                            )
                        }

                        // Close button
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.15f, bottomAlpha = 0.05f))
                                .border(0.5.dp, GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.08f), CircleShape)
                                .springPressClickable { onCloseTab(tab) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close ${tab.name}",
                                tint = if (isSelected) Color(0xFFC9D1D9) else Color(0xFF8B949E),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
