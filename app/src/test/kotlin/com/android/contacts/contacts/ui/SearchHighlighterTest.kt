package com.android.contacts.contacts.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHighlighterTest {

    //region ── matchEndIndex ────────────────────────────────────────────────────────

    @Test
    fun `matchEndIndex returns correct end for ASCII prefix`() {
        // "Alexander", start=0, query="Alex" → chars 0..4
        assertEquals(4, matchEndIndex("Alexander", 0, "Alex"))
    }

    @Test
    fun `matchEndIndex returns correct end for mid-word match`() {
        // "John Alex", start=5, query="Alex" → chars 5..9
        assertEquals(9, matchEndIndex("John Alex", 5, "Alex"))
    }

    @Test
    fun `matchEndIndex handles full name match`() {
        assertEquals(3, matchEndIndex("Bob", 0, "Bob"))
    }

    @Test
    fun `matchEndIndex advances correctly for surrogate pair in name`() {
        // U+1F600 😀 is a surrogate pair (String.length == 2)
        // "😀Bob", start=0, query="😀" → end should be 2 (past the surrogate pair)
        val name = "\uD83D\uDE00Bob" // 😀Bob
        val query = "\uD83D\uDE00"   // 😀
        assertEquals(2, matchEndIndex(name, 0, query))
    }
    //endregion

    //region ── parseSnippet ─────────────────────────────────────────────────────────

    @Test
    fun `parseSnippet plain text has no spans`() {
        val result = parseSnippet("john doe", Color.Blue)
        assertEquals("john doe", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `parseSnippet single match strips brackets and applies span`() {
        val result = parseSnippet("[john]", Color.Blue)
        assertEquals("john", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(0, result.spanStyles[0].start)
        assertEquals(4, result.spanStyles[0].end)
        assertEquals(FontWeight.Bold, result.spanStyles[0].item.fontWeight)
        assertEquals(Color.Blue, result.spanStyles[0].item.color)
    }

    @Test
    fun `parseSnippet prefix match with trailing text`() {
        val result = parseSnippet("[jo]hn doe", Color.Blue)
        assertEquals("john doe", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(0, result.spanStyles[0].start)
        assertEquals(2, result.spanStyles[0].end)
    }

    @Test
    fun `parseSnippet multiple matches all highlighted`() {
        val result = parseSnippet("[jo] and [doe]", Color.Blue)
        assertEquals("jo and doe", result.text)
        assertEquals(2, result.spanStyles.size)
        assertEquals(0, result.spanStyles[0].start)
        assertEquals(2, result.spanStyles[0].end)
        assertEquals(7, result.spanStyles[1].start)
        assertEquals(10, result.spanStyles[1].end)
    }

    @Test
    fun `parseSnippet phone number example from provider`() {
        val result = parseSnippet("+380 [50] 974 6758", Color.Blue)
        assertEquals("+380 50 974 6758", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(5, result.spanStyles[0].start)
        assertEquals(7, result.spanStyles[0].end)
    }

    @Test
    fun `parseSnippet email example from provider`() {
        val result = parseSnippet("[john].doe@example.com", Color.Blue)
        assertEquals("john.doe@example.com", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(0, result.spanStyles[0].start)
        assertEquals(4, result.spanStyles[0].end)
    }

    @Test
    fun `parseSnippet unterminated bracket appends remainder without crash`() {
        val result = parseSnippet("[unclosed", Color.Blue)
        assertEquals("[unclosed", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `parseSnippet stray closing bracket is skipped`() {
        val result = parseSnippet("foo]bar", Color.Blue)
        assertEquals("foobar", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `parseSnippet empty string returns empty AnnotatedString`() {
        val result = parseSnippet("", Color.Blue)
        assertEquals("", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `parseSnippet empty brackets produce zero-length span`() {
        val result = parseSnippet("[]text", Color.Blue)
        assertEquals("text", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(0, result.spanStyles[0].start)
        assertEquals(0, result.spanStyles[0].end)
    }
    //endregion
}
