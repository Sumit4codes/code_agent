package com.koda.dev.feature.editor

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

data class GutterLine(
    val lineNumber: Int,
    val baselinePx: Float,
    val isCurrent: Boolean
)

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

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    @Suppress("DEPRECATION")
    val normalGutterStyle = remember {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = Color(0xFF484F58),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None
            )
        )
    }
    @Suppress("DEPRECATION")
    val activeGutterStyle = remember {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = Color(0xFF58A6FF),
            fontWeight = FontWeight.Bold,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None
            )
        )
    }
    @Suppress("DEPRECATION")
    val editorTextStyle = remember {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = Color(0xFFF0F6FC),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None
            )
        )
    }

    val currentCursorOffset = textFieldValue.selection.start
    val gutterLines = remember(textLayoutResult, textFieldValue.text, currentCursorOffset) {
        val layout = textLayoutResult
        val text = textFieldValue.text
        if (layout == null) {
            val lines = text.lines()
            val count = maxOf(1, lines.size)
            val approxLineHeightPx = with(density) { 20.sp.toPx() }
            val approxBaselinePx = with(density) { 15.sp.toPx() }
            return@remember (1..count).map { num ->
                GutterLine(
                    lineNumber = num,
                    baselinePx = (num - 1) * approxLineHeightPx + approxBaselinePx,
                    isCurrent = (num == 1)
                )
            }
        }

        val cursorLogicalLine = 1 + text.take(currentCursorOffset.coerceIn(0, text.length)).count { it == '\n' }
        val count = layout.lineCount
        val result = ArrayList<GutterLine>(count)
        var logicalLine = 1

        for (v in 0 until count) {
            val start = layout.getLineStart(v)
            val isLogicalStart = (v == 0) || (start > 0 && text.getOrNull(start - 1) == '\n')
            if (isLogicalStart) {
                val num = logicalLine++
                result.add(
                    GutterLine(
                        lineNumber = num,
                        baselinePx = layout.getLineBaseline(v),
                        isCurrent = (num == cursorLogicalLine)
                    )
                )
            }
        }
        result
    }

    val totalLogicalLines = maxOf(1, gutterLines.lastOrNull()?.lineNumber ?: 1)
    val gutterDigits = maxOf(2, totalLogicalLines.toString().length)
    val charWidthPx = remember(textMeasurer, normalGutterStyle) {
        textMeasurer.measure("0", normalGutterStyle).size.width.toFloat()
    }
    val gutterWidth = with(density) { (gutterDigits * charWidthPx + 24.dp.toPx()).toDp() }

    val contentHeightPx = textLayoutResult?.size?.height?.toFloat()
        ?: (totalLogicalLines * with(density) { 20.sp.toPx() })
    val contentHeightDp = with(density) { contentHeightPx.toDp() } + 16.dp

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
                        text = "$totalLogicalLines lines",
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
            val verticalPaddingPx = with(density) { 8.dp.toPx() }
            val rightMarginPx = with(density) { 6.dp.toPx() }

            // Line numbers Gutter (drawn via Canvas with 100% exact baseline alignment to BasicTextField)
            Box(
                modifier = Modifier
                    .width(gutterWidth)
                    .height(contentHeightDp)
                    .background(Color(0xFF161B22))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (entry in gutterLines) {
                        val style = if (entry.isCurrent) activeGutterStyle else normalGutterStyle
                        val measured = textMeasurer.measure(
                            text = "${entry.lineNumber}",
                            style = style
                        )
                        val x = size.width - measured.size.width - rightMarginPx
                        // Align mathematically on the exact baseline of the text line
                        val y = verticalPaddingPx + entry.baselinePx - measured.firstBaseline
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(x, y)
                        )
                    }
                }
            }

            // Gutter divider line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(contentHeightDp)
                    .background(Color(0xFF30363D))
            )

            // Text Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(contentHeightDp)
                    .then(if (wrapLines) Modifier.fillMaxWidth() else Modifier.horizontalScroll(horizontalScroll))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onContentChange(newValue.text)
                    },
                    onTextLayout = { layout ->
                        textLayoutResult = layout
                    },
                    textStyle = editorTextStyle,
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
