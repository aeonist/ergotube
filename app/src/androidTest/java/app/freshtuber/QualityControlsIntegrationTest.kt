package app.freshtuber

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.data.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import java.io.File

class QualityControlsIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun directQualityWorksInPortraitAndFullscreen(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val settings = AppSettings(context)
        val original = settings.preferences.first()
        settings.markWelcomeDone()
        settings.setFeature(AppFeature.LiquidFlat, true)
        settings.setFeature(AppFeature.Autoplay, false)
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("videoId", "dQw4w9WgXcQ")).use { scenario ->
                val quality = context.getString(R.string.player_quality)
                val auto = context.getString(R.string.player_quality_auto)
                val fullscreen = context.getString(R.string.player_fullscreen)
                fun capture(name: String) {
                    File(context.getExternalFilesDir(null), "$name.png").outputStream().use { out ->
                        instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
                compose.waitUntil(45_000) { compose.onAllNodesWithContentDescription(quality).fetchSemanticsNodes().isNotEmpty() }
                Thread.sleep(4_000)
                compose.onNodeWithContentDescription(quality).performTouchInput { click(center) }
                compose.waitUntil(10_000) { compose.onAllNodesWithText(auto).fetchSemanticsNodes().isNotEmpty() }
                capture("round-quality-portrait")
                instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").close()
                compose.waitForIdle()
                compose.onNodeWithContentDescription(fullscreen).performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(quality).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription(quality).performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText(auto).fetchSemanticsNodes().isNotEmpty() }
                capture("round-quality-fullscreen")
                scenario.onActivity { it.playerController.stop() }
            }
        } finally {
            settings.setFeature(AppFeature.LiquidFlat, original.enabled(AppFeature.LiquidFlat))
            settings.setFeature(AppFeature.Autoplay, original.enabled(AppFeature.Autoplay))
        }
    }
}
