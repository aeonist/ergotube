package app.freshtuber.ui.player

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import app.freshtuber.ui.components.compactNumber
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.domain.model.LikeStatus
import app.freshtuber.ui.radial.*
import app.freshtuber.ui.screens.player.VideoUiState
import app.freshtuber.ui.screens.player.VideoViewModel
import app.freshtuber.ui.theme.BrandColors

internal val VideoRailWidth = 64.dp
internal val VideoRailInset = 8.dp
internal val VideoRailExclusion = VideoRailWidth + VideoRailInset + 8.dp

internal data class VideoActionCallbacks(
    val subscription: () -> Unit,
    val comments: () -> Unit,
    val dislike: () -> Unit,
    val like: () -> Unit,
)

private data class RailAction(val icon: ImageVector, val label: String, val selected: Boolean = false,
    val enabled: Boolean = true, val count: String? = null, val click: () -> Unit)

@Composable
fun VideoActionRail(
    state: VideoUiState,
    viewModel: VideoViewModel,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    onFullscreen: (() -> Unit)? = null,
    overVideo: Boolean = false,
    leftHanded: Boolean = false,
) = VideoActionRailContent(state, VideoActionCallbacks(viewModel::toggleSubscription, viewModel::openComments,
    viewModel::toggleDislike, viewModel::toggleLike), onMore, modifier, onFullscreen, overVideo, leftHanded)

/** Top to bottom reverses the agreed thumb-up order; short windows use complete rows. */
@Composable
internal fun VideoActionRailContent(
    state: VideoUiState,
    callbacks: VideoActionCallbacks,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    onFullscreen: (() -> Unit)? = null,
    overVideo: Boolean = false,
    leftHanded: Boolean = false,
) {
    val locale = LocalConfiguration.current.locales[0]
    val liked = state.likeStatus == LikeStatus.Like
    val details = state.details
    val likeCount = (if (liked) details?.likeCountTextLiked ?: details?.likeCountText else details?.likeCountText)
        ?.takeIf { it.any(Char::isDigit) }
    val actions = buildList {
        add(RailAction(if (state.subscribed == true) Icons.Rounded.PersonRemove else Icons.Rounded.PersonAdd,
            stringResource(if (state.subscribed == true) R.string.channel_unsubscribe else R.string.channel_subscribe),
            state.subscribed == true, details?.channel != null, click = callbacks.subscription))
        add(RailAction(Icons.Rounded.ChatBubbleOutline, stringResource(R.string.video_comments), enabled = details != null, click = callbacks.comments))
        add(RailAction(if (state.likeStatus == LikeStatus.Dislike) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt,
            stringResource(R.string.video_dislike), state.likeStatus == LikeStatus.Dislike, details != null,
            state.dislikes?.let { compactNumber(it, locale) }, callbacks.dislike))
        add(RailAction(if (liked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt,
            stringResource(R.string.video_like), liked, details != null, likeCount, callbacks.like))
        onFullscreen?.let { add(RailAction(Icons.Rounded.Fullscreen, stringResource(R.string.player_fullscreen), click = it)) }
        add(RailAction(Icons.Rounded.MoreHoriz, stringResource(R.string.video_more), click = onMore))
    }
    val measurer = rememberTextMeasurer()
    val countStyle = MaterialTheme.typography.labelSmall
    val countWidth = with(LocalDensity.current) { (VideoRailWidth - 4.dp).roundToPx() }
    val fittedActions = actions.map { action ->
        val count = action.count
        val display = if (count != null && measurer.measure(AnnotatedString(count), countStyle, softWrap = false, maxLines = 1).size.width > countWidth) {
            count.replace(0xA0.toChar(), ' ').replace(0x202F.toChar(), ' ').split(' ')
                .filter(String::isNotBlank).joinToString("\n")
        } else count
        action.copy(count = display)
    }
    @Composable fun button(action: RailAction) {
        ActionIcon(action.icon, action.label, action.selected, overVideo, action.enabled, action.count, compact = overVideo, onClick = action.click)
    }
    BoxWithConstraints(modifier) {
        if (maxHeight < (fittedActions.sumOf { if (it.count == null || (overVideo && '\n' !in it.count)) 48 else if ('\n' in it.count) (if (overVideo) 64 else 72) else 64 } + (actions.size - 1) * 8).dp) {
            Column(horizontalAlignment = if (leftHanded) Alignment.Start else Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fittedActions.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (if (leftHanded) row.reversed() else row).forEach { button(it) }
                    }
                }
            }
        } else Column(Modifier.width(VideoRailWidth), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fittedActions.forEach { button(it) }
        }
    }
}

