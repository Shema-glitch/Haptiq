package com.haptiq.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.haptiq.app.MainActivity
import com.haptiq.app.data.PlayerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * MediaSessionService for background playback.
 * Provides system Media Notification, lock screen controls, and background playback.
 * Uses the shared ExoPlayer instance from HaptiqPlayerManager, obtained via Hilt field
 * injection (Android instantiates this Service, so constructor injection isn't available).
 */
@AndroidEntryPoint
class HaptiqPlaybackService : MediaSessionService() {

    @Inject lateinit var playerManager: PlayerManager

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // Provide an immediate dummy notification to prevent RemoteServiceException 
        // if Media3 takes too long to build its own notification.
        val channelId = "haptiq_playback"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Playback", android.app.NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
        val dummyNotification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Haptiq")
            .setContentText("Preparing playback...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1001, dummyNotification)

        // Bind to the existing singleton ExoPlayer from HaptiqPlayerManager (injected above)
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
