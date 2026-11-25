package com.lakefront.tvlauncher.logic

/** One media session, as far as the launcher is allowed to see it. */
data class MediaEntry(
    val packageName: String,
    val title: String,
    val subtitle: String?,
    val positionMs: Long,
    val durationMs: Long,
    val artUri: String?,
    val intentUri: String?,
    val updatedAtMs: Long,
)

/**
 * Turns media sessions (live ones and stored snapshots) into a continue-watching row.
 *
 * Watch Next cannot carry this on devices whose content apps never publish to TvProvider, and a
 * normal app would only see rows marked searchable anyway. Media sessions are a source every
 * launcher can read once the user grants notification access.
 */
object MediaEntries {
    const val MAX_CARDS = 8
    const val ROW_ID = "continue_watching"

    fun toRow(entries: List<MediaEntry>, title: String, limit: Int = MAX_CARDS): PosterRow? {
        val posters = entries
            .asSequence()
            .filter { entry -> ContinueWatching.isResumable(entry) }
            .sortedByDescending { entry -> entry.updatedAtMs }
            .distinctBy { entry -> entry.packageName }
            .take(limit)
            .map { entry ->
                Poster(
                    id = "media:${entry.packageName}",
                    title = entry.title,
                    artUri = entry.artUri,
                    intentUri = entry.intentUri,
                    // A player that reports no duration still has something to resume; it just
                    // cannot draw a progress bar.
                    progress = TvMapper.playbackFraction(entry.positionMs, entry.durationMs),
                    detail = entry.subtitle?.takeIf { it.isNotBlank() },
                )
            }
            .toList()
        if (posters.isEmpty()) return null
        return PosterRow(id = ROW_ID, title = title, posters = posters)
    }
}
