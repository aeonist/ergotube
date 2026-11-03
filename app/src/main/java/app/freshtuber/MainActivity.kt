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
    private var leftPictureInPicture = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        acceptIntent(intent)
        // Keeps the content region in sync with the per-app language (Android 13+), which the
        // activity sees even when the Application context does not.
        val uiLocale = ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()
        NewPipeInitializer.updateLocale(uiLocale)
        lifecycleScope.launch { settings.pipOnLeave.collect { pipOnLeave = it } }

        setContent {
            AppTheme {
                AppRoot(inPictureInPicture = inPictureInPicture, openVideo = externalVideoId, onVideoOpened = { externalVideoId = null })
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
        controllerFuture = MediaController.Builder(
            this, SessionToken(this, ComponentName(this, app.freshtuber.player.PlaybackService::class.java)),
        ).buildAsync()
    }

    /** Leaving the app while a video plays on screen turns it into a floating window. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val player = playerController.player
        val canPip = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        if (canPip && pipOnLeave && playerController.videoOnScreen && player.isPlaying) {
            val size = player.videoSize
            val ratio = if (size.width > 0 && size.height > 0) {
                Rational(size.width, size.height)
            } else {
                Rational(16, 9)
            }
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio.clamped()).build())
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
        if (!isInPictureInPictureMode) leftPictureInPicture = true
    }

    override fun onResume() {
        super.onResume()
        leftPictureInPicture = false
    }

    /** Closing the floating window stops the video, as YouTube does. */
    override fun onStop() {
        super.onStop()
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        if (leftPictureInPicture) {
            playerController.player.pause()
            leftPictureInPicture = false
        }
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
