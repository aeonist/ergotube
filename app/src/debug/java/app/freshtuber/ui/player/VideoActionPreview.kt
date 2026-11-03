package app.freshtuber.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import app.freshtuber.ui.components.compactNumber
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.domain.model.*
import app.freshtuber.ui.screens.player.VideoUiState
import app.freshtuber.ui.theme.AppTheme

/** Debug-only synthetic states: no repository, player, account or callbacks are connected. */
@Preview
@Composable
fun VideoActionsStatesPreview() {
    val count = compactNumber(287_000, LocalConfiguration.current.locales[0])
    val details = VideoDetails(
        id = "preview", title = "Preview", viewsText = null, dateText = null, description = null,
        channel = ChannelSummary("preview-channel", "Preview channel"), likeCountText = count, likeCountTextLiked = count,
        likeStatus = LikeStatus.Like, subscribed = true, likeCommand = null, dislikeCommand = null,
        removeLikeCommand = null, subscribeCommand = null, unsubscribeCommand = null, related = emptyList(), commentsContinuation = null,
    )
    val state = VideoUiState(details = details, loading = false, likeStatus = LikeStatus.Like, subscribed = true, dislikes = 2_300)
    val callbacks = VideoActionCallbacks({}, {}, {}, {})
    AppTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.preview_states_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.preview_states_note), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.width(72.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.preview_portrait), style = MaterialTheme.typography.labelSmall)
                        VideoActionRailContent(state, callbacks, {}, onFullscreen = {})
                    }
                    Column(Modifier.width(72.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.nav_shorts), style = MaterialTheme.typography.labelSmall)
                        VideoActionRailContent(state.copy(likeStatus = LikeStatus.Dislike), callbacks, {}, overVideo = true)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.player_fullscreen), style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.height(180.dp).fillMaxWidth().background(Color.Black).padding(4.dp)) {
                            VideoActionRailContent(state, callbacks, {}, overVideo = true)
                        }
                    }
                }
            }
        }
    }
}
