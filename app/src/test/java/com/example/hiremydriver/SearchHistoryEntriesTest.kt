package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class SearchHistoryEntriesTest {
    @Test fun repeatMovesQueryToFrontWithoutDuplicates() {
        assertEquals(listOf("GLENN", "Bohol"), SearchHistoryEntries.remember(listOf("Bohol", "Glenn"), " GLENN "))
    }
    @Test fun emptyDoesNotEraseHistoryAndOnlyTenAreKept() {
        val old = (1..10).map { "Search $it" }
        assertEquals(old, SearchHistoryEntries.remember(old, "  "))
        assertEquals(listOf("New") + old.take(9), SearchHistoryEntries.remember(old, "New"))
        assertEquals(100, SearchHistoryEntries.remember(emptyList(), "x".repeat(200)).first().length)
    }
}
