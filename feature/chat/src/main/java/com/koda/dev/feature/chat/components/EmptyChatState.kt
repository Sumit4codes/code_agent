package com.koda.dev.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.koda.dev.core.ui.glass.springPressClickable

@Composable
fun EmptyChatState(
    hasProject: Boolean,
    projectName: String,
    modelName: String,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!hasProject) {
            NoProjectState()
        } else {
            ProjectReadyState(
                projectName = projectName,
                modelName = modelName,
                onSuggestionClick = onSuggestionClick
            )
        }
    }
}

@Composable
private fun NoProjectState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GlassDesign.microGlassBackground(topAlpha = 0.20f, bottomAlpha = 0.08f))
                .border(1.dp, GlassDesign.liquidGlassBorder(0.60f, 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = Color(0xFF58A6FF)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "No Project Selected",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Open a project from the Projects tab to allow Koda Dev to inspect files, search code, and propose edits.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun ProjectReadyState(
    projectName: String,
    modelName: String,
    onSuggestionClick: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Icon header
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(GlassDesign.microGlassBackground(topAlpha = 0.24f, bottomAlpha = 0.08f))
                .border(1.dp, GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.70f, dispersionAlpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = Color(0xFF58A6FF)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Koda Dev Ready",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Project & model badge row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val badgeShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .clip(badgeShape)
                    .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                    .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f), badgeShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF58A6FF)
                    )
                    Text(
                        text = projectName,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFFF0F6FC)
                    )
                }
            }

            if (modelName.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(badgeShape)
                        .background(GlassDesign.microGlassBackground(topAlpha = 0.14f, bottomAlpha = 0.05f))
                        .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.10f), badgeShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF8B949E)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Suggestions
        Text(
            text = "Suggested Prompts",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StarterCard(
                icon = Icons.Default.Search,
                title = "Explain project structure",
                subtitle = "Overview of directories, modules, and architecture",
                prompt = "Give an overview of this project's directory structure and key modules.",
                onClick = onSuggestionClick
            )
            StarterCard(
                icon = Icons.Default.Lightbulb,
                title = "Find TODOs and pending tasks",
                subtitle = "Scan the codebase for TODO, FIXME, or placeholders",
                prompt = "Search for TODO and FIXME comments across the project and list them.",
                onClick = onSuggestionClick
            )
            StarterCard(
                icon = Icons.Default.BugReport,
                title = "Inspect for potential issues",
                subtitle = "Review error handling and edge cases in the code",
                prompt = "Analyze the codebase for potential bugs, missing error handling, or performance issues.",
                onClick = onSuggestionClick
            )
            StarterCard(
                icon = Icons.Default.Speed,
                title = "Suggest test improvements",
                subtitle = "Identify areas needing unit or integration tests",
                prompt = "Review the existing tests and suggest new test cases to improve coverage.",
                onClick = onSuggestionClick
            )
        }
    }
}

@Composable
private fun StarterCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    prompt: String,
    onClick: (String) -> Unit
) {
    val cardShape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = cardShape,
                ambientColor = Color(0x20000000),
                spotColor = Color(0x400F172A)
            )
            .clip(cardShape)
            .background(GlassDesign.microGlassBackground(topAlpha = 0.15f, bottomAlpha = 0.06f))
            .border(
                width = 1.dp,
                brush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.50f, dispersionAlpha = 0.20f),
                shape = cardShape
            )
            .springPressClickable { onClick(prompt) }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val discShape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(discShape)
                    .background(GlassDesign.microGlassBackground(topAlpha = 0.20f, bottomAlpha = 0.08f))
                    .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.40f, bottomAlpha = 0.12f), discShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF58A6FF)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFF0F6FC)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8B949E)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF58A6FF).copy(alpha = 0.8f)
            )
        }
    }
}
