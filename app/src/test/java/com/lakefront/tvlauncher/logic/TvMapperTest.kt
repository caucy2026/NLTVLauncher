package com.lakefront.tvlauncher.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvMapperTest {
    @Test
    fun permissionAndProviderShortCircuit() {
        val rows = listOf(RawProgram(id = 1, title = "Should not show"))
        val denied = TvMapper.map(TvInput(TvAccess.PermissionDenied, watchNext = rows))
        assertEquals(TvSnapshot.Unavailable(TvUnavailable.PermissionDenied), denied)
        val missing = TvMapper.map(TvInput(TvAccess.ProviderMissing, watchNext = rows))
        assertEquals(TvSnapshot.Unavailable(TvUnavailable.ProviderMissing), missing)
    }

    @Test
    fun emptyInputIsEmptyNotFakeRows() {
        val snapshot = TvMapper.map(TvInput(TvAccess.Ok))
        assertEquals(TvSnapshot.Unavailable(TvUnavailable.Empty), snapshot)
    }

    @Test
    fun watchNextHidesWhenEmptyAndKeepsRecommendationRow() {
        val channel = RawChannel(id = 7, displayName = "Top picks", packageName = "app.video", browsable = true, type = "TYPE_PREVIEW")
        val program = RawProgram(
            id = 3,
            channelId = 7,
            title = "Salt and Signal",
            posterUri = "content://art/3",
            intentUri = "intent://play#Intent;end",
        )
        val snapshot = TvMapper.map(
            TvInput(
                access = TvAccess.Ok,
                channels = listOf(channel),
                programs = listOf(program),
                appLabels = mapOf("app.video" to "Harbor"),
            ),
        )
        val ready = snapshot as TvSnapshot.Ready
        assertEquals(1, ready.rows.size)
        assertEquals("Harbor · Top picks", ready.rows.single().title)
        assertEquals("Salt and Signal", ready.rows.single().posters.single().title)
        assertNull(ready.rows.single().posters.single().detail)
    }

    @Test
    fun watchNextProgressArtworkAndDeadIntent() {
        val halfHour = 32 * 60_000L
        val program = RawProgram(
            id = 9,
            title = "Between Mountains",
            thumbnailUri = "http://example/thumb",
            posterUri = " ",
            intentUri = "intent:#Intent;component=app/.Play;end",
            positionMs = halfHour / 2,
            durationMs = halfHour,
            engagementMs = 50,
        )
        val blank = RawProgram(id = 10, title = "   ", engagementMs = 100)
        val finished = RawProgram(
            id = 11,
            title = "Finished",
            positionMs = 1_000,
            durationMs = 1_000,
            engagementMs = 10,
        )
        val snapshot = TvMapper.map(TvInput(TvAccess.Ok, watchNext = listOf(finished, blank, program)))
        val ready = snapshot as TvSnapshot.Ready
        assertEquals("Watch Next", ready.rows.single().title)
        val posters = ready.rows.single().posters
        assertEquals(listOf("Between Mountains", "Finished"), posters.map { it.title })
        val first = posters.first()
        assertEquals("http://example/thumb", first.artUri)
        assertEquals("32 min", first.detail)
        assertEquals(0.5f, first.progress!!, 0.001f)
        assertEquals("intent:#Intent;component=app/.Play;end", first.intentUri)
        assertNull(posters[1].progress)
    }

    @Test
    fun skipsNonBrowsableNonPreviewAndBlankArtUsesThumbnail() {
        val live = RawChannel(id = 1, displayName = "News", browsable = true, type = "TYPE_OTHER")
        val hidden = RawChannel(id = 2, displayName = "Hidden", browsable = false, type = "TYPE_PREVIEW")
        val preview = RawChannel(id = 3, displayName = "YouTube", packageName = "com.example.tube", browsable = true, type = "TYPE_PREVIEW")
        val programs = listOf(
            RawProgram(id = 1, channelId = 1, title = "Live show"),
            RawProgram(id = 2, channelId = 2, title = "Secret"),
            RawProgram(id = 3, channelId = 3, title = "Real video", thumbnailUri = "content://thumb", browsable = true),
            RawProgram(id = 4, channelId = 3, title = "Hidden video", browsable = false),
            RawProgram(id = 5, channelId = 3, title = "", posterUri = "content://x"),
        )
        val snapshot = TvMapper.map(
            TvInput(
                access = TvAccess.Ok,
                channels = listOf(live, hidden, preview),
                programs = programs,
                appLabels = mapOf("com.example.tube" to "YouTube"),
            ),
        )
        val ready = snapshot as TvSnapshot.Ready
        assertEquals(1, ready.rows.size)
        assertEquals("YouTube", ready.rows.single().title)
        assertEquals(listOf("Real video"), ready.rows.single().posters.map { it.title })
        assertEquals("content://thumb", ready.rows.single().posters.single().artUri)
    }

    @Test
    fun durationAndFailureClassification() {
        assertEquals("1 min", TvMapper.formatDuration(30_000))
        assertEquals("32 min", TvMapper.formatDuration(32 * 60_000L))
        assertEquals("2 hr", TvMapper.formatDuration(2 * 60 * 60_000L))
        assertEquals("1 hr 5 min", TvMapper.formatDuration((65 * 60_000L)))
        assertNull(TvMapper.formatDuration(0))
        assertNull(TvMapper.playbackFraction(0, 10_000))
        assertNull(TvMapper.playbackFraction(10_000, 10_000))
        assertNull(TvMapper.sanitizeIntentUri("   "))
        assertEquals(TvAccess.PermissionDenied, TvFailures.classify("SecurityException", "Permission denial"))
        assertEquals(TvAccess.ProviderMissing, TvFailures.classify("IllegalArgumentException", "Unknown URI"))
    }

    @Test
    fun rowTitleFallbacks() {
        assertEquals("Recommendations", TvMapper.rowTitle(null, null))
        assertEquals("Prime Video", TvMapper.rowTitle(" ", "Prime Video"))
        assertEquals("Continue", TvMapper.rowTitle("Continue", null))
        assertEquals("Prime Video · Movies", TvMapper.rowTitle("Movies", "Prime Video"))
    }
}
