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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.koda.dev.core.model.ProviderConfig

/**
 * Bottom sheet to choose between all registered AI models/providers from settings,
 * styled with Liquid Glass effect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectorSheet(
    providers: List<ProviderConfig>,
    activeProviderId: String?,
    onSelectProvider: (String) -> Unit,
    onDismiss: () -> Unit,
    backdropState: BackdropState? = null,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                        .size(width = 38.dp, height = 4.5.dp)
                        .clip(CircleShape)
                        .background(Color(0x40FFFFFF))
                        .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
                )

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Choose AI Model",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = Color(0xFFF0F6FC)
                        )
                        Text(
                            text = "Switch between registered AI providers",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = Color(0xFF8B949E)
                        )
                    }

                    // Frosted Close Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x26FFFFFF), Color(0x0FFFFFFF))
                                )
                            )
                            .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFC9D1D9)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (providers.isEmpty()) {
                    val emptyShape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(emptyShape)
                            .background(Color(0x1FFFFFFF))
                            .border(0.75.dp, Color(0x33FFFFFF), emptyShape)
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No AI providers configured yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF8B949E)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Go to Settings > AI Providers to add a provider.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6E7681)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(providers, key = { it.id }) { provider ->
                            val isSelected = provider.id == activeProviderId || (activeProviderId == null && provider.isDefault)
                            val itemShape = RoundedCornerShape(14.dp)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(
                                        elevation = if (isSelected) 8.dp else 2.dp,
                                        shape = itemShape,
                                        ambientColor = if (isSelected) Color(0x401F6FEB) else Color(0x20000000),
                                        spotColor = if (isSelected) Color(0x601F6FEB) else Color(0x40000000)
                                    )
                                    .clip(itemShape)
                                    .background(
                                        brush = if (isSelected) {
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color(0x402F81F7),
                                                    Color(0x201F6FEB)
                                                )
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color(0x22FFFFFF),
                                                    Color(0x0DFFFFFF)
                                                )
                                            )
                                        }
                                    )
                                    .border(
                                        width = if (isSelected) 1.25.dp else 0.75.dp,
                                        brush = if (isSelected) {
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color(0x9958A6FF),
                                                    Color(0x402F81F7)
                                                )
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color(0x38FFFFFF),
                                                    Color(0x10FFFFFF)
                                                )
                                            )
                                        },
                                        shape = itemShape
                                    )
                                    .springPressClickable {
                                        onSelectProvider(provider.id)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Frosted Provider Disc
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) {
                                                        Brush.verticalGradient(
                                                            listOf(Color(0xFF2F81F7), Color(0xFF1F6FEB))
                                                        )
                                                    } else {
                                                        Brush.verticalGradient(
                                                            listOf(Color(0x26FFFFFF), Color(0x0FFFFFFF))
                                                        )
                                                    }
                                                )
                                                .border(
                                                    0.5.dp,
                                                    if (isSelected) Color(0x80FFFFFF) else Color(0x33FFFFFF),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SmartToy,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = if (isSelected) Color.White else Color(0xFFC9D1D9)
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = provider.name.ifBlank { "AI Provider" },
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.5.sp
                                                ),
                                                color = if (isSelected) Color(0xFFF0F6FC) else Color(0xFFE6EDF3),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = provider.model.ifBlank { "Default model" },
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp
                                                ),
                                                color = if (isSelected) Color(0xFF79C0FF) else Color(0xFF8B949E),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color(0xFF388BFF), Color(0xFF1F6FEB))
                                                    )
                                                )
                                                .border(0.75.dp, Color(0x80FFFFFF), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Active",
                                                modifier = Modifier.size(15.dp),
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
