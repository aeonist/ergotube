package app.freshtuber.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.freshtuber.MainActivity
import app.freshtuber.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** System media controls; the activity binds with a MediaController, including on Android 13+. */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var controller: PlayerController
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val next = SessionCommand("app.freshtuber.NEXT", Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, controller.player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, info: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(next).build())
                        .build()

                override fun onCustomCommand(
                    session: MediaSession, info: MediaSession.ControllerInfo, command: SessionCommand, args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (command.customAction != next.customAction) {
                        return Futures.immediateFuture(SessionResult(androidx.media3.session.SessionError.ERROR_NOT_SUPPORTED))
                    }
                    controller.playNext()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
        scope.launch {
            controller.state.collect { state ->
                session?.setMediaButtonPreferences(listOf(
                    CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
                        .setDisplayName(getString(R.string.player_back_10))
                        .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                        .setSlots(CommandButton.SLOT_BACK).build(),
                    CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
                        .setDisplayName(getString(R.string.player_forward_10))
                        .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                        .setSlots(CommandButton.SLOT_FORWARD).build(),
                    CommandButton.Builder(CommandButton.ICON_NEXT)
                        .setDisplayName(getString(R.string.player_next))
                        .setSessionCommand(next)
                        .setEnabled(state.queue.isNotEmpty())
                        .setSlots(CommandButton.SLOT_OVERFLOW).build(),
                ))
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!isPlaybackOngoing) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }
}
