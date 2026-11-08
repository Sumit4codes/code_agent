package com.koda.dev.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.LocalTableRowIndex
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier
) {
    val typography = markdownTypography(
        h1 = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.sp,
            color = Color(0xFFF0F6FC)
        ),
        h2 = TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 21.sp,
            color = Color(0xFFF0F6FC)
        ),
        h3 = TextStyle(
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp,
            color = Color(0xFFF0F6FC)
        ),
        h4 = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp,
            color = Color(0xFFF0F6FC)
        ),
        h5 = TextStyle(
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 19.sp,
            color = Color(0xFFF0F6FC)
        ),
        h6 = TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp,
            color = Color(0xFF8B949E)
        ),
        text = TextStyle(
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = Color(0xFFE6EDF3)
        ),
        paragraph = TextStyle(
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = Color(0xFFE6EDF3)
        ),
        bullet = TextStyle(
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = Color(0xFFE6EDF3)
        ),
        ordered = TextStyle(
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = Color(0xFFE6EDF3)
        ),
        list = TextStyle(
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = Color(0xFFE6EDF3)
        ),
        quote = TextStyle(
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = Color(0xFF8B949E)
        ),
        inlineCode = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFE5C07B)
        ),
        code = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = Color(0xFFF0F6FC)
        ),
        table = TextStyle(
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            color = Color(0xFFE6EDF3)
        )
    )

    // Warm golden amber inline code with subtle dark badge (#202328) matching user reference
    val colors = markdownColor(
        text = Color(0xFFE6EDF3),
        codeBackground = Color(0xFF161B22),
        inlineCodeBackground = Color(0xFF202328),
        dividerColor = Color(0xFF30363D),
        tableBackground = Color(0xFF161B22)
    )

    Markdown(
        content = content,
        modifier = modifier,
        colors = colors,
        typography = typography,
        components = markdownComponents(
            table = { model ->
                MarkdownTable(
                    content = model.content,
                    node = model.node,
                    style = model.typography.table,
                    headerBlock = { c, header, tableWidth, style ->
                        MarkdownTableHeader(
                            content = c,
                            header = header,
                            tableWidth = tableWidth,
                            style = style,
                            maxLines = Int.MAX_VALUE,
                            overflow = TextOverflow.Clip,
                        )
                    },
                    rowBlock = { c, row, tableWidth, style ->
                        MarkdownTableRow(
                            content = c,
                            header = row,
                            tableWidth = tableWidth,
                            style = style,
                            rowIndex = LocalTableRowIndex.current,
                            maxLines = Int.MAX_VALUE,
                            overflow = TextOverflow.Clip,
                        )
                    }
                )
            }
        )
    )
}