@Composable
fun ActionIcon(icon: ImageVector, label: String, selected: Boolean = false, overVideo: Boolean = false,
    enabled: Boolean = true, count: String? = null, compact: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.width(if (count == null) 56.dp else VideoRailWidth).height(if (count == null) 48.dp else if ('\n' in count) (if (compact) 64.dp else 72.dp) else if (compact) 48.dp else 64.dp).semantics {
            this.selected = selected
            contentDescription = listOfNotNull(label, count?.replace('\n', ' ')).joinToString(", ")
        }.clip(CircleShape).background(if (overVideo) Color(0xCC181818) else MaterialTheme.colorScheme.surfaceContainerHigh),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (overVideo) Color.White else MaterialTheme.colorScheme.onSurface,
            disabledContentColor = if (overVideo) Color(0x99FFFFFF) else MaterialTheme.colorScheme.onSurfaceVariant,
        )) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(if (count == null) 24.dp else if ('\n' in count) 18.dp else 22.dp))
            count?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = if ('\n' in it) 2 else 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp).clearAndSetSemantics {}) }
        }
    }
}

@Composable
fun NavigationAnchor(onClick: () -> Unit) {
    FilledIconButton(onClick, modifier = Modifier.size(56.dp).radialLauncher(onClick),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = BrandColors.Red, contentColor = Color.White)) {
        Icon(Icons.Rounded.Menu, stringResource(R.string.radial_open))
    }
}

@Composable
fun VideoMoreMenu(videoId: String, viewModel: VideoViewModel, left: Boolean, arc: ThumbArc, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val share = stringResource(R.string.video_share)
    val downloadNotifications = app.freshtuber.ui.components.rememberDownloadNotifications()
    val items = listOf(
        RadialItem("save", stringResource(R.string.video_save), Icons.Rounded.BookmarkBorder, shortTitle = stringResource(R.string.radial_save_short)) { viewModel.openPlaylistPicker() },
        RadialItem("share", share, Icons.Rounded.Share) {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/$videoId"), share))
        },
        RadialItem("download", stringResource(R.string.feed_download), Icons.Rounded.Download) { downloadNotifications(); viewModel.download() },
    )
    RadialMenu(items, left, arc, initiallyExpanded = true, showAnchor = false,
        onExpandedChange = { if (!it) onDismiss() },
        heading = stringResource(R.string.video_more), instruction = stringResource(R.string.radial_choose_action))
}

/** Compact entry points; reactions and channel actions never share a long vertical rail. */
enum class VideoActionGroup { Video, Channel, All }

@Composable
fun VideoActionGroups(state: VideoUiState, onOpen: (VideoActionGroup) -> Unit, modifier: Modifier = Modifier, onPosition: (VideoActionGroup, Offset) -> Unit = { _, _ -> }) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
        FilledTonalButton(onClick = { onOpen(VideoActionGroup.Video) }, modifier = Modifier.heightIn(min = 48.dp).onGloballyPositioned { onPosition(VideoActionGroup.Video, it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)) }.radialLauncher { onOpen(VideoActionGroup.Video) }) {
            Icon(Icons.Rounded.TouchApp, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.video_group))
        }
        FilledTonalButton(onClick = { onOpen(VideoActionGroup.Channel) }, enabled = state.details?.channel != null,
            modifier = Modifier.heightIn(min = 48.dp).onGloballyPositioned { onPosition(VideoActionGroup.Channel, it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)) }.radialLauncher { if (state.details?.channel != null) onOpen(VideoActionGroup.Channel) }) {
            val channel = state.details?.channel
            if (channel?.avatarUrl != null) coil3.compose.AsyncImage(channel.avatarUrl, null,
                Modifier.size(24.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else Icon(Icons.Rounded.AccountCircle, null, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.channel_group))
        }
    }
}

