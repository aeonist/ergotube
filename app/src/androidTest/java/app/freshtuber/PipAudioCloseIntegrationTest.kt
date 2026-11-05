package app.freshtuber

import android.content.Intent
import android.app.PictureInPictureParams
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.data.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PipAudioCloseIntegrationTest {
    @Test fun headphonesKeepAudioAndCrossStopsEverything(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation(); val context = instrumentation.targetContext
        val settings = AppSettings(context); val original = settings.preferences.first()
        settings.markWelcomeDone(); settings.setFeature(AppFeature.LiquidFlat, true)
        settings.setFeature(AppFeature.PictureInPicture, true); settings.setFeature(AppFeature.Autoplay, false)
        var activity: MainActivity? = null
        suspend fun until(timeout: Long = 15_000, predicate: () -> Boolean) {
            withTimeout(timeout) { while (true) {
                var done = false; instrumentation.runOnMainSync { done = predicate() }
                if (done) break; delay(100)
            } }
        }
        val method = MainActivity::class.java.getDeclaredMethod("pictureInPictureParams").apply { isAccessible = true }
        try {
            repeat(2) { round ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("videoId", "dQw4w9WgXcQ")).use { scenario ->
                    scenario.onActivity { activity = it }
                    until(60_000) { activity!!.playerController.player.playbackState == Player.STATE_READY }
                    instrumentation.runOnMainSync { activity!!.playerController.player.play() }
                    instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
                    until { activity!!.isInPictureInPictureMode }
                    delay(1_800)
                    var params: PictureInPictureParams? = null
                    instrumentation.runOnMainSync { params = method.invoke(activity) as PictureInPictureParams }
                    if (round == 0) {
                        File(context.getExternalFilesDir(null), "liquid12-pip.png").outputStream().use {
                            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                        }
                        val title = context.getString(R.string.player_audio_only)
                        params!!.actions.first { it.title.toString() == title }.actionIntent.send()
                        until { activity!!.isDestroyed }
                        var position = 0L
                        instrumentation.runOnMainSync { position = activity!!.playerController.player.currentPosition }
                        until { activity!!.playerController.player.currentPosition > position + 500 }
                        instrumentation.runOnMainSync {
                            assertTrue(activity!!.playerController.player.playWhenReady)
                            assertTrue(activity!!.playerController.player.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO))
                        }
                    } else {
                        params!!.closeAction!!.actionIntent.send()
                        until { activity!!.isDestroyed }
                        instrumentation.runOnMainSync {
                            assertEquals(0, activity!!.playerController.player.mediaItemCount)
                            assertFalse(activity!!.playerController.player.isPlaying)
                            assertNull(activity!!.playerController.state.value.nowPlaying)
                        }
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync { activity?.playerController?.stop() }
            settings.setFeature(AppFeature.LiquidFlat, original.enabled(AppFeature.LiquidFlat))
            settings.setFeature(AppFeature.PictureInPicture, original.enabled(AppFeature.PictureInPicture))
            settings.setFeature(AppFeature.Autoplay, original.enabled(AppFeature.Autoplay))
        }
    }
}
