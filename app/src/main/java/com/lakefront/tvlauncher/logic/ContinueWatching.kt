package com.lakefront.tvlauncher.logic

/**
 * Keeps the continue-watching list alive after a media session is gone.
 *
 * A TV player usually drops its session the moment it is backgrounded, so reading only live
 * sessions leaves the row empty exactly when the home screen is visible. The launcher therefore
 * samples sessions while they exist, stores a snapshot, and merges it back in later.
 */
object ContinueWatching {
    const val MAX_ENTRIES = 12
    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    /** Anything this close to the end counts as finished and is dropped instead of resumed. */
    const val FINISHED_MARGIN_MS = 30_000L

    /** Only long-form items are treated as "finished"; ads and clips are short and end constantly. */
    const val FINISHED_MIN_DURATION_MS = 120_000L

    fun isFinished(entry: MediaEntry): Boolean =
        entry.durationMs >= FINISHED_MIN_DURATION_MS &&
            entry.positionMs >= entry.durationMs - FINISHED_MARGIN_MS

    /**
     * True when the entry is worth showing or keeping.
     *
     * Position is deliberately not required: some TV players report a title and a playing state
     * but keep position at 0, and a card with no progress bar is still more useful than nothing.
     */
    fun isResumable(entry: MediaEntry): Boolean =
        entry.title.isNotBlank() && !isFinished(entry)

    /**
     * Newest wins per package. A finished item removes the stored one (you do not want to resume
     * what you just watched to the end), and anything older than [MAX_AGE_MS] falls away.
     */
    fun merge(
        stored: List<MediaEntry>,
        incoming: List<MediaEntry>,
        nowMs: Long,
    ): List<MediaEntry> {
        val byPackage = LinkedHashMap<String, MediaEntry>()
        (stored + incoming)
            .filter { it.title.isNotBlank() }
            .sortedBy { it.updatedAtMs }
            .forEach { entry ->
                if (isFinished(entry)) {
                    byPackage.remove(entry.packageName)
                } else if (entry.title.isNotBlank()) {
                    val previous = byPackage[entry.packageName]
                    if (previous == null || entry.updatedAtMs >= previous.updatedAtMs) {
                        byPackage[entry.packageName] = entry
                    }
                }
            }
        return byPackage.values
            .filter { nowMs - it.updatedAtMs <= MAX_AGE_MS }
            .sortedByDescending { it.updatedAtMs }
            .take(MAX_ENTRIES)
    }
}
