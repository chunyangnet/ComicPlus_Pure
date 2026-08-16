package com.comicplus.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import java.net.URI

/** Annotation tag used for links emitted by [markdownToAnnotatedString]. */
const val MARKDOWN_URL_TAG: String = "markdown-url"

private const val MAX_MARKDOWN_LENGTH = 40_000
private const val MAX_MARKDOWN_LINES = 800
private const val MAX_INLINE_DEPTH = 8
private const val MAX_LINK_LENGTH = 1_024

private val HEADING_PATTERN = Regex("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*#*\\s*$")
private val UNORDERED_PATTERN = Regex("^(\\s*)[-+*]\\s+(.+)$")
private val ORDERED_PATTERN = Regex("^(\\s*)(\\d{1,4})[.)]\\s+(.+)$")
private val QUOTE_PATTERN = Regex("^\\s*>\\s?(.*)$")
private val FENCE_PATTERN = Regex("^\\s*```(?:[^`]*)$")
private val BR_TAG_PATTERN = Regex("(?i)<\\s*br\\s*/?\\s*>")
private val PARAGRAPH_TAG_PATTERN = Regex("(?i)</?\\s*(?:p|div|li|blockquote)\\s*>")
private val HTML_TAG_PATTERN = Regex("(?s)<[^>]{0,240}>")
private val ENTITY_PATTERN = Regex("(?i)&(?:amp|lt|gt|quot|apos|nbsp|#39|#x27);")
private val LINK_PATTERN = Regex(
    "\\[((?:\\\\.|[^\\]]){1,240})\\]\\((https://[^\\s<>\\\"]{1,$MAX_LINK_LENGTH})\\)",
    RegexOption.IGNORE_CASE,
)

private val HeadingStyles = listOf(
    SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp),
    SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp),
    SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
    SpanStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp),
)

private val QuoteStyle = SpanStyle(
    color = Color(0xFF5F6368),
    fontStyle = FontStyle.Italic,
)

private val CodeStyle = SpanStyle(
    color = Color(0xFF27313A),
    background = Color(0xFFE9EEF2),
    fontFamily = FontFamily.Monospace,
)

private val InlineCodeStyle = CodeStyle.copy(fontSize = 13.sp)
private val LinkStyle = SpanStyle(
    color = Color(0xFF1769AA),
    textDecoration = TextDecoration.Underline,
)

private sealed interface ParsedLine {
    data class Text(val value: String) : ParsedLine
    data class Code(val value: String) : ParsedLine
}

/**
 * Converts a small, deliberately bounded Markdown subset to Compose text.
 * Unknown HTML is stripped after safe line-break conversion, so server text
 * cannot inject a view or executable resource into the UI.
 */
fun markdownToAnnotatedString(source: String): AnnotatedString {
    val normalized = sanitizeMarkup(source.take(MAX_MARKDOWN_LENGTH)).trim()
    if (normalized.isBlank()) return AnnotatedString("")

    val parsed = ArrayList<ParsedLine>()
    var inFence = false
    normalized.split('\n').take(MAX_MARKDOWN_LINES).forEach { rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trimStart()
        if (inFence) {
            if (trimmed.startsWith("```")) {
                inFence = false
            } else {
                parsed += ParsedLine.Code(line)
            }
        } else if (FENCE_PATTERN.matches(line)) {
            inFence = true
        } else {
            parsed += ParsedLine.Text(line)
        }
    }

    val builder = AnnotatedString.Builder()
    parsed.forEachIndexed { index, line ->
        if (index > 0) builder.append('\n')
        when (line) {
            is ParsedLine.Code -> appendCodeLine(builder, line.value)
            is ParsedLine.Text -> appendMarkdownLine(builder, line.value)
        }
    }
    return builder.toAnnotatedString()
}

private fun appendCodeLine(builder: AnnotatedString.Builder, line: String) {
    builder.pushStyle(CodeStyle)
    builder.append(if (line.isBlank()) " " else line)
    builder.pop()
}

