package com.lakefront.tvlauncher.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingTest {
    private val day = 24L * 60 * 60 * 1000

    private fun entry(
        pkg: String = "com.example.player",
        title: String = "Episode 1",
        position: Long = 60_000,
        duration: Long = 600_000,
        updated: Long = 0,
    ) = MediaEntry(
        packageName = pkg,
        title = title,
        subtitle = null,
        positionMs = position,
        durationMs = duration,
        artUri = null,
        intentUri = null,
        updatedAtMs = updated,
    )

    @Test
    fun newestSnapshotWinsPerApp() {
        val merged = ContinueWatching.merge(
            stored = listOf(entry(title = "old", position = 10_000, updated = 100)),
            incoming = listOf(entry(title = "new", position = 20_000, updated = 200)),
            nowMs = 300,
        )
        assertEquals(1, merged.size)
        assertEquals("new", merged.first().title)
        assertEquals(20_000, merged.first().positionMs)
    }

    @Test
    fun olderSamplesNeverOverwriteNewerOnes() {
        val merged = ContinueWatching.merge(
            stored = listOf(entry(title = "newer", position = 20_000, updated = 500)),
            incoming = listOf(entry(title = "older", position = 10_000, updated = 100)),
            nowMs = 600,
        )
        assertEquals("newer", merged.first().title)
    }

    @Test
    fun finishingAnItemRemovesIt() {
        val merged = ContinueWatching.merge(
            stored = listOf(entry(position = 60_000, updated = 100)),
            incoming = listOf(entry(position = 600_000, duration = 600_000, updated = 200)),
            nowMs = 300,
        )
        assertTrue(merged.isEmpty())
    }

    @Test
    fun staleEntriesExpire() {
        val merged = ContinueWatching.merge(
            stored = listOf(
                entry(pkg = "fresh", updated = 30 * day),
                entry(pkg = "stale", updated = 1),
            ),
            incoming = emptyList(),
            nowMs = 30 * day + 1000,
        )
        assertEquals(listOf("fresh"), merged.map { it.packageName })
    }

    @Test
    fun listIsCapped() {
        val many = (1..20).map { entry(pkg = "p$it", updated = it.toLong()) }
        val merged = ContinueWatching.merge(emptyList(), many, nowMs = 100)
        assertEquals(ContinueWatching.MAX_ENTRIES, merged.size)
        assertEquals("p20", merged.first().packageName)
    }
}
