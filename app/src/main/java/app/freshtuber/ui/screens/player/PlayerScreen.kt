package app.freshtuber.ui.screens.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.freshtuber.R
import app.freshtuber.domain.model.VideoDetails
import app.freshtuber.player.PlayerEvent
import app.freshtuber.ui.components.VideoCard
import app.freshtuber.ui.feed.FeedNavigation
import app.freshtuber.ui.feed.LoadError
import app.freshtuber.ui.feed.LoadErrorState
import app.freshtuber.ui.player.*
import app.freshtuber.ui.components.*
import app.freshtuber.domain.model.LikeStatus
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected

@Composable
fun PlayerScreen(
    navigation: FeedNavigation,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onAdvanced: (String) -> Unit,
    viewModel: VideoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val gestures by viewModel.gesturesEnabled.collectAsStateWithLifecycle()
    val left = LocalLeftHanded.current
    val messages = LocalAppMessages.current
    val resources = LocalResources.current
    val menuSettings: app.freshtuber.ui.screens.settings.MenuSettingsViewModel = hiltViewModel()
    val thumbArc by menuSettings.arc.collectAsStateWithLifecycle()
    var actionGroup by remember { mutableStateOf<VideoActionGroup?>(null) }
    var settingsPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var fullscreenRequested by rememberSaveable { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullscreen = fullscreenRequested || landscape
    val padUi = remember { PadUi() }
    val pages = remember { StoryboardPages() }
    var lastSubtitle by remember(playerState.nowPlaying?.videoId) { mutableIntStateOf(-1) }

    // Coming back to this page (e.g. from a suggested video) brings its video back.
    LaunchedEffect(viewModel.videoId) { viewModel.startPlayback() }
    DisposableEffect(Unit) {
        viewModel.setVideoOnScreen(true)
        onDispose { viewModel.setVideoOnScreen(false) }
    }
    FullscreenEffect(fullscreen = fullscreen, requested = fullscreenRequested)
    BackHandler(enabled = fullscreenRequested) { fullscreenRequested = false }

    LaunchedEffect(Unit) {
        viewModel.playerEvents.collect { event ->
            when (event) {
                is PlayerEvent.SegmentSkipped -> messages?.show(resources.getString(R.string.player_sponsor_skipped))
                is PlayerEvent.Advanced -> onAdvanced(event.videoId)
            }
        }
    }
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        messages?.show(resources.getString(current.textRes()),
            if (current == VideoMessage.NeedsSignIn) resources.getString(R.string.welcome_sign_in) else null) { if (it) onSignIn() }
        viewModel.consumeMessage()
    }

    var videoAspect by remember(viewModel.player) { mutableFloatStateOf(16f / 9f) }
    DisposableEffect(viewModel.player) {
        fun update(size: androidx.media3.common.VideoSize) {
            if (size.width > 0 && size.height > 0) videoAspect = size.width * size.pixelWidthHeightRatio / size.height
        }
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) = update(videoSize)
        }
        update(viewModel.player.videoSize)
        viewModel.player.addListener(listener)
        onDispose { viewModel.player.removeListener(listener) }
    }

    if (LocalInPictureInPicture.current) {
        VideoSurface(viewModel.player, Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val controller = viewModel.controller
    val now = playerState.nowPlaying
    val captionsOn = (now?.subtitleIndex ?: -1) >= 0
    val captionsOnText = stringResource(R.string.player_captions_on)
    val captionsOffText = stringResource(R.string.player_captions_off)
    val speedText = stringResource(R.string.player_speed)
    val onSpeed = {
        val next = PlaybackSpeeds.getOrNull(PlaybackSpeeds.indexOf(playerState.speed) + 1) ?: 1f
        controller.setSpeed(next)
        padUi.say("$speedText ${speedLabel(next)}")
    }
    val onCaptions = {
        if (captionsOn) lastSubtitle = now?.subtitleIndex ?: -1
        padUi.say(if (toggleCaptions(controller, playerState, lastSubtitle)) captionsOnText else captionsOffText)
    }
    val onBoost: (Boolean) -> Unit = { on -> viewModel.player.setPlaybackSpeed(if (on) 2f else playerState.speed) }
    val onNext: () -> Unit = { if (playerState.queue.isNotEmpty()) viewModel.playNext() }

    if (fullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoSurface(viewModel.player, Modifier.fillMaxSize())
            PlayerOverlay(
                player = viewModel.player,
                state = playerState,
                fullscreen = true,
                gesturesEnabled = gestures,
                onBack = { if (fullscreenRequested) fullscreenRequested = false else onBack() },
                onToggleFullscreen = { fullscreenRequested = !fullscreen },
                onOpenSettings = { position -> settingsPosition = position; settingsOpen = true },
                onNext = if (playerState.queue.isNotEmpty()) viewModel::playNext else null,
                onRetry = viewModel::retryPlayback,
                onOpenActions = { actionGroup = VideoActionGroup.All },
                actionsExpanded = actionGroup != null,
                leftHanded = left,
                thumbArc = thumbArc,
            )
        }
    } else {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()
            .background(MaterialTheme.colorScheme.surface).clipToBounds()) {
            val details = state.details
            val videoHeight = minOf(maxWidth / videoAspect, maxHeight * 0.4f)
            val density = LocalDensity.current
            var zoneHeight by remember { mutableStateOf(0.dp) }
            Column(Modifier.fillMaxSize()) {
                // Watch and read at the top; nothing here needs reaching.
                Box(Modifier.fillMaxWidth().height(videoHeight).background(Color.Black)) {
                    VideoSurface(viewModel.player, Modifier.fillMaxSize())
                    PortraitVideoLayer(viewModel.player, playerState, onMinimize = onBack, onRetry = viewModel::retryPlayback)
                }
                Box(Modifier.weight(1f)) {
                    when {
                        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        details == null -> LoadErrorState(state.error ?: LoadError.Unknown, viewModel::load, onSignIn)
                        else -> VideoBody(details, state, navigation, offline = now?.isOffline == true)
                    }
                }
                // Everything pressed sits here, under the thumb.
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .onSizeChanged { zoneHeight = with(density) { it.height.toDp() } }
                    .navigationBarsPadding().padding(top = 4.dp, bottom = 8.dp)) {
                    PlayerActions(state, viewModel, left, onMore = { moreOpen = true })
                    PlayerPad(
                        player = viewModel.player,
                        ui = padUi,
                        speed = playerState.speed,
                        captionsOn = captionsOn,
                        captionsAvailable = !now?.subtitles.isNullOrEmpty(),
                        chapters = now?.chapters.orEmpty(),
                        live = now?.isLive == true,
                        left = left,
                        hasNext = playerState.queue.isNotEmpty(),
                        onNext = onNext,
                        onMinimize = onBack,
                        onFullscreen = { fullscreenRequested = true },
                        onSpeed = onSpeed,
                        onCaptions = onCaptions,
                        onBoost = onBoost,
                    )
                }
            }
            val scrub = padUi.scrubMs
            if (scrub != null) {
                val duration = viewModel.player.duration.coerceAtLeast(1)
                val fraction = (scrub.toFloat() / duration).coerceIn(0f, 1f)
                val x = (18.dp + (maxWidth - 36.dp) * fraction - 76.dp).coerceIn(8.dp, maxWidth - 160.dp)
                ScrubPreview(now?.storyboard, scrub, now?.chapters.orEmpty(), padUi.fine, fanHint = false, pages = pages,
                    modifier = Modifier.align(Alignment.BottomStart).padding(bottom = zoneHeight + 8.dp).offset(x = x))
            }
        }
    }

    actionGroup?.let { group ->
        VideoActionsMenu(state, viewModel, group, navigation::openChannel, left, thumbArc, anchorRoot = null) { actionGroup = null }
    }
    if (moreOpen) PlayerMoreSheet(playerState, state, controller, viewModel, navigation::openChannel) { moreOpen = false }
    if (comments.opened) CommentsSheet(comments, viewModel, navigation::openChannel)
    if (picker.opened) PlaylistPickerSheet(picker, viewModel)
    if (settingsOpen) PlayerSettingsSheet(playerState, viewModel.controller, left, thumbArc, centerOnScreen = settingsPosition, onDismiss = { settingsOpen = false })
}