private fun appendMarkdownLine(builder: AnnotatedString.Builder, line: String) {
    if (line.isBlank()) return
    HEADING_PATTERN.matchEntire(line)?.let { match ->
        val level = match.groupValues[1].length.coerceIn(1, HeadingStyles.size)
        builder.pushStyle(HeadingStyles[level - 1])
        appendInline(builder, match.groupValues[2].trim())
        builder.pop()
        return
    }
    UNORDERED_PATTERN.matchEntire(line)?.let { match ->
        builder.append("  ".repeat((match.groupValues[1].length / 2).coerceAtMost(8)))
        builder.append("- ")
        appendInline(builder, match.groupValues[2])
        return
    }
    ORDERED_PATTERN.matchEntire(line)?.let { match ->
        builder.append("  ".repeat((match.groupValues[1].length / 2).coerceAtMost(8)))
        builder.append(match.groupValues[2])
        builder.append(". ")
        appendInline(builder, match.groupValues[3])
        return
    }
    QUOTE_PATTERN.matchEntire(line)?.let { match ->
        builder.pushStyle(QuoteStyle)
        builder.append("| ")
        appendInline(builder, match.groupValues[1])
        builder.pop()
        return
    }
    if (line.trim().matches(Regex("^(?:[-*_]\\s*){3,}$"))) {
        builder.append("---")
        return
    }
    appendInline(builder, line)
}

private fun appendInline(builder: AnnotatedString.Builder, source: String, depth: Int = 0) {
    if (source.isEmpty()) return
    if (depth >= MAX_INLINE_DEPTH) {
        builder.append(source)
        return
    }
    var index = 0
    while (index < source.length) {
        val current = source[index]
        if (current == '\\' && index + 1 < source.length && source[index + 1] in "\\`*_~[]()") {
            builder.append(source[index + 1])
            index += 2
            continue
        }

        if (current == '`') {
            val close = source.indexOf('`', index + 1)
            if (close > index + 1) {
                builder.pushStyle(InlineCodeStyle)
                builder.append(source.substring(index + 1, close))
                builder.pop()
                index = close + 1
                continue
            }
        }

        val link = LINK_PATTERN.find(source, index)
        if (link != null && link.range.first == index + 1 && current == '!' && source[index + 1] == '[') {
            // Remote images are deliberately rendered as their alt text. The
            // detail screen is text-only and must not turn server Markdown
            // into an unbounded stream of network requests.
            appendInline(builder, unescape(link.groupValues[1]), depth + 1)
            index = link.range.last + 1
            continue
        }
        if (link != null && link.range.first == index) {
            val url = link.groupValues[2]
            if (isSafeHttpsUrl(url)) {
                builder.pushStringAnnotation(MARKDOWN_URL_TAG, url)
                builder.pushStyle(LinkStyle)
                appendInline(builder, unescape(link.groupValues[1]), depth + 1)
                builder.pop()
                builder.pop()
            } else {
                appendInline(builder, unescape(link.groupValues[1]), depth + 1)
            }
            index = link.range.last + 1
            continue
        }

        val marker = when {
            source.startsWith("**", index) -> "**"
            source.startsWith("__", index) -> "__"
            source.startsWith("~~", index) -> "~~"
            current == '*' || current == '_' -> current.toString()
            else -> null
        }
        if (marker != null) {
            val close = source.indexOf(marker, index + marker.length)
            if (close > index + marker.length) {
                val style = when (marker) {
                    "**", "__" -> SpanStyle(fontWeight = FontWeight.Bold)
                    "~~" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    else -> SpanStyle(fontStyle = FontStyle.Italic)
                }
                builder.pushStyle(style)
                appendInline(builder, source.substring(index + marker.length, close), depth + 1)
                builder.pop()
                index = close + marker.length
                continue
            }
        }

        builder.append(current)
        index++
    }
}

private fun sanitizeMarkup(source: String): String {
    var value = source.replace("\r\n", "\n").replace('\r', '\n')
    value = BR_TAG_PATTERN.replace(value, "\n")
    value = PARAGRAPH_TAG_PATTERN.replace(value, "\n")
    value = HTML_TAG_PATTERN.replace(value, "")
    return ENTITY_PATTERN.replace(value) { match ->
        when (match.value.lowercase()) {
            "&amp;" -> "&"
            "&lt;" -> "<"
            "&gt;" -> ">"
            "&quot;" -> "\""
            "&apos;", "&#39;", "&#x27;" -> "'"
            "&nbsp;" -> " "
            else -> match.value
        }
    }
}

private fun unescape(value: String): String = value.replace("\\\\", "\\").replace("\\]", "]")

private fun isSafeHttpsUrl(value: String): Boolean {
    if (value.length > MAX_LINK_LENGTH || value.any(Char::isWhitespace)) return false
    return runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host != null &&
            uri.userInfo == null &&
            uri.fragment == null
    }.getOrDefault(false)
}
