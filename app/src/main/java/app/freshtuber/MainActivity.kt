package app.freshtuber

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.freshtuber.data.settings.AppFeature
import app.freshtuber.data.settings.AppPreferences
import app.freshtuber.data.settings.ThemeMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.lifecycleScope
import app.freshtuber.core.newpipe.NewPipeInitializer
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.player.PlayerController
import app.freshtuber.ui.AppRoot
import app.freshtuber.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playerController: PlayerController
    @Inject lateinit var settings: AppSettings

    private var externalVideoId by mutableStateOf<String?>(null)
    private var inPictureInPicture by mutableStateOf(false)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pipOnLeave = true
    private var backgroundPlayback = true
    private var leftPictureInPicture = false
    private var stopped = false
    private var audioOnlyRequested = false
    private var wasInPictureInPicture = false
    private var videoBounds: android.graphics.Rect? = null
    private val reportVideoBounds: (android.graphics.Rect) -> Unit = { bounds ->
        if (!isInPictureInPictureMode && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
            bounds.left >= 0 && bounds.top >= 0 && bounds.right <= window.decorView.width && bounds.bottom <= window.decorView.height &&
            bounds.width() > 0 && bounds.height() > 0 && bounds != videoBounds) {
            videoBounds = bounds
            refreshPictureInPicture()
        }
    }

    /** Floating-window commands: background audio, playback and complete dismissal. */
    private val pipReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            when (intent?.getIntExtra(PIP_COMMAND, -1)) {
                PIP_PREVIOUS -> playerController.skipBack()
                PIP_TOGGLE -> playerController.togglePlayPause()
                PIP_NEXT -> playerController.skipForward()
                PIP_BACKGROUND -> {
                    audioOnlyRequested = true
                    playerController.enterAudioOnly()
                    finish()
                }
                PIP_CLOSE -> { playerController.stop(); finishAndRemoveTask() }
            }
        }
    }
    private val pipPlayerListener = object : androidx.media3.common.Player.Listener {
        override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) = refreshPictureInPicture()
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) = refreshPictureInPicture()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        acceptIntent(intent)
        // Keeps the content region in sync with the per-app language (Android 13+), which the
        // activity sees even when the Application context does not.
        val uiLocale = ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()
        NewPipeInitializer.updateLocale(uiLocale)
        lifecycleScope.launch { settings.pipOnLeave.collect { pipOnLeave = it; refreshPictureInPicture() } }
        lifecycleScope.launch { settings.backgroundPlayback.collect { backgroundPlayback = it } }
        androidx.core.content.ContextCompat.registerReceiver(this, pipReceiver, android.content.IntentFilter(PIP_ACTION),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        playerController.player.addListener(pipPlayerListener)
        lifecycleScope.launch { playerController.videoVisible.collect { refreshPictureInPicture() } }
        // Previous / next turn on and off with the queue.
        lifecycleScope.launch { playerController.state.collect { refreshPictureInPicture() } }

        setContent {
            val preferences by settings.preferences.collectAsStateWithLifecycle(AppPreferences())
            val dark = when (preferences.theme) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; else -> true }
            val youTube = preferences.youTubeColors
            val lightScope = androidx.compose.runtime.rememberCoroutineScope()
            val light = androidx.compose.runtime.remember { app.freshtuber.ui.liquid.LiquidLight(this, lightScope) }
            androidx.compose.runtime.SideEffect { light.setDark(dark) }
            AppTheme(darkTheme = dark, dynamicColor = preferences.enabled(AppFeature.DynamicColor), amoled = preferences.theme == ThemeMode.Amoled,
                serif = preferences.enabled(AppFeature.SerifFont), hue = light.hue, youTube = youTube) {
                androidx.compose.runtime.CompositionLocalProvider(app.freshtuber.ui.theme.LocalAppPreferences provides preferences,
                    app.freshtuber.ui.liquid.LocalLiquidLight provides light,
                    app.freshtuber.ui.components.LocalClassic provides preferences.classicLayout,
                    app.freshtuber.ui.player.LocalVideoBounds provides reportVideoBounds,
                    app.freshtuber.ui.components.LocalFoldPosture provides app.freshtuber.ui.components.rememberFoldPosture(this)) {
                    AppRoot(inPictureInPicture = inPictureInPicture, openVideo = externalVideoId, onVideoOpened = { externalVideoId = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
    }

    private fun acceptIntent(intent: Intent?) {
        externalVideoId = intent?.getStringExtra("videoId")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }

    override fun onStart() {
        super.onStart()
        stopped = false
        controllerFuture = MediaController.Builder(
            this, SessionToken(this, ComponentName(this, app.freshtuber.player.PlaybackService::class.java)),
        ).buildAsync()
    }

    /** Leaving the app while a video plays on screen turns it into a floating window. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters automatically during the Home gesture; older versions need this.
        if (android.os.Build.VERSION.SDK_INT < 31 && canEnterPictureInPicture()) {
            runCatching { enterPictureInPictureMode(pictureInPictureParams()) }
        }
    }

    private fun canEnterPictureInPicture(): Boolean {
        val player = playerController.player
        return packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && pipOnLeave &&
            !audioOnlyRequested && playerController.videoOnScreen && player.playWhenReady &&
            player.playbackState != androidx.media3.common.Player.STATE_IDLE && player.playbackState != androidx.media3.common.Player.STATE_ENDED
    }

    private fun pictureInPictureParams(): PictureInPictureParams {
        val player = playerController.player
        val size = player.videoSize
        val ratio = if (size.width > 0 && size.height > 0) Rational(size.width, size.height) else Rational(16, 9)
        val state = playerController.state.value
        val short = state.nowPlaying?.isShort == true
        fun action(command: Int, icon: Int, title: Int, enabled: Boolean = true) = android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(this, icon), getString(title), getString(title),
            android.app.PendingIntent.getBroadcast(this, command, Intent(PIP_ACTION).setPackage(packageName).putExtra(PIP_COMMAND, command),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT),
        ).apply { isEnabled = enabled }
        val playing = player.isPlaying || (player.playbackState == androidx.media3.common.Player.STATE_BUFFERING && player.playWhenReady)
        val actions = listOf(
            action(PIP_BACKGROUND, R.drawable.ic_pip_headphones, R.string.player_audio_only),
            action(PIP_TOGGLE, if (playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
                if (playing) R.string.player_pause else R.string.player_play),
            action(PIP_NEXT, R.drawable.ic_pip_next, R.string.player_next, enabled = short || state.queue.isNotEmpty()),
        )
        return PictureInPictureParams.Builder().setAspectRatio(ratio.clamped()).setActions(actions).apply {
            videoBounds?.let { setSourceRectHint(it) }
            if (android.os.Build.VERSION.SDK_INT >= 33) setCloseAction(action(PIP_CLOSE, R.drawable.ic_pip_close, R.string.player_close))
            if (android.os.Build.VERSION.SDK_INT >= 31) setAutoEnterEnabled(canEnterPictureInPicture())
        }.build()
    }

    /** Keeps the floating window's buttons (pause/play, next) in step with the player. */
    private fun refreshPictureInPicture() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching { setPictureInPictureParams(pictureInPictureParams()) }
    }

    override fun onDestroy() {
        if (wasInPictureInPicture && !isChangingConfigurations && !audioOnlyRequested) playerController.stop()
        playerController.player.removeListener(pipPlayerListener)
        runCatching { unregisterReceiver(pipReceiver) }
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
        if (isInPictureInPictureMode) wasInPictureInPicture = true
        if (!isInPictureInPictureMode) {
            leftPictureInPicture = true
            // Some Android versions report PiP dismissal after onStop, with no second
            // onStop or immediate onDestroy. Stop here too; expanding starts the activity.
            if (stopped && !audioOnlyRequested && !isChangingConfigurations) playerController.stop()
        }
    }

    override fun onResume() {
        super.onResume()
        leftPictureInPicture = false
        if (!isInPictureInPictureMode) wasInPictureInPicture = false
    }

    /** Closing the floating window stops the video, as YouTube does. */
    override fun onStop() {
        stopped = true
        if (!backgroundPlayback && !audioOnlyRequested && !isInPictureInPictureMode && !isChangingConfigurations) playerController.player.pause()
        super.onStop()
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        if (leftPictureInPicture || (wasInPictureInPicture && isFinishing)) {
            if (!audioOnlyRequested) playerController.stop()
            leftPictureInPicture = false
        }
    }

    private companion object {
        const val PIP_ACTION = "app.freshtuber.PIP_CONTROL"
        const val PIP_COMMAND = "command"
        const val PIP_PREVIOUS = 1
        const val PIP_TOGGLE = 2
        const val PIP_NEXT = 3
        const val PIP_BACKGROUND = 4
        const val PIP_CLOSE = 5
    }

    /** Android only accepts ratios between 1:2.39 and 2.39:1. */
    private fun Rational.clamped(): Rational {
        val value = toFloat()
        return when {
            value > 2.39f -> Rational(239, 100)
            value < 1 / 2.39f -> Rational(100, 239)
            else -> this
        }
    }
}
