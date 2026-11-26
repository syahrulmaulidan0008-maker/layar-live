package com.layar.live

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata.*
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow

data class Track(
    val title: String = "", val artist: String = "", val cover: Bitmap? = null,
    val playing: Boolean = false, val pos: Long = 0, val dur: Long = 0,
    val stamp: Long = 0, val speed: Float = 1f
)

object NowPlaying { val state = MutableStateFlow(Track()) }

/** Membaca lagu dari Spotify / YT Music / dll lewat MediaSession (butuh izin Notification Access). */
class MusicListener : NotificationListenerService() {
    private var ctrl: MediaController? = null
    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
    }

    override fun onListenerConnected() {
        val cn = ComponentName(this, MusicListener::class.java)
        val msm = getSystemService(MediaSessionManager::class.java)
        msm.addOnActiveSessionsChangedListener({ pick(it) }, cn)
        pick(msm.getActiveSessions(cn))
    }

    private fun pick(l: List<MediaController>?) {
        ctrl?.unregisterCallback(cb)
        ctrl = l?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: l?.firstOrNull()
        ctrl?.registerCallback(cb)
        publish()
    }

    private fun publish() {
        val c = ctrl ?: return
        val m = c.metadata; val s = c.playbackState
        NowPlaying.state.value = Track(
            title = m?.getString(METADATA_KEY_TITLE) ?: "",
            artist = m?.getString(METADATA_KEY_ARTIST) ?: m?.getString(METADATA_KEY_ALBUM_ARTIST) ?: "",
            cover = m?.getBitmap(METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(METADATA_KEY_ART),
            playing = s?.state == PlaybackState.STATE_PLAYING,
            pos = s?.position ?: 0, dur = m?.getLong(METADATA_KEY_DURATION) ?: 0,
            stamp = s?.lastPositionUpdateTime ?: 0, speed = s?.playbackSpeed ?: 1f
        )
    }
}
