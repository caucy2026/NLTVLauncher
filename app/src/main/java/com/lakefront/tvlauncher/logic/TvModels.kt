package com.lakefront.tvlauncher.logic

enum class TvAccess {
    Ok,
    PermissionDenied,
    ProviderMissing,

    /** A TV provider answers, but it has none of the Watch Next tables. */
    ListingsUnsupported,
}

enum class TvUnavailable {
    Empty,
    PermissionDenied,
    ProviderMissing,
    ListingsUnsupported,
}

data class RawProgram(
    val id: Long,
    val channelId: Long? = null,
    val title: String? = null,
    val posterUri: String? = null,
    val thumbnailUri: String? = null,
    val intentUri: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val packageName: String? = null,
    val engagementMs: Long = 0L,
    val weight: Int = 0,
    val browsable: Boolean = true,
)

data class RawChannel(
    val id: Long,
    val displayName: String? = null,
    val packageName: String? = null,
    val browsable: Boolean = false,
    val type: String? = null,
)

data class Poster(
    val id: String,
    val title: String,
    val artUri: String?,
    val intentUri: String?,
    val progress: Float?,
    val detail: String?,
)

data class PosterRow(
    val id: String,
    val title: String,
    val posters: List<Poster>,
)

sealed interface TvSnapshot {
    data class Ready(val rows: List<PosterRow>) : TvSnapshot
    data class Unavailable(val reason: TvUnavailable) : TvSnapshot
}

data class TvInput(
    val access: TvAccess,
    val watchNext: List<RawProgram> = emptyList(),
    val channels: List<RawChannel> = emptyList(),
    val programs: List<RawProgram> = emptyList(),
    val appLabels: Map<String, String> = emptyMap(),
)

object TvText {
    const val WATCH_NEXT = "Watch Next"
    const val PREVIEW_TYPE = "TYPE_PREVIEW"
}

enum class RadioState {
    Absent,
    Off,
    On,
    Connected,
    NeedsPermission,
}

data class SystemStatus(
    val time: String,
    val wifi: RadioState,
    val bluetooth: RadioState,
    val usbMounted: Boolean = false,
)
