package com.haptiq.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.haptiq.app.MainActivity
import com.haptiq.app.data.HaptiqPlayerManager

/**
 * MediaSessionService for background playback.
 * Provides system Media Notification, lock screen controls, and background playback.
 * Uses the shared ExoPlayer instance from HaptiqPlayerManager.
 */
class HaptiqPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // Bind to the existing singleton ExoPlayer from HaptiqPlayerManager
        val playerManager = HaptiqPlayerManager.getInstance(applicationContext)
        val player = playerManager.getPlayer() ?: return

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            // DO NOT release player here. The player is a singleton owned by HaptiqPlayerManager
            // and should survive the service lifecycle.
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
