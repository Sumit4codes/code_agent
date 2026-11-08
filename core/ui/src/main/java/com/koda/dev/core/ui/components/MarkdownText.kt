package com.koda.dev.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.LocalTableRowIndex
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor

@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier
) {
    Markdown(
        content = content,
        modifier = modifier,
        colors = markdownColor(
            text = MaterialTheme.colorScheme.onSurface,
            codeBackground = MaterialTheme.colorScheme.surfaceVariant,
            inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant,
        ),
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

