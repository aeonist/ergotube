package app.freshtuber

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.freshtuber.player.PlayerState
import app.freshtuber.ui.player.*
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

/** Full screen: while a video plays, the controls and the elapsed time leave by themselves and come back on a tap. */
class FullscreenAutoHideIntegrationTest {
    @get:Rule val compose = createComposeRule()

    private fun playingPlayer(): Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, args -> when (method.name) {
        "isPlaying", "getPlayWhenReady" -> true
        "getPlaybackState" -> Player.STATE_READY
        "getCurrentPosition" -> 30_000L
        "getDuration", "getBufferedPosition" -> 600_000L
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.get(0)
        "toString" -> "Auto-hide test player"
        else -> null
    } } as Player

    @Test fun fanAndTimeHideAndReturnOnTap() {
        val player = playingPlayer()
        compose.setContent {
            Box(Modifier.size(800.dp, 360.dp).testTag("full")) {
                FullscreenPlayer(player, PlayerState(), PadUi(), StoryboardPages(), left = false, gesturesEnabled = true,
                    captionsOn = false, captionsAvailable = false, hasNext = true, showRotateHint = false,
                    onExit = {}, onNext = {}, onQuality = {}, onSpeed = {}, onCaptions = {}, onBoost = {})
            }
        }
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("video:time").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("video:time").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("full").performTouchInput { click(center.copy(x = width * 0.2f)) }
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("video:time").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("video:time").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun classicFullscreenControlsHide() {
        val player = playingPlayer()
        compose.setContent {
            Box(Modifier.size(800.dp, 360.dp)) {
                ClassicVideoControls(player, PlayerState(), fullscreen = true, segments = emptyList(), hasPrevious = false, hasNext = true,
                    captionsOn = false, captionsAvailable = false, onPrevious = {}, onNext = {}, onFullscreen = {}, onMinimize = {},
                    onCaptions = {}, onMore = {}, onQuality = {}, title = "Title")
            }
        }
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("classic:fullscreen").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("classic:fullscreen").fetchSemanticsNodes().isEmpty() }
    }
}
