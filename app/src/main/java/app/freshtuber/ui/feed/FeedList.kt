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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * The list fades out over its last 28 dp instead of ending at a hard edge; [atTop] fades the
 * top edge instead (lists that grow upward from the thumb).
 */
fun Modifier.bottomFade(height: androidx.compose.ui.unit.Dp = 28.dp, atTop: Boolean = false): Modifier =
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = height.toPx()
            val clear = androidx.compose.ui.graphics.Color.Transparent
            val black = androidx.compose.ui.graphics.Color.Black
            if (atTop) drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(black, clear), startY = 0f, endY = fade),
                size = androidx.compose.ui.geometry.Size(size.width, fade),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstOut,
            ) else drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(clear, black), startY = size.height - fade, endY = size.height),
                topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fade),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstOut,
            )
        }

/** What a tap on a list item opens. */
interface FeedNavigation {
    fun openVideo(videoId: String)
    fun openPlaylist(playlistId: String, title: String, seedVideoId: String? = null)
    fun openChannel(channelId: String)
    /** Opens [videoIds] as Shorts, swiping between them, starting at [start]. */
    fun openShorts(videoIds: List<String>, thumbnails: List<String>, start: Int) = openVideo(videoIds[start])
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
    /** Whether Not interested may hide these videos (only recommendations). */
    recommendations: Boolean = false,
    onSignInAgain: (() -> Unit)? = null,
    header: (LazyListScope.() -> Unit)? = null,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    /**
     * Grows upward from the thumb like a messenger: the header and the first (newest) item sit
     * right above the bottom bar, older ones are scrolled to upward.
     */
    fromBottom: Boolean = false,
    /** Runs of at least this many Shorts become a sideways shelf; 0 never. */
    shortsShelf: Int = 2,
) {
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.items.size) {
        if (nearEnd && state.items.isNotEmpty()) onLoadMore()
    }

    val columns = app.freshtuber.ui.components.feedColumns()
    val rows = remember(state.items, shortsShelf, columns) { FeedRows.grid(FeedRows.of(state.items, shortsShelf), columns) }
    LazyColumn(modifier.fillMaxSize().bottomFade(atTop = fromBottom), state = listState, reverseLayout = fromBottom,
        contentPadding = PaddingValues(bottom = if (fromBottom) 8.dp else LocalFeedBottomClearance.current, top = if (fromBottom) 24.dp else 0.dp)) {
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
            // Empty and error states sit low, so their button is under the thumb.
            state.error != null && state.items.isEmpty() -> item {
                LoadErrorState(state.error, onRetry, onSignInAgain, Modifier.fillParentMaxHeight(0.62f))
            }
            state.items.isEmpty() -> item {
                MessageState(emptyMessage, Modifier.fillParentMaxHeight(0.62f))
            }
            else -> {
                items(rows, key = { it.key }, contentType = { (it as? FeedRow.Item)?.item?.let { item -> item::class } ?: it::class }) { row ->
                    // New rows fade in, hidden ones fade out while the rest glide into place.
                    val appear = Modifier.animateItem(fadeInSpec = androidx.compose.animation.core.tween(320),
                        placementSpec = androidx.compose.animation.core.spring(stiffness = 500f, visibilityThreshold = androidx.compose.ui.unit.IntOffset(1, 1)),
                        fadeOutSpec = androidx.compose.animation.core.tween(200))
                    if (row is FeedRow.Shelf) { ShortsShelf(row.shorts, navigation, appear); return@items }
                    val card: @Composable (FeedItem.Video, Modifier) -> Unit = { item, cardModifier ->
                        VideoCard(
                            modifier = cardModifier,
                            video = item.video,
                            branding = state.branding[item.video.id],
                            onClick = { navigation.openVideo(item.video.id) },
                            onChannelClick = navigation::openChannel,
                            showChannel = showChannel,
                            enableMenu = videoActions,
                            recommendation = videoActions && recommendations,
                        )
                    }
                    if (row is FeedRow.Grid) {
                        Row(appear.fillMaxWidth().padding(horizontal = 4.dp)) {
                            row.items.forEach { androidx.compose.runtime.key(it.key) { card(it.item as FeedItem.Video, Modifier.weight(1f)) } }
                            repeat(columns - row.items.size) { Spacer(Modifier.weight(1f)) }
                        }
                        return@items
                    }
                    when (val item = (row as FeedRow.Item).item) {
                        is FeedItem.Video -> card(item, appear)
                        is FeedItem.Playlist -> PlaylistRow(item.playlist, appear) {
                            navigation.openPlaylist(item.playlist.id, item.playlist.title, item.playlist.seedVideoId)
                        }
                        is FeedItem.Channel -> ChannelRow(item.channel, appear) { navigation.openChannel(item.channel.id) }
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
private fun PlaylistRow(playlist: PlaylistSummary, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .width(160.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
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
fun ChannelRow(channel: ChannelSummary, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
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
