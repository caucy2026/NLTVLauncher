package com.lakefront.tvlauncher.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaEntriesTest {
    private fun entry(
        pkg: String = "com.example.player",
        title: String = "Episode 1",
        position: Long = 60_000,
        duration: Long = 600_000,
        updated: Long = 1_000,
    ) = MediaEntry(
        packageName = pkg,
        title = title,
        subtitle = "Season 1",
        positionMs = position,
        durationMs = duration,
        artUri = null,
        intentUri = "intent:#Intent;component=x/y;end",
        updatedAtMs = updated,
    )

    @Test
    fun keepsStartedButUnfinishedSessions() {
        val row = MediaEntries.toRow(listOf(entry(position = 60_000, duration = 600_000)), "Continue")
        assertNotNull(row)
        assertEquals(1, row!!.posters.size)
        assertEquals("Episode 1", row.posters.first().title)
        assertEquals(0.1f, row.posters.first().progress!!, 0.001f)
    }

    @Test
    fun dropsSessionsWithoutUsableProgress() {
        assertNull(MediaEntries.toRow(listOf(entry(position = 0, duration = 600_000)), "Continue"))
        assertNull(MediaEntries.toRow(listOf(entry(position = 600_000, duration = 600_000)), "Continue"))
        assertNull(MediaEntries.toRow(listOf(entry(duration = 0)), "Continue"))
        assertNull(MediaEntries.toRow(listOf(entry(title = "   ")), "Continue"))
    }

    @Test
    fun oneCardPerAppNewestFirst() {
        val row = MediaEntries.toRow(
            listOf(
                entry(pkg = "a", title = "A old", updated = 10),
                entry(pkg = "a", title = "A new", updated = 20),
                entry(pkg = "b", title = "B", updated = 15),
            ),
            "Continue",
        )
        assertNotNull(row)
        assertEquals(listOf("A new", "B"), row!!.posters.map { it.title })
    }

    @Test
    fun respectsTheCardLimit() {
        val many = (1..12).map { entry(pkg = "p$it", title = "T$it", updated = it.toLong()) }
        val row = MediaEntries.toRow(many, "Continue", limit = 5)
        assertNotNull(row)
        assertEquals(5, row!!.posters.size)
        assertTrue(row.posters.first().title == "T12")
    }
}
