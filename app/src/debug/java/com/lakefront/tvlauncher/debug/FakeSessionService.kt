package com.lakefront.tvlauncher.debug

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.IBinder
import android.util.Log

/**
 * Debug-only: keeps a media session with rich metadata alive so other launchers (Google TV's
 * included) can be observed reacting to it. Started with:
 *     adb shell am startservice -n com.lakefront.tvlauncher/com.lakefront.tvlauncher.debug.FakeSessionService
 * Stopped with:
 *     adb shell am stopservice -n com.lakefront.tvlauncher/com.lakefront.tvlauncher.debug.FakeSessionService
 */
class FakeSessionService : Service() {
    private var session: MediaSession? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (session == null) {
            val session = MediaSession(this, "LakeFakeSession")
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "周杰伦 - 怎么就听不腻的30首叙事曲串烧")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "LAKE TEST SESSION")
                    .putString(
                        MediaMetadata.METADATA_KEY_ART_URI,
                        Uri.parse("https://picsum.photos/id/1025/640/360").toString(),
                    )
                    .putBitmap(MediaMetadata.METADATA_KEY_ART, testArtwork())
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, 1_800_000L)
                    .build(),
            )
            session.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING, 600_000L, 1f)
                    .build(),
            )
            session.isActive = true
            this.session = session
            Log.i("LakeTv", "fake session service active: 周杰伦 test item 600000/1800000")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** A recognisable cover, so it is obvious whether a launcher used the bitmap or the URI. */
    private fun testArtwork(): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(20, 60, 110))
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 56f
            isAntiAlias = true
        }
        canvas.drawText("LAKE TV", 40f, 170f, paint)
        paint.textSize = 36f
        canvas.drawText("bitmap art test", 40f, 240f, paint)
        return bitmap
    }
}
