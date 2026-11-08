package com.koda.dev.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    Surface(
        color = Color(0xFF161B22),
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Save Button with Unsaved Indicator
            FilledTonalButton(
                onClick = onSave,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isDirty) Color(0xFF238636) else Color(0xFF21262D),
                    contentColor = if (isDirty) Color.White else Color(0xFF8B949E)
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(32.dp)
                    .focusProperties { canFocus = false },
                shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save File",
                        modifier = Modifier.size(15.dp)
                    )
                    Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (isDirty) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF39D353), CircleShape)
                        )
                    }
                }
            }

            // Undo / Redo
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier
                    .size(32.dp)
                    .focusProperties { canFocus = false }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) Color(0xFFC9D1D9) else Color(0xFF484F58),
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier
                    .size(32.dp)
                    .focusProperties { canFocus = false }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) Color(0xFFC9D1D9) else Color(0xFF484F58),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Tab (Indent)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF21262D),
                modifier = Modifier
                    .height(32.dp)
                    .focusProperties { canFocus = false }
                    .clickable { onIndent() }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp),
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
            }

            // Quick Programming Symbols
            symbols.forEach { sym ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF21262D),
                    modifier = Modifier
                        .height(32.dp)
                        .focusProperties { canFocus = false }
                        .clickable { onInsertSymbol(sym) }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 11.dp),
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
}
