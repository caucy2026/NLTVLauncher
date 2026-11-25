package com.lakefront.tvlauncher.data

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.util.Log
import com.lakefront.tvlauncher.logic.MediaEntry
import com.lakefront.tvlauncher.media.NowPlayingListenerService

/**
 * Reads the media sessions the system already knows about.
 *
 * This needs notification access, which the user grants once; [enabled] reports whether that
 * happened so the screen can explain itself instead of silently staying empty.
 */
class MediaSessionRepository(context: Context) {
    private val appContext = context.applicationContext
    private val listener = ComponentName(appContext, NowPlayingListenerService::class.java)
    private val sessions = appContext.getSystemService(MediaSessionManager::class.java)

    fun enabled(): Boolean = try {
        appContext.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(listener)
    } catch (error: Exception) {
        Log.i(TAG, "notification access check failed: ${error.message}")
        false
    }

    fun load(): List<MediaEntry> {
        if (sessions == null || !enabled()) return emptyList()
        val controllers = try {
            sessions.getActiveSessions(listener)
        } catch (error: SecurityException) {
            Log.i(TAG, "media sessions denied: ${error.message}")
            return emptyList()
        } catch (error: Exception) {
            Log.i(TAG, "media sessions unavailable: ${error.message}")
            return emptyList()
        }
        return controllers.mapNotNull { controller -> toEntry(controller) }
    }

    private fun toEntry(controller: MediaController): MediaEntry? {
        val metadata = controller.metadata
        // Players fill different keys: Cobalt leaves METADATA_KEY_TITLE empty for some items but
        // still sets the display title or a queue title, so try them in turn instead of dropping
        // the session outright.
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
            ?: controller.queueTitle?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val subtitle = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val art = metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
        val state = controller.playbackState
        // The session's own PendingIntent is not an Intent URI, so reuse the app's launch intent:
        // tapping the card reopens the player that owns the session.
        val launch = appContext.packageManager.getLaunchIntentForPackage(controller.packageName)
        val intentUri = launch?.toUri(Intent.URI_INTENT_SCHEME)?.toString()
        return MediaEntry(
            packageName = controller.packageName,
            title = title,
            subtitle = subtitle?.trim(),
            positionMs = state?.position ?: 0L,
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            artUri = art,
            intentUri = intentUri,
            // Wall-clock time of the sample: PlaybackState.lastPositionUpdateTime is uptime, which
            // makes every entry look decades old to the expiry rule.
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    private companion object {
        const val TAG = "LakeTv"
    }
}
