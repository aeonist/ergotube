package app.freshtuber.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.domain.model.ChannelSummary
import app.freshtuber.domain.model.FeedItem
import app.freshtuber.domain.model.PlaylistSummary
import app.freshtuber.ui.components.MessageState
import app.freshtuber.ui.components.VideoCard
import app.freshtuber.ui.theme.BrandColors
import coil3.compose.AsyncImage

/** Clearance is scrollable content padding, never a permanent empty footer. */
val LocalFeedBottomClearance = androidx.compose.runtime.staticCompositionLocalOf { 88.dp }

/** What a tap on a list item opens. */
interface FeedNavigation {
    fun openVideo(videoId: String)
    fun openPlaylist(playlistId: String, title: String, seedVideoId: String? = null)
    fun openChannel(channelId: String)
}

/**
 * A YouTube list with first-load, empty, error and load-more states.
 * [header] scrolls with the list; [emptyMessage] shows when there is nothing at all.
 */
@Composable
fun FeedList(
    state: FeedUiState,
    navigation: FeedNavigation,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    showChannel: Boolean = true,
    videoActions: Boolean = false,
    onSignInAgain: (() -> Unit)? = null,
    header: (LazyListScope.() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.items.size) {
        if (nearEnd && state.items.isNotEmpty()) onLoadMore()
    }

    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = LocalFeedBottomClearance.current)) {
        header?.invoke(this)
        if (state.error != null && state.items.isNotEmpty()) item(key = "refresh-error") {
            LoadErrorState(state.error, onRetry, onSignInAgain, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        when {
            state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.error != null && state.items.isEmpty() -> item {
                LoadErrorState(state.error, onRetry, onSignInAgain, Modifier.padding(vertical = 48.dp))
            }
            state.items.isEmpty() -> item {
                MessageState(emptyMessage, Modifier.padding(vertical = 48.dp))
            }
            else -> {
                items(state.items, key = { it.key }, contentType = { it::class }) { item ->
                    when (item) {
                        is FeedItem.Video -> VideoCard(
                            video = item.video,
                            branding = state.branding[item.video.id],
                            onClick = { navigation.openVideo(item.video.id) },
                            onChannelClick = navigation::openChannel,
                            showChannel = showChannel,
                            enableMenu = videoActions,
                        )
                        is FeedItem.Playlist -> PlaylistRow(item.playlist) {
                            navigation.openPlaylist(item.playlist.id, item.playlist.title, item.playlist.seedVideoId)
                        }
                        is FeedItem.Channel -> ChannelRow(item.channel) { navigation.openChannel(item.channel.id) }
                    }
                }
                item(key = "footer") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.loadingMore -> CircularProgressIndicator(
                                Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            state.moreFailed -> TextButton(onClick = onLoadMore) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadErrorState(
    error: LoadError,
    onRetry: () -> Unit,
    onSignInAgain: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val expired = error == LoadError.SessionExpired && onSignInAgain != null
    MessageState(
        message = stringResource(error.messageRes()),
        modifier = modifier,
        action = {
            FilledTonalButton(onClick = if (expired) onSignInAgain else onRetry) {
                Text(stringResource(if (expired) R.string.welcome_sign_in else R.string.action_retry))
            }
        },
    )
}

fun LoadError.messageRes(): Int = when (this) {
    LoadError.Network -> R.string.error_network
    LoadError.Captcha -> R.string.error_captcha
    LoadError.SessionExpired -> R.string.error_session_expired
    LoadError.TooManyActions -> R.string.error_too_many_actions
    LoadError.Unknown -> R.string.error_unknown
}

@Composable
private fun PlaylistRow(playlist: PlaylistSummary, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .width(160.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            playlist.videoCountText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandColors.BadgeText,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(BrandColors.BadgeBackground, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            playlist.channelName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
fun ChannelRow(channel: ChannelSummary, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = channel.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            channel.detailText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
