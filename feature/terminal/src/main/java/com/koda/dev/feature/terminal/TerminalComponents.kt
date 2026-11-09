package com.koda.dev.feature.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koda.dev.core.ui.glass.GlassDesign
import com.koda.dev.core.ui.glass.springPressClickable

internal val TerminalBg = Color(0xFF0D1117)
internal val TerminalPrompt = Color(0xFF39D353)
internal val TerminalStderr = Color(0xFFF85149)
internal val AccessoryKeyBg = Color(0xFF21262D)
internal val AccessoryKeyContent = Color(0xFFC9D1D9)
internal val EscKeyBg = Color(0xFF1F6FEB)

@Composable
internal fun TerminalFontSizeDialog(
    currentFontSizeSp: Int,
    onFontSizeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
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
            // Internal Top Refraction Sheen
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with Frosted FormatSize disc + title + close button
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
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(GlassDesign.microGlassBackground(topAlpha = 0.22f, bottomAlpha = 0.08f))
                                .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.45f, bottomAlpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = null,
                                tint = Color(0xFF58A6FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Terminal Text Size",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFFF0F6FC)
                            )
                            Text(
                                text = "Current: $currentFontSizeSp sp",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF58A6FF)
                            )
                        }
                    }

                    // Close Button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.08f), CircleShape)
                            .springPressClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close dialog",
                            tint = Color(0xFFC9D1D9),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Steppers + Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.08f), CircleShape)
                            .then(if (currentFontSizeSp > 9) Modifier.springPressClickable { onFontSizeChange(currentFontSizeSp - 1) } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Decrease",
                            tint = if (currentFontSizeSp > 9) Color(0xFFF0F6FC) else Color(0xFF484F58),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Slider(
                        value = currentFontSizeSp.toFloat(),
                        onValueChange = { onFontSizeChange(it.toInt()) },
                        valueRange = 9f..28f,
                        steps = 18,
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.30f, bottomAlpha = 0.08f), CircleShape)
                            .then(if (currentFontSizeSp < 28) Modifier.springPressClickable { onFontSizeChange(currentFontSizeSp + 1) } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Increase",
                            tint = if (currentFontSizeSp < 28) Color(0xFFF0F6FC) else Color(0xFF484F58),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Quick Presets with frosted micro-glass chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        11 to "Compact",
                        14 to "Default",
                        16 to "Medium",
                        18 to "Large",
                        22 to "Huge"
                    ).forEach { (size, label) ->
                        val isChosen = currentFontSizeSp == size
                        val chipShape = RoundedCornerShape(8.dp)
                        Box(
                            modifier = Modifier
                                .clip(chipShape)
                                .then(
                                    if (isChosen) {
                                        Modifier
                                            .background(Color(0xFF1F6FEB).copy(alpha = 0.30f))
                                            .border(1.dp, Brush.verticalGradient(listOf(Color(0xFF58A6FF).copy(alpha = 0.75f), Color(0xFF1F6FEB).copy(alpha = 0.35f))), chipShape)
                                    } else {
                                        Modifier
                                            .background(GlassDesign.microGlassBackground(topAlpha = 0.12f, bottomAlpha = 0.04f))
                                            .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.25f, bottomAlpha = 0.06f), chipShape)
                                    }
                                )
                                .springPressClickable { onFontSizeChange(size) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$size sp ($label)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Normal),
                                color = if (isChosen) Color(0xFFF0F6FC) else Color(0xFFC9D1D9)
                            )
                        }
                    }
                }

                // Live Preview Box (frosted dark container with code font)
                val previewShape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(previewShape)
                        .background(Color(0xFF0D1117).copy(alpha = 0.85f))
                        .border(1.dp, Color(0xFF30363D), previewShape)
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "$ ls -la",
                            color = Color(0xFF39D353),
                            fontFamily = FontFamily.Monospace,
                            fontSize = currentFontSizeSp.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "total 24\n-rw-r--r-- 1 user 1024 main.py",
                            color = Color(0xFFC9D1D9),
                            fontFamily = FontFamily.Monospace,
                            fontSize = currentFontSizeSp.sp
                        )
                    }
                }

                // Footer Done button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    val doneBtnShape = RoundedCornerShape(12.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(doneBtnShape)
                            .background(Brush.horizontalGradient(listOf(Color(0xFF1F6FEB).copy(alpha = 0.65f), Color(0xFF238636).copy(alpha = 0.50f))))
                            .border(0.75.dp, GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.80f, dispersionAlpha = 0.35f), doneBtnShape)
                        .springPressClickable(onClick = onDismiss)
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Text("Done", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFFF0F6FC))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassKeycap(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    borderBrush: Brush? = null,
    shape: RoundedCornerShape = RoundedCornerShape(9.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .then(
                if (backgroundColor != null) Modifier.background(backgroundColor)
                else Modifier.background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.05f))
            )
            .border(
                width = 0.75.dp,
                brush = borderBrush ?: GlassDesign.microGlassBorder(topAlpha = 0.28f, bottomAlpha = 0.08f),
                shape = shape
            )
            .springPressClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun GlassTextKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    borderBrush: Brush? = null,
    textColor: Color = AccessoryKeyContent,
    isBold: Boolean = true
) {
    GlassKeycap(
        onClick = onClick,
        backgroundColor = backgroundColor,
        borderBrush = borderBrush,
        modifier = modifier
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}

@Composable
private fun GlassIconKey(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = AccessoryKeyContent
) {
    GlassKeycap(
        onClick = onClick,
        modifier = modifier.size(32.dp)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
internal fun AccessoryKeyboardBar(
    onKeyPress: (String) -> Unit,
    isControlActive: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val quickTokens = listOf(
        "|", "&&", ";", "-", "--", "/", "~", "$", ":", "'", "\"",
        "sh", "bash", "chmod +x", "apk", "git", "python3", "gcc", "make", "npm", "cargo", "vim", "ls -la", "pwd", "clear"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .focusProperties { canFocus = false }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ESC key (crucial for Vim mode switching - accent blue glass)
        GlassTextKey(
            text = "ESC",
            onClick = { onKeyPress("ESC") },
            backgroundColor = Color(0xFF1F6FEB).copy(alpha = 0.40f),
            borderBrush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.75f, dispersionAlpha = 0.35f),
            textColor = Color.White
        )

        // TAB key (autocompletion)
        GlassTextKey(
            text = "TAB",
            onClick = { onKeyPress("TAB") },
            textColor = Color(0xFFF0F6FC)
        )

        // CTRL toggle modifier (glows green when active)
        GlassTextKey(
            text = "CTRL",
            onClick = { onKeyPress("CTRL") },
            backgroundColor = if (isControlActive) TerminalPrompt.copy(alpha = 0.35f) else null,
            borderBrush = if (isControlActive) GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.80f, dispersionAlpha = 0.40f) else null,
            textColor = if (isControlActive) Color(0xFF7EE787) else Color(0xFFF0F6FC)
        )

        // CTRL-C interrupt key (accent red glass)
        GlassTextKey(
            text = "CTRL-C",
            onClick = { onKeyPress("CTRL-C") },
            backgroundColor = Color(0xFFDA3633).copy(alpha = 0.35f),
            borderBrush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.75f, dispersionAlpha = 0.35f),
            textColor = Color(0xFFFF7B72)
        )

        // Arrow Left
        GlassIconKey(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Left Arrow",
            onClick = { onKeyPress("LEFT") }
        )

        // Arrow Up
        GlassIconKey(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = "Up Arrow",
            onClick = { onKeyPress("UP") }
        )

        // Arrow Down
        GlassIconKey(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Down Arrow",
            onClick = { onKeyPress("DOWN") }
        )

        // Arrow Right
        GlassIconKey(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Right Arrow",
            onClick = { onKeyPress("RIGHT") }
        )

        // Quick keys & tokens
        quickTokens.forEach { token ->
            GlassTextKey(
                text = token,
                onClick = { onKeyPress(token) },
                isBold = false,
                textColor = Color(0xFFC9D1D9)
            )
        }
    }
}