/** Like, dislike, comments, subscribe, more; the first one sits under the thumb. */
@Composable
private fun PlayerActions(state: VideoUiState, viewModel: VideoViewModel, left: Boolean, onMore: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val details = state.details
    val liked = state.likeStatus == LikeStatus.Like
    val disliked = state.likeStatus == LikeStatus.Dislike
    val likeCount = (if (liked) details?.likeCountTextLiked ?: details?.likeCountText else details?.likeCountText)?.takeIf { it.any(Char::isDigit) }
    val subscribed = state.subscribed == true
    data class Action(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val description: String,
        val selected: Boolean, val enabled: Boolean, val onClick: () -> Unit)
    val likeLabel = stringResource(R.string.video_like)
    val dislikeLabel = stringResource(R.string.video_dislike)
    val actions = listOf(
        Action(if (liked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt, likeCount ?: likeLabel, likeLabel, liked, details != null, viewModel::toggleLike),
        Action(if (disliked) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt,
            state.dislikes?.let { compactNumber(it, locale) } ?: dislikeLabel, dislikeLabel, disliked, details != null, viewModel::toggleDislike),
        Action(Icons.Rounded.ChatBubbleOutline, stringResource(R.string.player_comments_short), stringResource(R.string.video_comments), false, details != null, viewModel::openComments),
        Action(if (subscribed) Icons.Rounded.HowToReg else Icons.Rounded.PersonAdd,
            stringResource(if (subscribed) R.string.player_subscribed_short else R.string.player_subscribe_short),
            stringResource(if (subscribed) R.string.channel_unsubscribe else R.string.channel_subscribe), subscribed, details?.channel != null, viewModel::toggleSubscription),
        Action(Icons.Rounded.MoreHoriz, stringResource(R.string.player_more), stringResource(R.string.video_more), false, true, onMore),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
        handOrder(actions, left).forEach { action ->
            Surface(onClick = action.onClick, enabled = action.enabled, modifier = Modifier.weight(1f).height(54.dp)
                .semantics { contentDescription = action.description; selected = action.selected },
                shape = RoundedCornerShape(14.dp), color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (action.enabled) 1f else 0.38f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(action.icon, null)
                    Text(action.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp))
                }
            }
        }
    }
}

