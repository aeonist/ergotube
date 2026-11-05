package app.freshtuber

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.data.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class RoundLayoutIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun navigationWidthOrderAndClassicTransparency() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val settings = AppSettings(context)
        val original = settings.preferences.first()
        try {
            settings.markWelcomeDone()
            settings.setFeature(AppFeature.LiquidFlat, true)
            settings.setStyle(UiStyle.Ergonomic)
            settings.setDockWidth(100)
            ActivityScenario.launch(MainActivity::class.java).use {
                fun ready() { compose.waitForIdle(); Thread.sleep(800); compose.waitForIdle() }
                fun capture(name: String) {
                    ready()
                    File(context.getExternalFilesDir(null), "$name.png").outputStream().use { out ->
                        instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
                val tabs = listOf("Mine", "Subscriptions", "Search", "Shorts", "Home")
                fun centers() = tabs.map { compose.onNodeWithTag("dock:$it").fetchSemanticsNode().boundsInRoot.center.x }
                ready()
                val full = centers()
                assertEquals(full.sorted(), full)
                capture("round-full")
                settings.setDockWidth(0)
                ready()
                val compact = centers()
                if (context.resources.configuration.smallestScreenWidthDp < 600) assertTrue(compact.last() - compact.first() < full.last() - full.first())
                else assertEquals(full, compact)
                capture("round-compact")
                settings.setStyle(UiStyle.Classic)
                settings.setDockWidth(100)
                ready()
                assertEquals(centers().sorted(), centers())
                capture("round-classic")
            }
        } finally {
            settings.setFeature(AppFeature.LiquidFlat, original.enabled(AppFeature.LiquidFlat))
            settings.setStyle(original.style)
            settings.setDockWidth(original.dockWidth)
        }
    }
}
