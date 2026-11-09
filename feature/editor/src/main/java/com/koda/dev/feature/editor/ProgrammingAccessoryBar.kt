package com.koda.dev.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koda.dev.core.ui.glass.GlassDesign
import com.koda.dev.core.ui.glass.springPressClickable

@Composable
fun ProgrammingAccessoryBar(
    isDirty: Boolean,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onInsertSymbol: (String) -> Unit,
    onIndent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val symbols = listOf(
        "{", "}", "(", ")", "[", "]", ";", ":", "=",
        "\"", "'", "<", ">", "+", "-", "*", "/", "\\",
        ".", ",", "!", "?", "&", "|", "$", "_", "->", "=>"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(GlassDesign.liquidGlassBackground(topAlpha = 0.92f, midAlpha = 0.88f, bottomAlpha = 0.82f))
            .border(
                width = 0.5.dp,
                brush = GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.35f, dispersionAlpha = 0.15f),
                shape = RectangleShape
            )
            .focusProperties { canFocus = false }
    ) {
        // Internal top refractive sheen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(GlassDesign.internalGlassSheen())
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Save Button with Unsaved Indicator
            val saveShape = RoundedCornerShape(8.dp)
            val saveBackground = if (isDirty) {
                Brush.horizontalGradient(listOf(Color(0xFF238636), Color(0xFF2EA043)))
            } else {
                GlassDesign.microGlassBackground(topAlpha = 0.12f, bottomAlpha = 0.05f)
            }
            val saveBorder = if (isDirty) {
                GlassDesign.liquidGlassBorder(topSpecularAlpha = 0.70f, dispersionAlpha = 0.30f)
            } else {
                GlassDesign.microGlassBorder(topAlpha = 0.28f, bottomAlpha = 0.08f)
            }

            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(saveShape)
                    .background(saveBackground)
                    .border(1.dp, saveBorder, saveShape)
                    .springPressClickable(onClick = onSave)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .focusProperties { canFocus = false },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save File",
                        tint = if (isDirty) Color.White else Color(0xFF8B949E),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        "Save",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDirty) Color.White else Color(0xFF8B949E)
                    )
                    if (isDirty) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF39D353), CircleShape)
                        )
                    }
                }
            }

            // Undo
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(GlassDesign.microGlassBackground(topAlpha = if (canUndo) 0.16f else 0.06f, bottomAlpha = 0.04f))
                    .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = if (canUndo) 0.35f else 0.15f, bottomAlpha = 0.06f), CircleShape)
                    .springPressClickable(enabled = canUndo, onClick = onUndo)
                    .focusProperties { canFocus = false },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) Color(0xFFC9D1D9) else Color(0xFF484F58),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Redo
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(GlassDesign.microGlassBackground(topAlpha = if (canRedo) 0.16f else 0.06f, bottomAlpha = 0.04f))
                    .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = if (canRedo) 0.35f else 0.15f, bottomAlpha = 0.06f), CircleShape)
                    .springPressClickable(enabled = canRedo, onClick = onRedo)
                    .focusProperties { canFocus = false },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) Color(0xFFC9D1D9) else Color(0xFF484F58),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Tab (Indent)
            val keyShape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(keyShape)
                    .background(GlassDesign.microGlassBackground(topAlpha = 0.16f, bottomAlpha = 0.06f))
                    .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.35f, bottomAlpha = 0.10f), keyShape)
                    .springPressClickable(onClick = onIndent)
                    .padding(horizontal = 10.dp)
                    .focusProperties { canFocus = false },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "TAB",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF58A6FF)
                )
            }

            // Quick Programming Symbols
            symbols.forEach { sym ->
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(keyShape)
                        .background(GlassDesign.microGlassBackground(topAlpha = 0.12f, bottomAlpha = 0.04f))
                        .border(0.75.dp, GlassDesign.microGlassBorder(topAlpha = 0.28f, bottomAlpha = 0.08f), keyShape)
                        .springPressClickable { onInsertSymbol(sym) }
                        .padding(horizontal = 11.dp)
                        .focusProperties { canFocus = false },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        sym,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF0F6FC)
                    )
                }
            }
        }
    }
}
