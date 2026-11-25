package com.lakefront.tvlauncher.media

import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import com.lakefront.tvlauncher.data.ContinueWatchingStore
import com.lakefront.tvlauncher.data.MediaSessionRepository

/**
 * Keeps a snapshot of what is being watched.
 *
 * Declaring this service is also what lets the user grant notification access, which is the only
 * way a normal app can read other apps' media sessions. Sampling matters more than it looks: a TV
 * player drops its session when it goes to the background, so if nobody samples while it plays,
 * the launcher has nothing to show once it comes back to the foreground.
 */
class NowPlayingListenerService : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private var store: ContinueWatchingStore? = null
    private var repository: MediaSessionRepository? = null

    private val sampler = object : Runnable {
        override fun run() {
            sample()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        store = ContinueWatchingStore(this)
        repository = MediaSessionRepository(this)
        handler.removeCallbacks(sampler)
        handler.post(sampler)
    }

    override fun onListenerDisconnected() {
        handler.removeCallbacks(sampler)
        store = null
        repository = null
        super.onListenerDisconnected()
    }

    private fun sample() {
        val store = store ?: return
        val repository = repository ?: return
        try {
            val live = repository.load()
            if (live.isEmpty()) return
            val merged = store.record(live)
            val first = live.first()
            Log.i(
                TAG,
                "continue watching: ${live.size} live, ${merged.size} stored; " +
                    "first='${first.title}' ${first.positionMs}/${first.durationMs} pkg=${first.packageName}",
            )
        } catch (error: Exception) {
            Log.i(TAG, "session sample failed: ${error.message}")
        }
    }

    private companion object {
        const val TAG = "LakeTv"
        const val INTERVAL_MS = 5_000L
    }
}
