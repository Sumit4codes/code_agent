package com.koda.dev.feature.editor

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun InteractiveCodeEditor(
    tab: EditorTab,
    wrapLines: Boolean,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onCloseFile: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
    onRegisterInsertHandle: (((String) -> Unit)?) -> Unit = {}
) {
    var textFieldValue by remember(tab.uri) {
        mutableStateOf(TextFieldValue(tab.currentContent, TextRange(tab.currentContent.length)))
    }

    // Keep textFieldValue in sync if updated from external undo/save/tab switch
    LaunchedEffect(tab.currentContent) {
        if (textFieldValue.text != tab.currentContent) {
            val prevSelection = textFieldValue.selection
            val safeStart = prevSelection.start.coerceIn(0, tab.currentContent.length)
            val safeEnd = prevSelection.end.coerceIn(0, tab.currentContent.length)
            textFieldValue = textFieldValue.copy(
                text = tab.currentContent,
                selection = TextRange(safeStart, safeEnd)
            )
        }
    }

    val currentTextFieldValue by rememberUpdatedState(textFieldValue)
    val currentOnContentChange by rememberUpdatedState(onContentChange)

    // Expose insertion lambda to parent/accessory bar
    DisposableEffect(tab.uri) {
        onRegisterInsertHandle { insertString ->
            val value = currentTextFieldValue
            val curText = value.text
            val sel = value.selection
            val start = minOf(sel.start, sel.end).coerceIn(0, curText.length)
            val end = maxOf(sel.start, sel.end).coerceIn(0, curText.length)
            val newText = curText.substring(0, start) + insertString + curText.substring(end)
            val newCursor = start + insertString.length
            textFieldValue = TextFieldValue(newText, TextRange(newCursor))
            currentOnContentChange(newText)
        }
        onDispose {
            onRegisterInsertHandle(null)
        }
    }

    val language = remember(tab.name) { getEditorLanguage(tab.name) }
    val visualTransformation = remember(tab.name) { SyntaxVisualTransformation(language) }

    val lines = remember(textFieldValue.text) { textFieldValue.text.lines() }
    val lineCount = maxOf(1, lines.size)
    val gutterDigits = maxOf(2, lineCount.toString().length)
    val gutterWidth = (gutterDigits * 10 + 16).dp

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        // Code Metadata Header bar
        Surface(
            color = Color(0xFF161B22),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Language badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF21262D)
                    ) {
                        Text(
                            text = language,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF58A6FF),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Line count badge
                    Text(
                        text = "$lineCount lines",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B949E)
                    )

                    // Dirty indicator
                    if (tab.isDirty) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF58A6FF), CircleShape)
                            )
                            Text(
                                text = "Modified",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF58A6FF),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onCloseFile,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close file",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF8B949E)
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF30363D))

        // Code Editor Body: Synchronized Line Numbers Gutter + BasicTextField
        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(verticalScroll)
        ) {
            // Line numbers Gutter
            Column(
                modifier = Modifier
                    .width(gutterWidth)
                    .background(Color(0xFF161B22))
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                for (i in 1..lineCount) {
                    Text(
                        text = "$i",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = Color(0xFF484F58)
                        ),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            // Gutter divider line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF30363D))
            )

            // Text Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(if (wrapLines) Modifier.fillMaxWidth() else Modifier.horizontalScroll(horizontalScroll))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onContentChange(newValue.text)
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFFF0F6FC)
                    ),
                    cursorBrush = SolidColor(Color(0xFF58A6FF)),
                    visualTransformation = visualTransformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                // Ctrl+S -> Save
                                if (event.isCtrlPressed && event.key == Key.S) {
                                    onSave()
                                    return@onPreviewKeyEvent true
                                }
                                // Ctrl+Z -> Undo
                                if (event.isCtrlPressed && !event.isShiftPressed && event.key == Key.Z) {
                                    onUndo()
                                    return@onPreviewKeyEvent true
                                }
                                // Ctrl+Y or Ctrl+Shift+Z -> Redo
                                if ((event.isCtrlPressed && event.key == Key.Y) ||
                                    (event.isCtrlPressed && event.isShiftPressed && event.key == Key.Z)) {
                                    onRedo()
                                    return@onPreviewKeyEvent true
                                }
                                // Tab key -> insert 4 spaces
                                if (event.key == Key.Tab) {
                                    val curText = textFieldValue.text
                                    val sel = textFieldValue.selection
                                    val start = minOf(sel.start, sel.end)
                                    val end = maxOf(sel.start, sel.end)
                                    val indent = "    "
                                    val newText = curText.substring(0, start) + indent + curText.substring(end)
                                    val newCursor = start + indent.length
                                    textFieldValue = TextFieldValue(newText, TextRange(newCursor))
                                    onContentChange(newText)
                                    return@onPreviewKeyEvent true
                                }
                            }
                            false
                        }
                )
            }
        }
    }
}

fun getEditorLanguage(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts" -> "Kotlin"
        "java" -> "Java"
        "c", "h" -> "C"
        "cpp", "hpp", "cc", "cxx" -> "C++"
        "py" -> "Python"
        "js", "mjs", "cjs" -> "JavaScript"
        "ts", "tsx" -> "TypeScript"
        "json" -> "JSON"
        "xml", "html", "svg" -> "XML/HTML"
        "md", "markdown" -> "Markdown"
        "sh", "bash" -> "Shell"
        "sql" -> "SQL"
        "yml", "yaml" -> "YAML"
        "gradle" -> "Gradle"
        "txt" -> "Text"
        else -> ext.uppercase().ifBlank { "Plain Text" }
    }
}
