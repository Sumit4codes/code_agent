package com.koda.dev.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EditorTabBar(
    tabs: List<EditorTab>,
    activeTabUri: android.net.Uri?,
    onSelectTab: (EditorTab) -> Unit,
    onCloseTab: (EditorTab) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    Surface(
        color = Color(0xFF161B22),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEach { tab ->
                    val isSelected = tab.uri == activeTabUri

                    Surface(
                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                        color = if (isSelected) Color(0xFF0D1117) else Color(0xFF21262D),
                        border = if (isSelected) BorderStroke(1.dp, Color(0xFF388BFD)) else null,
                        modifier = Modifier
                            .height(32.dp)
                            .clickable { onSelectTab(tab) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                        .background(Color(0xFF58A6FF), CircleShape)
                                )
                            }

                            // Close button
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onCloseTab(tab) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close ${tab.name}",
                                    tint = if (isSelected) Color(0xFFC9D1D9) else Color(0xFF8B949E),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF30363D))
        }
    }
}
