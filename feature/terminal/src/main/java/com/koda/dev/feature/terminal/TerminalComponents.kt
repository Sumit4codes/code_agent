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
import androidx.compose.foundation.shape.RoundedCornerShape
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FormatSize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Terminal Text Size",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Current Size",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "$currentFontSizeSp sp",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Steppers + Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { onFontSizeChange(currentFontSizeSp - 1) },
                        enabled = currentFontSizeSp > 9,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                    }

                    Slider(
                        value = currentFontSizeSp.toFloat(),
                        onValueChange = { onFontSizeChange(it.toInt()) },
                        valueRange = 9f..28f,
                        steps = 18,
                        modifier = Modifier.weight(1f)
                    )

                    FilledTonalIconButton(
                        onClick = { onFontSizeChange(currentFontSizeSp + 1) },
                        enabled = currentFontSizeSp < 28,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
                    }
                }

                // Quick Presets
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
                        FilterChip(
                            selected = currentFontSizeSp == size,
                            onClick = { onFontSizeChange(size) },
                            label = { Text("$size sp ($label)", fontSize = 11.sp) }
                        )
                    }
                }

                // Live Preview box
                Surface(
                    color = Color(0xFF0D1117),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
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

                Text(
                    text = "Tip: You can also pinch-to-zoom with two fingers directly on the terminal screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
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
