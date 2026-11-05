package app.freshtuber

import android.content.Intent
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.data.settings.AppFeature
import app.freshtuber.data.settings.AppSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PictureInPictureIntegrationTest {
    @Test fun repeatedHomeGesturesWorkFromThePageAndTheMiniPlayer() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val settings = AppSettings(context)
        val original = settings.preferences.first()
        settings.markWelcomeDone()
        settings.setFeature(AppFeature.PictureInPicture, true)
        settings.setFeature(AppFeature.LiquidFlat, true)
        settings.setFeature(AppFeature.Autoplay, false)
        var last: MainActivity? = null
        val paramsMethod = MainActivity::class.java.getDeclaredMethod("pictureInPictureParams").apply { isAccessible = true }
        fun autoEnabled(activity: MainActivity) = (paramsMethod.invoke(activity) as android.app.PictureInPictureParams).isAutoEnterEnabled
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("videoId", "jNQXAC9IVRw")).use { scenario ->
                scenario.onActivity { last = it }
                suspend fun until(timeout: Long = 15_000, predicate: (MainActivity) -> Boolean) {
                    withTimeout(timeout) {
                        while (true) {
                            var ready = false
                            instrumentation.runOnMainSync { ready = last?.let(predicate) == true }
                            if (ready) break
                            delay(100)
                        }
                    }
                }
                until(60_000) { it.playerController.player.playbackState == Player.STATE_READY }
                instrumentation.runOnMainSync { last!!.playerController.player.repeatMode = Player.REPEAT_MODE_ONE; last!!.playerController.player.play() }
                until { autoEnabled(it) }
                repeat(3) { cycle ->
                    if (cycle == 1) {
                        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").close()
                        delay(1_000)
                        until { it.playerController.videoOnScreen && autoEnabled(it) }
                    }
                    instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
                    until { it.isInPictureInPictureMode }
                    delay(1_800)
                    instrumentation.runOnMainSync { assertTrue(last!!.playerController.player.playWhenReady) }
                    context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    until { !it.isInPictureInPictureMode && it.hasWindowFocus() }
                    delay(1_800)
                }
                instrumentation.runOnMainSync { last!!.playerController.player.pause() }
                until { !autoEnabled(it) }
                settings.setFeature(AppFeature.PictureInPicture, false)
                instrumentation.runOnMainSync { last!!.playerController.player.play() }
                delay(500)
                until { !autoEnabled(it) }
            }
        } finally {
            instrumentation.runOnMainSync { last?.playerController?.stop() }
            settings.setFeature(AppFeature.PictureInPicture, original.enabled(AppFeature.PictureInPicture))
            settings.setFeature(AppFeature.LiquidFlat, original.enabled(AppFeature.LiquidFlat))
            settings.setFeature(AppFeature.Autoplay, original.enabled(AppFeature.Autoplay))
        }
    }
}
