package com.koda.dev.feature.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class SyntaxVisualTransformation(private val language: String) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val annotated = highlightCode(raw)
        return TransformedText(annotated, OffsetMapping.Identity)
    }

    private fun highlightCode(code: String): AnnotatedString {
        val keywordRegex = Regex(
            "\\b(val|var|fun|class|object|interface|package|import|return|if|else|when|for|while|try|catch|finally|throw|new|public|private|protected|internal|override|suspend|data|sealed|inline|const|null|true|false|this|super|def|let|function|async|await|struct|typedef|enum|int|float|double|char|void|bool|unsigned|long|static|extern|auto|sizeof|nullptr|type|from|as|lambda|echo|fi|done|elif|esac|case|export|default|switch|break|continue)\\b"
        )
        val directiveRegex = Regex("(#include|#define|#ifdef|#ifndef|#endif|#pragma|@[A-Za-z0-9_]+)")
        val stringRegex = Regex("\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'")
        val commentRegex = Regex("//.*|/\\*.*?\\*/|#.*")
        val numberRegex = Regex("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?([fFlL])?)\\b")

        val keywordColor = Color(0xFFFF7B72)       // GitHub dark coral
        val directiveColor = Color(0xFFD2A8FF)     // GitHub dark purple
        val stringColor = Color(0xFFA5D6A7)        // Mint green
        val commentColor = Color(0xFF8B949E)       // Muted slate gray
        val numberColor = Color(0xFF79C0FF)        // Light cyan blue

        return buildAnnotatedString {
            append(code)

            for (m in keywordRegex.findAll(code)) {
                addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
            }
            for (m in directiveRegex.findAll(code)) {
                addStyle(SpanStyle(color = directiveColor, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
            }
            for (m in numberRegex.findAll(code)) {
                addStyle(SpanStyle(color = numberColor), m.range.first, m.range.last + 1)
            }
            for (m in stringRegex.findAll(code)) {
                addStyle(SpanStyle(color = stringColor), m.range.first, m.range.last + 1)
            }
            for (m in commentRegex.findAll(code)) {
                addStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
            }
        }
    }
}
