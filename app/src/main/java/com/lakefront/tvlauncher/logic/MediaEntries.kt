package com.lakefront.tvlauncher.logic

/** One active media session, as far as the launcher is allowed to see it. */
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
 * Turns active media sessions into a continue-watching row.
 *
 * Watch Next is not usable on devices whose content apps never publish to TvProvider, and even
 * when they do, a normal app only sees rows that are marked searchable. The media sessions that
 * the system already tracks ("what is playing, how far in") are a source every launcher can read
 * once the user grants notification access, which is what Google's own TV launcher does.
 */
object MediaEntries {
    const val MAX_CARDS = 8

    fun toRow(entries: List<MediaEntry>, title: String, limit: Int = MAX_CARDS): PosterRow? {
        val posters = entries
            .asSequence()
            .filter { entry -> entry.title.isNotBlank() }
            // Only sessions with a usable position: "started but not finished" is what the row is
            // for. A live stream or a music track without a duration has nothing to resume.
            .filter { entry -> entry.durationMs > 0L && entry.positionMs > 0L && entry.positionMs < entry.durationMs }
            .sortedByDescending { entry -> entry.updatedAtMs }
            .distinctBy { entry -> entry.packageName }
            .take(limit)
            .map { entry ->
                Poster(
                    id = "media:${entry.packageName}",
                    title = entry.title,
                    artUri = entry.artUri,
                    intentUri = entry.intentUri,
                    progress = TvMapper.playbackFraction(entry.positionMs, entry.durationMs),
                    detail = entry.subtitle?.takeIf { it.isNotBlank() },
                )
            }
            .toList()
        if (posters.isEmpty()) return null
        return PosterRow(id = ROW_ID, title = title, posters = posters)
    }

    const val ROW_ID = "continue_watching"
}
