package app.freshtuber.player.music

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The music notification and lock screen controls: previous, play, next, seeking, and two of
 * its own buttons, «В любимое» and «Повтор». Separate from the video's [app.freshtuber.player.PlaybackService]
 * so a paused song keeps its notification while a video plays.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MusicService : MediaSessionService() {
    @Inject lateinit var music: MusicPlayer
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val favorite = SessionCommand(ACTION_FAVORITE, Bundle.EMPTY)
    private val repeat = SessionCommand(ACTION_REPEAT, Bundle.EMPTY)

    /** Headset button presses counted into one, two or three. */
    private var presses = 0
    private val pressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pressDone = Runnable {
        when (presses) {
            1 -> music.toggle()
            2 -> music.next()
            else -> music.previous()
        }
        presses = 0
    }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(NOTIFICATION_ID).setChannelId(CHANNEL_ID).setChannelName(R.string.music_channel).build())
        val open = PendingIntent.getActivity(this, 7, Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_MUSIC, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val forwarding = object : ForwardingPlayer(music.player) {
            override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_PREVIOUS).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_PLAY_PAUSE).build()

            override fun isCommandAvailable(command: Int): Boolean = command in NAVIGATION || super.isCommandAvailable(command)
            override fun seekToPrevious() = music.previous()
            override fun seekToPreviousMediaItem() = music.previous()
            override fun seekToNext() = music.next()
            override fun seekToNextMediaItem() = music.next()
            override fun play() = music.play()
            override fun setPlayWhenReady(playWhenReady: Boolean) { if (playWhenReady) music.play() else music.pause() }
        }
        session = MediaSession.Builder(this, forwarding)
            .setId("music")
            .setSessionActivity(open)
            .setMediaButtonPreferences(buttons(false, RepeatMode.Off))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, info: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(favorite).add(repeat).build())
                        .build()

                override fun onCustomCommand(session: MediaSession, info: MediaSession.ControllerInfo, command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    when (command.customAction) {
                        ACTION_FAVORITE -> music.state.value.queue.track?.let { track -> scope.launch { music.toggleFavorite(track) } }
                        ACTION_REPEAT -> music.cycleRepeat()
                        else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                override fun onMediaButtonEvent(session: MediaSession, info: MediaSession.ControllerInfo, intent: Intent): Boolean {
                    val event = intent.getParcelableExtraCompat() ?: return false
                    val down = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT -> { if (down) music.next(); return true }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { if (down) music.previous(); return true }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> { if (down) music.play(); return true }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (down) music.pause(); return true }
                        KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> Unit
                        else -> return false
                    }
                    if (down) {
                        presses++
                        pressHandler.removeCallbacks(pressDone)
                        if (presses >= 3) pressDone.run() else pressHandler.postDelayed(pressDone, PRESS_WINDOW_MS)
                    }
                    return true
                }
            })
            .build()
        scope.launch {
            combine(music.state, music.favoriteIds) { state, favorites ->
                (state.queue.track?.id?.let { it in favorites } == true) to state.queue.repeat
            }.distinctUntilChanged().collect { (liked, mode) -> session?.setMediaButtonPreferences(buttons(liked, mode)) }
        }
    }

    private fun buttons(liked: Boolean, mode: RepeatMode): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_PREVIOUS).setDisplayName(getString(R.string.music_previous))
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS).setSlots(CommandButton.SLOT_BACK).build(),
        CommandButton.Builder(CommandButton.ICON_NEXT).setDisplayName(getString(R.string.music_next))
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT).setSlots(CommandButton.SLOT_FORWARD).build(),
        CommandButton.Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(getString(if (liked) R.string.music_unfavorite else R.string.music_favorite))
            .setSessionCommand(favorite).setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW).build(),
        CommandButton.Builder(when (mode) { RepeatMode.Off -> CommandButton.ICON_REPEAT_OFF; RepeatMode.All -> CommandButton.ICON_REPEAT_ALL; RepeatMode.One -> CommandButton.ICON_REPEAT_ONE })
            .setDisplayName(getString(R.string.music_repeat)).setSessionCommand(repeat)
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW).build(),
    )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!isPlaybackOngoing) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        pressHandler.removeCallbacks(pressDone)
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun Intent.getParcelableExtraCompat(): KeyEvent? =
        if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(Intent.EXTRA_KEY_EVENT)

    companion object {
        const val NOTIFICATION_ID = 2207
        const val CHANNEL_ID = "music_playback"
        private const val ACTION_FAVORITE = "app.freshtuber.music.FAVORITE"
        private const val ACTION_REPEAT = "app.freshtuber.music.REPEAT"
        private const val PRESS_WINDOW_MS = 420L
        private val NAVIGATION = setOf(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
    }
}
