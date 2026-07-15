package com.haptiq.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.haptiq.app.MainActivity
import com.haptiq.app.R
import com.haptiq.app.data.PlayerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * MediaSessionService for background playback.
 *
 * IMPORTANT: no manual startForeground / placeholder notification here. Media3 owns
 * the media notification (posting, foreground promotion when playing, demotion when
 * paused). The previous "Preparing playback..." placeholder was posted at the SAME
 * notification id Media3 uses (1001) before the session existed, which is why users
 * only ever saw the placeholder and no transport controls.
 *
 * Uses the shared ExoPlayer instance from HaptiqPlayerManager via Hilt field injection
 * (Android instantiates this Service, so constructor injection isn't available).
 */
@AndroidEntryPoint
class HaptiqPlaybackService : MediaSessionService() {

    companion object {
        private const val CMD_TOGGLE_HAPTICS = "com.haptiq.app.TOGGLE_HAPTICS"
    }

    @Inject lateinit var playerManager: PlayerManager

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = playerManager.getPlayer() ?: run {
            stopSelf()
            return
        }

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(HaptiqSessionCallback())
            .setCustomLayout(listOf(hapticsButton()))
            .build()
        mediaSession = session

        // REQUIRED for the notification to exist at all: this app's UI drives the
        // player directly (no MediaController ever connects), so onGetSession is
        // never called and the service would otherwise hold zero sessions — and a
        // MediaSessionService only posts/updates the media notification for
        // sessions it knows about. Register the session explicitly.
        addSession(session)
    }

    /** The one Haptiq-specific control: toggle haptics without opening the app. */
    private fun hapticsButton(): CommandButton {
        val active = playerManager.hapticActive.value
        return CommandButton.Builder()
            .setDisplayName(if (active) "Haptics on" else "Haptics off")
            .setIconResId(R.drawable.ic_vibration)
            .setSessionCommand(SessionCommand(CMD_TOGGLE_HAPTICS, Bundle.EMPTY))
            .build()
    }

    private inner class HaptiqSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(SessionCommand(CMD_TOGGLE_HAPTICS, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_TOGGLE_HAPTICS) {
                playerManager.setHapticActive(!playerManager.hapticActive.value)
                session.setCustomLayout(listOf(hapticsButton()))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
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
