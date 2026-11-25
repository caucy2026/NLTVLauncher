package com.lakefront.tvlauncher.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class TvFailuresTest {
    @Test
    fun missingProviderIsNotReportedAsEmpty() {
        // ADT-3 style build: the authority simply does not exist.
        assertEquals(
            TvAccess.ProviderMissing,
            TvFailures.classify(
                "IllegalStateException",
                "Could not find provider: android.media.tv",
            ),
        )
        assertEquals(
            TvAccess.ProviderMissing,
            TvFailures.classify(
                "IllegalArgumentException",
                "Unknown URL content://android.media.tv/watch_next_program",
            ),
        )
        assertEquals(
            TvAccess.ProviderMissing,
            TvFailures.classify("IllegalArgumentException", "Unknown authority android.media.tv"),
        )
    }

    @Test
    fun permissionFailuresStayDistinct() {
        assertEquals(
            TvAccess.PermissionDenied,
            TvFailures.classify("SecurityException", "Permission Denial: reading WatchNextPrograms"),
        )
        assertEquals(
            TvAccess.PermissionDenied,
            TvFailures.classify("SecurityException", null),
        )
    }

    @Test
    fun otherFailuresAreNotBlamedOnAMissingProvider() {
        // A broken database or an unexpected runtime error is not "no TV provider".
        assertEquals(
            TvAccess.Unknown,
            TvFailures.classify("SQLiteException", "unable to open database file"),
        )
        assertEquals(
            TvAccess.Unknown,
            TvFailures.classify("IllegalStateException", "database not initialized"),
        )
        assertEquals(
            TvAccess.Unknown,
            TvFailures.classify("NullPointerException", null),
        )
    }

    @Test
    fun missingProviderMapsToProviderMissingSnapshot() {
        val snapshot = TvMapper.map(TvInput(TvAccess.ProviderMissing))
        assertEquals(
            TvSnapshot.Unavailable(TvUnavailable.ProviderMissing),
            snapshot,
        )
    }

    @Test
    fun unknownFailureMapsToUnknownSnapshot() {
        val snapshot = TvMapper.map(TvInput(TvAccess.Unknown))
        assertEquals(TvSnapshot.Unavailable(TvUnavailable.Unknown), snapshot)
    }

    @Test
    fun emptyListingStillReportsEmpty() {
        val snapshot = TvMapper.map(TvInput(TvAccess.Ok))
        assertEquals(TvSnapshot.Unavailable(TvUnavailable.Empty), snapshot)
    }
}
