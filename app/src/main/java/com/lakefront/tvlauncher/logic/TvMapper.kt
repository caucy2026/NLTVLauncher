package com.lakefront.tvlauncher.logic

object TvMapper {
    fun map(input: TvInput): TvSnapshot {
        when (input.access) {
            TvAccess.ProviderMissing -> return TvSnapshot.Unavailable(TvUnavailable.ProviderMissing)
            TvAccess.PermissionDenied -> return TvSnapshot.Unavailable(TvUnavailable.PermissionDenied)
            TvAccess.ListingsUnsupported ->
                return TvSnapshot.Unavailable(TvUnavailable.ListingsUnsupported)
            TvAccess.Ok -> Unit
        }

        val rows = ArrayList<PosterRow>()
        val watchPosters = input.watchNext
            .asSequence()
            .filter { it.browsable }
            .sortedWith(compareByDescending<RawProgram> { it.engagementMs }.thenBy { it.id })
            .mapNotNull { program -> toPoster(program, watchNext = true) }
            .distinctBy { it.id }
            .toList()
        if (watchPosters.isNotEmpty()) {
            rows += PosterRow(
                id = "watch_next",
                title = TvText.WATCH_NEXT,
                posters = watchPosters,
            )
        }

        val channels = input.channels
            .filter { it.browsable && it.type == TvText.PREVIEW_TYPE }
            .sortedWith(compareBy({ it.displayName?.lowercase().orEmpty() }, { it.id }))
        val programsByChannel = input.programs.groupBy { it.channelId }
        for (channel in channels) {
            val posters = (programsByChannel[channel.id] ?: emptyList())
                .asSequence()
                .filter { it.browsable }
                .sortedWith(
                    compareByDescending<RawProgram> { it.weight }
                        .thenByDescending { it.engagementMs }
                        .thenBy { it.id },
                )
                .mapNotNull { program -> toPoster(program, watchNext = false) }
                .distinctBy { it.id }
                .toList()
            if (posters.isEmpty()) continue
            val appLabel = channel.packageName?.let { input.appLabels[it] }
            rows += PosterRow(
                id = "channel:${channel.id}",
                title = rowTitle(channel.displayName, appLabel),
                posters = posters,
            )
        }

        if (rows.isEmpty()) return TvSnapshot.Unavailable(TvUnavailable.Empty)
        return TvSnapshot.Ready(rows)
    }

    fun rowTitle(channelName: String?, appLabel: String?): String {
        val channel = channelName?.trim().orEmpty()
        val app = appLabel?.trim().orEmpty()
        return when {
            channel.isEmpty() && app.isEmpty() -> "Recommendations"
            channel.isEmpty() -> app
            app.isEmpty() -> channel
            channel.contains(app, ignoreCase = true) -> channel
            else -> "$app · $channel"
        }
    }

    fun chooseArt(posterUri: String?, thumbnailUri: String?): String? {
        posterUri?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return thumbnailUri?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun sanitizeIntentUri(uri: String?): String? {
        val value = uri?.trim().orEmpty()
        if (value.isEmpty() || value.length > 4000) return null
        return value
    }

    /** Fraction for a continue-watching bar. Missing, zero, or finished playback stays hidden. */
    fun playbackFraction(positionMs: Long, durationMs: Long): Float? {
        if (durationMs <= 0L || positionMs <= 0L || positionMs >= durationMs) return null
        return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0.02f, 0.98f)
    }

    fun formatDuration(ms: Long): String? {
        if (ms <= 0L) return null
        val totalMin = (ms / 60_000L).toInt()
        if (totalMin < 1) return "1 min"
        if (totalMin < 60) return "$totalMin min"
        val hours = totalMin / 60
        val minutes = totalMin % 60
        return if (minutes == 0) "$hours hr" else "$hours hr $minutes min"
    }

    private fun toPoster(program: RawProgram, watchNext: Boolean): Poster? {
        val title = program.title?.trim().orEmpty()
        if (title.isEmpty()) return null
        return Poster(
            id = if (watchNext) "watch:${program.id}" else "prog:${program.id}",
            title = title,
            artUri = chooseArt(program.posterUri, program.thumbnailUri),
            intentUri = sanitizeIntentUri(program.intentUri),
            progress = playbackFraction(program.positionMs, program.durationMs),
            detail = if (watchNext) formatDuration(program.durationMs) else null,
        )
    }
}

object TvFailures {
    fun classify(exceptionClassSimpleName: String, message: String?): TvAccess {
        val name = exceptionClassSimpleName.lowercase()
        val msg = message?.lowercase().orEmpty()
        val denied = "security" in name ||
            "permission" in name ||
            "securityexception" in msg ||
            "permission denial" in msg
        return if (denied) TvAccess.PermissionDenied else TvAccess.ProviderMissing
    }
}