/** Landscape and hidden system bars while full screen; everything restored on exit. */
@Composable
private fun FullscreenEffect(fullscreen: Boolean, requested: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(fullscreen, requested) {
        val window = activity.window
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        val oldCutout = if (android.os.Build.VERSION.SDK_INT >= 28) window.attributes.layoutInDisplayCutoutMode else 0
        val oldStatusIcons = insets.isAppearanceLightStatusBars
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
        insets.isAppearanceLightStatusBars = false
        if (fullscreen) {
            if (android.os.Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (android.os.Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
        // Rotate only when the button asked for it; a rotated phone already is landscape.
        if (requested) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (android.os.Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (android.os.Build.VERSION.SDK_INT >= 30 && oldCutout == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS)
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else when (oldCutout) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                    else -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
            insets.isAppearanceLightStatusBars = oldStatusIcons
            if (requested) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** Title, channel, description and up next; scrolls on its own between the video and the controls. */
@Composable
private fun VideoBody(details: VideoDetails, state: VideoUiState, navigation: FeedNavigation, offline: Boolean = false) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "info") { TitleBlock(details, offline) }
        details.channel?.let { channel ->
            item(key = "channel") {
                Row(Modifier.fillMaxWidth().clickable { navigation.openChannel(channel.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    coil3.compose.AsyncImage(channel.avatarUrl, null, Modifier.size(36.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(10.dp))
                    Text(channel.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (state.subscribed == true) Text(stringResource(R.string.channel_subscribed), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (details.related.isNotEmpty()) {
            item(key = "related-title") {
                Text(stringResource(R.string.player_up_next), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp))
            }
            items(details.related, key = { "r:${it.id}" }) { video ->
                VideoCard(video, onClick = { navigation.openVideo(video.id) }, onChannelClick = navigation::openChannel, enableMenu = true)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TitleBlock(details: VideoDetails, offline: Boolean = false) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(details.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(if (offline) stringResource(R.string.download_complete) else null, details.viewsText, details.dateText).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        details.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
    if (expanded) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { expanded = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(details.title, style = MaterialTheme.typography.titleLarge)
            details.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

internal fun VideoMessage.textRes(): Int = when (this) {
    VideoMessage.NeedsSignIn -> R.string.action_needs_sign_in
    VideoMessage.ActionFailed -> R.string.action_failed
    VideoMessage.TooManyActions -> R.string.error_too_many_actions
    VideoMessage.CommentPosted -> R.string.comment_posted
    VideoMessage.DownloadQueued -> R.string.download_queued_message
    VideoMessage.DownloadExists -> R.string.download_exists
    VideoMessage.WatchLaterAdded -> R.string.watch_later_added
}
