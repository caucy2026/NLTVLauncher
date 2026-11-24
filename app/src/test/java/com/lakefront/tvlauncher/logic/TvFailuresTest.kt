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
                "Could not find provider: android.tvprovider",
            ),
        )
        assertEquals(
            TvAccess.ProviderMissing,
            TvFailures.classify(
                "IllegalArgumentException",
                "Unknown URL content://android.tvprovider/watch_next/program",
            ),
        )
        assertEquals(
            TvAccess.ProviderMissing,
            TvFailures.classify("NullPointerException", null),
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
    fun missingProviderMapsToProviderMissingSnapshot() {
        val snapshot = TvMapper.map(TvInput(TvAccess.ProviderMissing))
        assertEquals(
            TvSnapshot.Unavailable(TvUnavailable.ProviderMissing),
            snapshot,
        )
    }

    @Test
    fun emptyListingStillReportsEmpty() {
        val snapshot = TvMapper.map(TvInput(TvAccess.Ok))
        assertEquals(TvSnapshot.Unavailable(TvUnavailable.Empty), snapshot)
    }
}
