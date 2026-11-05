package app.freshtuber

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.player.NowPlaying
import app.freshtuber.ui.player.FloatingMiniPlayer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MiniPlayerTapIntegrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingChoosesBetweenOpeningAndShowingControls() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var player: ExoPlayer? = null
        instrumentation.runOnMainSync { player = ExoPlayer.Builder(instrumentation.targetContext).build() }
        val now = NowPlaying("jNQXAC9IVRw", "Video", null, null, false, false, emptyList(), -1, emptyList(), -1)
        var opens = 0
        var tapOpens by mutableStateOf(true)
        try {
            compose.setContent {
                Box(Modifier.fillMaxSize()) {
                    FloatingMiniPlayer(player!!, now, onOpen = { opens++ }, onClose = {}, left = false,
                        bottomClearance = 80.dp, corner = 3, onCorner = {}, widthDp = 240f, onWidth = {},
                        hasNext = false, hasPrevious = false, onNext = {}, onPrevious = {}, tapOpens = tapOpens)
                }
            }
            compose.onNodeWithTag("mini").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.25f)) }
            compose.waitUntil(5_000) { opens == 1 }
            compose.runOnIdle { assertEquals(1, opens); tapOpens = false }
            compose.onNodeWithTag("mini").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.25f)) }
            compose.runOnIdle { assertEquals(1, opens) }
            val openLabel = instrumentation.targetContext.getString(R.string.player_open)
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(openLabel).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription(openLabel).assertExists().performClick()
            compose.runOnIdle { assertEquals(2, opens) }
        } finally { instrumentation.runOnMainSync { player?.release() } }
    }
}
