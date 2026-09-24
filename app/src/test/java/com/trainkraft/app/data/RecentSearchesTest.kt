package com.trainkraft.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchesTest {

    @Test
    fun `empty history stores the first query`() {
        assertEquals(listOf("12951"), addRecentSearch(emptyList(), "12951"))
    }

    @Test
    fun `new queries prepend most-recent-first`() {
        val updated = addRecentSearch(listOf("12951"), "NDLS")
        assertEquals(listOf("NDLS", "12951"), updated)
    }

    @Test
    fun `re-search moves the entry to the front`() {
        val updated = addRecentSearch(listOf("NDLS", "12951"), "12951")
        assertEquals(listOf("12951", "NDLS"), updated)
    }

    @Test
    fun `dedup is case-insensitive with the newest casing kept`() {
        val updated = addRecentSearch(listOf("ndls"), "NDLS")
        assertEquals(listOf("NDLS"), updated)
    }

    @Test
    fun `blank queries are ignored`() {
        val existing = listOf("12951")
        assertEquals(existing, addRecentSearch(existing, ""))
        assertEquals(existing, addRecentSearch(existing, "   "))
    }

    @Test
    fun `queries are trimmed on save`() {
        assertEquals(listOf("12951"), addRecentSearch(emptyList(), "  12951  "))
    }

    @Test
    fun `history caps at ten dropping the oldest`() {
        // Most-recent-first: head q1 is newest, tail q10 is oldest. A new
        // prepend makes 11, so take(10) drops the tail (q10, the oldest).
        val existing = (1..10).map { "q$it" }
        val updated = addRecentSearch(existing, "new")
        assertEquals(10, updated.size)
        assertEquals("new", updated.first())
        assertEquals((1..9).map { "q$it" }, updated.drop(1))
    }

    @Test
    fun `encode decode round-trips order`() {
        val items = listOf("12951 Rajdhani", "NDLS", "BRC")
        assertEquals(items, decodeRecentSearches(encodeRecentSearches(items)))
    }

    @Test
    fun `decode of empty string is empty`() {
        assertEquals(emptyList<String>(), decodeRecentSearches(""))
    }

    @Test
    fun `decode drops blank segments`() {
        // Defensive: a hand-edited value with empty segments keeps only queries.
        val raw = encodeRecentSearches(listOf("a", "b"))
        val padded = "\u001F$raw\u001F"
        assertEquals(listOf("a", "b"), decodeRecentSearches(padded))
    }
}
