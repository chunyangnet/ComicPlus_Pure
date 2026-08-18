package com.comicplus.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {
    @Test
    fun rendersCommonMarkdownWithoutDelimiters() {
        val rendered = markdownToAnnotatedString(
            """
            # Heading
            **bold** *italic* ~~removed~~ `code`
            - first
            1. second
            > quoted
            ```
            println(1)
            ```
            """.trimIndent(),
        )

        assertTrue(rendered.text.contains("Heading"))
        assertTrue(rendered.text.contains("bold"))
        assertTrue(rendered.text.contains("first"))
        assertTrue(rendered.text.contains("println(1)"))
        assertFalse(rendered.text.contains("**"))
        assertFalse(rendered.text.contains("```"))
    }

    @Test
    fun acceptsOnlyHttpsLinkAnnotations() {
        val rendered = markdownToAnnotatedString(
            "[safe](https://example.com/read) [unsafe](javascript:alert(1))",
        )
        val annotations = rendered.getStringAnnotations(MARKDOWN_URL_TAG, 0, rendered.length)
        assertEquals(1, annotations.size)
        assertEquals("https://example.com/read", annotations.single().item)
        assertTrue(rendered.text.contains("unsafe"))
    }

    @Test
    fun stripsHtmlTagsAndCapsUntrustedInput() {
        val rendered = markdownToAnnotatedString("<script>alert(1)</script><br/>text" + "x".repeat(50_000))
        assertFalse(rendered.text.contains("<script>"))
        assertTrue(rendered.text.contains("alert(1)"))
        assertTrue(rendered.text.contains("\ntext"))
        assertTrue(rendered.length <= 40_000)
    }

    @Test
    fun leavesUnclosedMarkersReadable() {
        assertEquals("**unfinished", markdownToAnnotatedString("**unfinished").text)
    }

    @Test
    fun reusesCachedLayoutForRepeatedShortComments() {
        val source = "cache-test-**same comment**"
        assertSame(markdownToAnnotatedString(source), markdownToAnnotatedString(source))
    }
}