@Composable
fun VideoActionsMenu(
    state: VideoUiState,
    viewModel: VideoViewModel,
    group: VideoActionGroup,
    openChannel: (String) -> Unit,
    left: Boolean,
    arc: ThumbArc,
    anchorRoot: Offset? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val shareLabel = stringResource(R.string.video_share)
    val liked = state.likeStatus == LikeStatus.Like
    val likeCount = (if (liked) state.details?.likeCountTextLiked ?: state.details?.likeCountText else state.details?.likeCountText)
        ?.takeIf { it.any(Char::isDigit) }
    fun withCount(label: String, count: String?) = listOfNotNull(label, count).joinToString("\n")
    val downloadNotifications = app.freshtuber.ui.components.rememberDownloadNotifications()
    val videoItems = listOf(
        RadialItem("like", withCount(stringResource(R.string.video_like), likeCount),
            if (liked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt, selected = liked, shortTitle = withCount(stringResource(R.string.radial_like_short), likeCount)) { viewModel.toggleLike() },
        RadialItem("dislike", withCount(stringResource(R.string.video_dislike), state.dislikes?.let { compactNumber(it, locale) }),
            if (state.likeStatus == LikeStatus.Dislike) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt,
            selected = state.likeStatus == LikeStatus.Dislike,
            shortTitle = withCount(stringResource(R.string.video_dislike_short), state.dislikes?.let { compactNumber(it, locale) })) { viewModel.toggleDislike() },
        RadialItem("comments", stringResource(R.string.video_comments), Icons.Rounded.ChatBubbleOutline, shortTitle = stringResource(R.string.radial_comments_short)) { viewModel.openComments() },
        RadialItem("save", stringResource(R.string.video_save), Icons.Rounded.BookmarkBorder, shortTitle = stringResource(R.string.radial_save_short)) { viewModel.openPlaylistPicker() },
        RadialItem("share", shareLabel, Icons.Rounded.Share, shortTitle = stringResource(R.string.radial_share_short)) {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${viewModel.videoId}"), shareLabel))
        },
        RadialItem("download", stringResource(R.string.feed_download), Icons.Rounded.Download) { downloadNotifications(); viewModel.download() },
    )
    val enableNotifications = app.freshtuber.ui.components.rememberNotificationPermission { viewModel.setLocalNotifications(true) }
    val channelItems = state.details?.channel?.let { channel -> listOf(
        RadialItem("channel", stringResource(R.string.channel_open), Icons.Rounded.AccountCircle) { openChannel(channel.id) },
        RadialItem("subscription", stringResource(if (state.subscribed == true) R.string.channel_unsubscribe else R.string.channel_subscribe),
            if (state.subscribed == true) Icons.Rounded.PersonRemove else Icons.Rounded.PersonAdd,
            selected = state.subscribed == true) { viewModel.toggleSubscription() },
        RadialItem("notifications", stringResource(R.string.notification_new_videos), Icons.Rounded.Notifications,
            selected = state.localNotifications) { if (state.localNotifications) viewModel.setLocalNotifications(false) else enableNotifications() },
    ) }.orEmpty()
    val videoTitle = stringResource(R.string.video_group)
    val channelTitle = stringResource(R.string.channel_group)
    val items = when (group) {
        VideoActionGroup.Video -> videoItems
        VideoActionGroup.Channel -> channelItems
        VideoActionGroup.All -> listOf(
            RadialItem("video-actions", videoTitle, Icons.Rounded.TouchApp, children = videoItems) {},
            RadialItem("channel-actions", channelTitle, Icons.Rounded.AccountCircle, children = channelItems) {},
        )
    }
    RadialMenu(items, left, arc, initiallyExpanded = true, showAnchor = true, closeRoot = anchorRoot,
        onExpandedChange = { if (!it) onDismiss() },
        heading = when (group) { VideoActionGroup.Video -> videoTitle; VideoActionGroup.Channel -> listOfNotNull(channelTitle, state.details?.channel?.name).joinToString(" · "); VideoActionGroup.All -> stringResource(R.string.player_actions) },
        instruction = stringResource(R.string.radial_choose_action))
}
