package com.lakefront.tvlauncher.debug

import android.app.Activity
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Debug-only: publishes a media session with rich metadata so the behaviour of other launchers
 * can be observed. Not part of the release build.
 */
class FakeSessionActivity : Activity() {
    private var session: MediaSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = MediaSession(this, "LakeFakeSession")
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "周杰伦 - 怎么就听不腻的30首叙事曲串烧")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "LAKE TEST SESSION")
                .putString(
                    MediaMetadata.METADATA_KEY_ART_URI,
                    Uri.parse("https://picsum.photos/id/1025/640/360").toString(),
                )
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
        Log.i("LakeTv", "fake session active: 周杰伦 test item 600000/1800000")
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
