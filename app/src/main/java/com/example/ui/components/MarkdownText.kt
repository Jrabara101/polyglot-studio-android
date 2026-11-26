package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

private val HEADING_PREFIX_REGEX = Regex("^#{1,6}\\s+")
private val BULLET_PREFIX_REGEX = Regex("^\\s*[-*•]\\s+")
private val MARKDOWN_LINK_REGEX = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
private val MULTI_SPACE_REGEX = Regex("\\s+")

/**
 * Lightweight Compose Markdown renderer tailored for AI tutor replies.
 * Supports headings (`#`/`##`/`###`), bullet lists (`*`/`-`), `***bold italic***`,
 * `**bold**`, `*italic*`, `` `inline code` ``, and `[label](url)` links without crashing
 * on partial tokens during token-by-token streaming.
 */
fun parseSimpleMarkdown(
    raw: String,
    codeBackground: Color = Color.Transparent
): AnnotatedString = buildAnnotatedString {
    val lines = raw.replace("\r\n", "\n").split('\n')
    lines.forEachIndexed { lineIdx, line ->
        if (lineIdx > 0) append('\n')
        when {
            HEADING_PREFIX_REGEX.containsMatchIn(line) -> {
                val content = line.replaceFirst(HEADING_PREFIX_REGEX, "")
                val start = length
                appendInlineMarkdown(content, codeBackground)
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
            }
            BULLET_PREFIX_REGEX.containsMatchIn(line) -> {
                val content = line.replaceFirst(BULLET_PREFIX_REGEX, "")
                append("• ")
                appendInlineMarkdown(content, codeBackground)
            }
            else -> appendInlineMarkdown(line, codeBackground)
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    codeBackground: Color
) {
    var i = 0
    val n = text.length
    while (i < n) {
        // Inline code: `code`
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                val start = length
                append(text.substring(i + 1, end))
                addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackground
                    ),
                    start,
                    length
                )
                i = end + 1
                continue
            }
        }

        // Markdown link: [label](url)
        if (text[i] == '[') {
            val closeBracket = text.indexOf(']', i + 1)
            if (closeBracket > i + 1 && closeBracket + 1 < n && text[closeBracket + 1] == '(') {
                val closeParen = text.indexOf(')', closeBracket + 2)
                if (closeParen > closeBracket + 2) {
                    val start = length
                    append(text.substring(i + 1, closeBracket))
                    addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, length)
                    i = closeParen + 1
                    continue
                }
            }
        }

        // Bold + Italic: ***text***
        if (i + 2 < n && text[i] == '*' && text[i + 1] == '*' && text[i + 2] == '*') {
            val end = text.indexOf("***", i + 3)
            if (end > i + 3) {
                val start = length
                appendInlineMarkdown(text.substring(i + 3, end), codeBackground)
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                    start,
                    length
                )
                i = end + 3
                continue
            }
        }

        // Bold: **text** (also handles unclosed trailing ** gracefully during streaming)
        if (i + 1 < n && text[i] == '*' && text[i + 1] == '*') {
            val end = text.indexOf("**", i + 2)
            if (end > i + 2) {
                val start = length
                appendInlineMarkdown(text.substring(i + 2, end), codeBackground)
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                i = end + 2
                continue
            } else {
                // Streaming unclosed bold: style remaining text cleanly instead of showing raw "**"
                val start = length
                append(text.substring(i + 2))
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                return
            }
        }

        // Italic: *text* (require non-space immediately after opening '*' so math like "2 * 3" stays literal)
        if (text[i] == '*' && i + 1 < n && !text[i + 1].isWhitespace() && text[i + 1] != '*') {
            val end = findClosingSingleAsterisk(text, i + 1)
            if (end > i + 1) {
                val start = length
                appendInlineMarkdown(text.substring(i + 1, end), codeBackground)
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                i = end + 1
                continue
            }
        }

        append(text[i])
        i++
    }
}

private fun findClosingSingleAsterisk(text: String, fromIndex: Int): Int {
    var idx = fromIndex
    while (idx < text.length) {
        if (text[idx] == '*') {
            val prevIsStar = idx > 0 && text[idx - 1] == '*'
            val nextIsStar = idx + 1 < text.length && text[idx + 1] == '*'
            val prevIsSpace = idx > 0 && text[idx - 1].isWhitespace()
            if (!prevIsStar && !nextIsStar && !prevIsSpace) {
                return idx
            }
        }
        idx++
    }
    return -1
}

/** Strips Markdown syntax so TTS voices read natural prose instead of literal punctuation. */
fun stripMarkdownForSpeech(raw: String): String {
    val sentenceEnders = setOf('.', '!', '?', '。', '！', '？', ':', '：')
    val cleanedLines = raw
        .replace("\r\n", "\n")
        .split('\n')
        .mapNotNull { line ->
            val stripped = line
                .replaceFirst(HEADING_PREFIX_REGEX, "")
                .replaceFirst(BULLET_PREFIX_REGEX, "")
                .replace(MARKDOWN_LINK_REGEX, "$1")
                .replace("***", "")
                .replace("**", "")
                .replace("__", "")
                .replace("*", "")
                .replace("`", "")
                .replace(MULTI_SPACE_REGEX, " ")
                .trim()
            stripped.takeIf { it.isNotEmpty() }
        }

    if (cleanedLines.isEmpty()) return ""
    val sb = StringBuilder()
    cleanedLines.forEachIndexed { idx, line ->
        if (idx > 0) {
            val prevLast = sb.lastOrNull()
            if (prevLast != null && prevLast in sentenceEnders) {
                sb.append(' ')
            } else {
                sb.append(". ")
            }
        }
        sb.append(line)
    }
    return sb.toString()
}
