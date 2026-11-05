package com.example.hiremydriver

/** Most recent searches first, without duplicate queries or partially typed snapshots. */
object SearchHistoryEntries {
    fun remember(entries: List<String>, query: String): List<String> {
        val value = query.trim().take(100)
        if (value.isEmpty()) return entries
        return (listOf(value) + entries.filterNot { it.equals(value, ignoreCase = true) }).take(10)
    }
}
