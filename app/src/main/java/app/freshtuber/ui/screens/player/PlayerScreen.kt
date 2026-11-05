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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.outlined.PushPin

@Composable
fun PlayerScreen(
    navigation: FeedNavigation,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onAdvanced: (String) -> Unit,
    /** The page has fully arrived (the video surface can appear). */
    settled: Boolean = true,
    /** The page is on its way out. */
    leaving: Boolean = false,
    viewModel: VideoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val gestures by viewModel.gesturesEnabled.collectAsStateWithLifecycle()
    val pinned by viewModel.videoPinned.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // The pad slides away while the list is read downward and comes back on the way up.
    val padHide = rememberScrollHide()
    val left = LocalLeftHanded.current
    val messages = LocalAppMessages.current
    val resources = LocalResources.current
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var lock by rememberSaveable { mutableStateOf(OrientationLock.None) }
    var rotateHint by remember { mutableStateOf(false) }
    var videoAspect by remember(viewModel.player, viewModel.videoId) { mutableFloatStateOf(16f / 9f) }
    DisposableEffect(viewModel.player, viewModel.videoId) {
        fun update(size: androidx.media3.common.VideoSize) {
            if (size.width > 0 && size.height > 0) videoAspect = size.width * size.pixelWidthHeightRatio / size.height
        }
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) = update(videoSize)
        }
        // The shared player may still show the previous video; only its own size counts.
        if (viewModel.player.currentMediaItem?.mediaId == viewModel.videoId) update(viewModel.player.videoSize)
        viewModel.player.addListener(listener)
        onDispose { viewModel.player.removeListener(listener) }
    }
    // A vertical video goes full screen upright, like YouTube, instead of turning the phone.
    val vertical = videoAspect < 0.9f
    var uprightFull by rememberSaveable { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Turning the phone opens fullscreen like YouTube; the button and the pad swipe ask for it.
    val fullscreen = uprightFull || lock == OrientationLock.Landscape || (landscape && lock != OrientationLock.Portrait)
    val enterFullscreen = {
        if (vertical && !landscape) uprightFull = true
        else { lock = OrientationLock.Landscape; rotateHint = !landscape }
    }
    val exitFullscreen = { uprightFull = false; lock = if (landscape) OrientationLock.Portrait else OrientationLock.None; rotateHint = false }
    // The next video is wide: it turns the phone like any other.
    LaunchedEffect(vertical) { if (uprightFull && !vertical) { uprightFull = false; lock = OrientationLock.Landscape } }
    val padUi = remember { PadUi() }
    val pages = remember { StoryboardPages() }
    var lastSubtitle by remember(playerState.nowPlaying?.videoId) { mutableIntStateOf(-1) }

    // Coming back to this page (e.g. from a suggested video) brings its video back.
    LaunchedEffect(viewModel.videoId) { viewModel.startPlayback() }
    DisposableEffect(Unit) {
        viewModel.setVideoOnScreen(true)
        onDispose { viewModel.setVideoOnScreen(false) }
    }
    FullscreenEffect(fullscreen = fullscreen, lock = lock, upright = uprightFull, onLock = { lock = it })
    BackHandler(enabled = fullscreen) { exitFullscreen() }

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


    // The portrait page is lit by the video itself.
    val light = app.freshtuber.ui.liquid.LocalLiquidLight.current
    var lightLeft by remember { mutableStateOf(false) }
    DisposableEffect(light) {
        light?.enterPlayer()
        onDispose { light?.setPlayerCovered(false); if (!lightLeft) light?.exitPlayer() }
    }
    val grab: ((android.graphics.Bitmap) -> Unit)? = light?.let { l -> { bitmap -> l.pushVideoFrame(bitmap) } }

    val inPip = LocalInPictureInPicture.current
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
    val onPrevious: () -> Unit = { if (playerState.history.isNotEmpty()) viewModel.playPrevious() }
    SideEffect { light?.setPlayerCovered(fullscreen && !inPip) }

    // The picture shown until this page's surface has the video: the frame or thumbnail it flew
    // in with, else the video's thumbnail.
    val handoff = LocalVideoHandoff.current
    val videoId = viewModel.videoId
    DisposableEffect(handoff, videoId) {
        handoff?.pageVideoId = videoId
        onDispose { if (handoff?.pageVideoId == videoId) handoff.pageVideoId = null }
    }
    val poster = handoff?.poster(videoId) ?: now?.thumbnailUrl?.takeIf { now.videoId == videoId } ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    // The page is lit by its video's thumbnail from the first frame, not only once the stream is known.
    val lightThumbnail = handoff?.thumbnail(videoId) ?: now?.thumbnailUrl?.takeIf { now.videoId == videoId } ?: "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
    LaunchedEffect(light, lightThumbnail) { light?.setVideoThumbnail(lightThumbnail) }
    val surface = remember { VideoSurfaceHandle() }
    var surfaceOff by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)

    BoxWithConstraints(Modifier.fillMaxSize().then(if (fullscreen || inPip) Modifier.background(Color.Black) else Modifier)) {
        val details = state.details
        val pageHeight = maxHeight - with(density) { statusTop.toDp() }
        // Upright videos get more of the page, wide ones keep the top 40 %.
        val share = when { videoAspect < 0.8f -> 0.56f; videoAspect < 1.2f -> 0.48f; else -> 0.4f }
        val videoHeight by androidx.compose.animation.core.animateDpAsState(minOf(maxWidth / videoAspect, pageHeight * share),
            androidx.compose.animation.core.tween(320, easing = app.freshtuber.ui.motion.Motion.Emphasized), label = "videoHeight")
        val videoHeightPx = with(density) { videoHeight.roundToPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        // Unpinned, the video is the list's first row: it scrolls away and returns at the top.
        // The surface itself never moves in the composition, only its offset, so it never goes black.
        val scrolls = !pinned && details != null
        // Adding or removing the video row keeps the old first row in place; start from the top instead.
        LaunchedEffect(scrolls) { listState.scrollToItem(0) }
        val videoOffset by remember(scrolls, videoHeightPx) { derivedStateOf {
            if (!scrolls) 0 else if (listState.firstVisibleItemIndex == 0) -listState.firstVisibleItemScrollOffset else -videoHeightPx
        } }
        val big = fullscreen || inPip
        // Where the picture sits on screen, for pictures flying in and out.
        val videoRect = if (big) {
            val fitW = minOf(widthPx, heightPx * videoAspect)
            val fitH = fitW / videoAspect
            androidx.compose.ui.geometry.Rect((widthPx - fitW) / 2f, (heightPx - fitH) / 2f, (widthPx + fitW) / 2f, (heightPx + fitH) / 2f)
        } else androidx.compose.ui.geometry.Rect(0f, statusTop.toFloat(), widthPx, statusTop.toFloat() + videoHeightPx)
        val currentRect by rememberUpdatedState(videoRect.translate(0f, if (big) 0f else videoOffset.toFloat()))
        LaunchedEffect(handoff, videoRect, handoff?.flight) { handoff?.land(videoId, FlightTarget.Page, videoRect, 0f) }
        LaunchedEffect(settled) { if (settled) handoff?.arrived(videoId, FlightTarget.Page) }
        // Leaving: keep a copy of the picture, drop the surface (it cannot fade) and, when the
        // floating window takes over, send the picture flying into it.
        LaunchedEffect(leaving) {
            if (!leaving) return@LaunchedEffect
            if (light != null && !lightLeft) { lightLeft = true; light.setPlayerCovered(false); light.exitPlayer() }
            val toMini = handoff?.miniExpected == true && viewModel.player.currentMediaItem?.mediaId == videoId
            val from = currentRect
            val frame = surface.capture()
            frame?.let { handoff?.setPoster(videoId, it) }
            surfaceOff = true
            val visible = from.bottom > statusTop + 1f
            if (toMini && visible) handoff?.launch(videoId, frame ?: poster, from, 0f, FlightTarget.Mini)
        }

        if (!big) PortraitPage(details, state, navigation, listState, padHide, padUi, playerState, viewModel, now, left, pinned, scrolls, videoHeight,
            captionsOn, onSignIn, onBack, onNext, onPrevious, enterFullscreen, onSpeed, onCaptions, onBoost, { moreOpen = true }, pages)

        // The one video surface of this page: portrait, fullscreen and the system's floating
        // window all use it, so turning the phone never recreates it.
        Box((if (big) Modifier.fillMaxSize() else Modifier.fillMaxWidth().padding(top = with(density) { statusTop.toDp() }).height(videoHeight)
            .offset { androidx.compose.ui.unit.IntOffset(0, videoOffset) }.clipToBounds())
            // Hidden while its picture is still flying in, or once it has flown off to the floating window.
            .graphicsLayer { alpha = if (handoff != null && (handoff.awaiting(videoId, FlightTarget.Page) || handoff.departed(videoId, FlightTarget.Mini))) 0f else 1f }
            .background(Color.Black)
            .onGloballyPositioned { c ->
                if (!big) {
                    val p = c.positionInRoot()
                    light?.setVideoRect(androidx.compose.ui.geometry.Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height))
                }
            }) {
            VideoSurface(viewModel.player, Modifier.fillMaxSize(), onFrame = if (big) null else grab,
                active = (settled || leaving || inPip) && !surfaceOff, poster = poster, mediaId = videoId, handle = surface,
                posterScale = if (big) ContentScale.Fit else ContentScale.Crop)
            when {
                inPip -> Unit
                fullscreen -> FullscreenPlayer(
                    player = viewModel.player,
                    state = playerState,
                    ui = padUi,
                    pages = pages,
                    left = left,
                    gesturesEnabled = gestures,
                    captionsOn = captionsOn,
                    captionsAvailable = !now?.subtitles.isNullOrEmpty(),
                    hasNext = playerState.queue.isNotEmpty(),
                    showRotateHint = rotateHint,
                    onExit = exitFullscreen,
                    onNext = onNext,
                    onSpeed = onSpeed,
                    onCaptions = onCaptions,
                    onBoost = onBoost,
                    onRetry = viewModel::retryPlayback,
                )
                else -> PortraitVideoLayer(viewModel.player, playerState, onMinimize = onBack, onRetry = viewModel::retryPlayback)
            }
        }
        val scrub = padUi.scrubMs
        if (scrub != null && !big) {
            val duration = viewModel.player.duration.coerceAtLeast(1)
            val fraction = (scrub.toFloat() / duration).coerceIn(0f, 1f)
            val x = (18.dp + (maxWidth - 36.dp) * fraction - 76.dp).coerceIn(8.dp, maxWidth - 160.dp)
            ScrubPreview(now?.storyboard, scrub, now?.chapters.orEmpty(), padUi.fine, fanHint = false, pages = pages,
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = padUi.zoneHeight + 8.dp).offset(x = x))
        }
    }

    if (inPip) return
    if (moreOpen) PlayerMoreSheet(playerState, state, controller, viewModel, navigation::openChannel) { moreOpen = false }
    if (comments.opened) CommentsSheet(comments, viewModel, navigation::openChannel)
    if (picker.opened) PlaylistPickerSheet(picker, viewModel)
}

/** Everything of the portrait page around the video: title, list, actions and the pad. */
@Composable
private fun PortraitPage(
    details: VideoDetails?, state: VideoUiState, navigation: FeedNavigation, listState: androidx.compose.foundation.lazy.LazyListState,
    padHide: ScrollHide, padUi: PadUi, playerState: app.freshtuber.player.PlayerState, viewModel: VideoViewModel,
    now: app.freshtuber.player.NowPlaying?, left: Boolean, pinned: Boolean, scrolls: Boolean, videoHeight: androidx.compose.ui.unit.Dp,
    captionsOn: Boolean, onSignIn: () -> Unit, onBack: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onFullscreen: () -> Unit,
    onSpeed: () -> Unit, onCaptions: () -> Unit, onBoost: (Boolean) -> Unit, onMore: () -> Unit, pages: StoryboardPages,
) {
    val statusBar = WindowInsets.statusBars
    val density = LocalDensity.current
    Column(Modifier.fillMaxSize()
        // Black under the status bar only; below it the background light shows through.
        .drawBehind { drawRect(Color.Black, size = androidx.compose.ui.geometry.Size(size.width, statusBar.getTop(this).toFloat())) }
        .statusBarsPadding().clipToBounds()) {
        if (!scrolls) Spacer(Modifier.fillMaxWidth().height(videoHeight))
        Box(Modifier.weight(1f)) {
            // Loading and errors fade into the page instead of swapping in.
            androidx.compose.animation.Crossfade(when {
                state.loading -> 0
                details == null -> 1
                else -> 2
            }, animationSpec = androidx.compose.animation.core.tween(320), label = "pageBody") { phase ->
                when {
                    phase == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    phase == 1 || details == null -> LoadErrorState(state.error ?: LoadError.Unknown, viewModel::load, onSignIn)
                    else -> VideoBody(details, state, navigation, listState, offline = now?.isOffline == true,
                        videoSpace = if (scrolls) videoHeight else null, modifier = Modifier.nestedScroll(padHide.connection))
                }
            }
        }
        // Everything pressed sits here, under the thumb.
        Column(Modifier.fillMaxWidth()
            .onSizeChanged { padUi.zoneHeight = with(density) { it.height.toDp() } }
            .navigationBarsPadding().padding(bottom = 14.dp)) {
            val padShown = padHide.visible || padUi.active || padUi.scrubMs != null
            androidx.compose.animation.AnimatedVisibility(!padShown, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                MiniProgress(viewModel.player)
            }
            PlayerActions(state, viewModel, left, pinned = pinned, onPin = { viewModel.setVideoPinned(!pinned) }, onMore = onMore)
            androidx.compose.animation.AnimatedVisibility(padShown,
                enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(320, easing = app.freshtuber.ui.motion.Motion.Decelerate)) +
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240)),
                exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(260, easing = app.freshtuber.ui.motion.Motion.Emphasized)) +
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160))) {
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
                    hasPrevious = playerState.history.isNotEmpty(),
                    onPrevious = onPrevious,
                    onMinimize = onBack,
                    onFullscreen = onFullscreen,
                    onSpeed = onSpeed,
                    onCaptions = onCaptions,
                    onBoost = onBoost,
                )
            }
        }
    }
}

/** Like, dislike, comments, subscribe, pin, more; the first one sits under the thumb. */
@Composable
private fun PlayerActions(state: VideoUiState, viewModel: VideoViewModel, left: Boolean, pinned: Boolean, onPin: () -> Unit, onMore: () -> Unit) {
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
        Action(if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, stringResource(R.string.player_pin_short),
            stringResource(if (pinned) R.string.player_unpin else R.string.player_pin), pinned, true, onPin),
        Action(Icons.Rounded.MoreHoriz, stringResource(R.string.player_more), stringResource(R.string.video_more), false, true, onMore),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
        handOrder(actions, left).forEach { action ->
            Surface(onClick = action.onClick, enabled = action.enabled, modifier = Modifier.weight(1f).height(50.dp)
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

/** Hidden system bars while full screen; the orientation follows [lock] and the phone's turns. */
@Composable
private fun FullscreenEffect(fullscreen: Boolean, lock: OrientationLock, upright: Boolean, onLock: (OrientationLock) -> Unit) {
    val activity = LocalActivity.current ?: return
    val currentLock by rememberUpdatedState(lock)
    val holdUpright by rememberUpdatedState(upright)
    val setLock by rememberUpdatedState(onLock)
    DisposableEffect(activity) {
        var seenLandscape = false
        val listener = object : android.view.OrientationEventListener(activity) {
            override fun onOrientationChanged(degrees: Int) {
                if (holdUpright) return
                val (next, seen) = FullscreenOrientation.next(currentLock, seenLandscape, FullscreenOrientation.physical(degrees))
                seenLandscape = seen
                if (next != currentLock) setLock(next)
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose {
            listener.disable()
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(lock, upright) {
        activity.requestedOrientation = if (upright) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else when (lock) {
            OrientationLock.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationLock.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationLock.None -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(fullscreen) {
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
        onDispose {
            if (android.os.Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = when (oldCutout) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                    else -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
            insets.isAppearanceLightStatusBars = oldStatusIcons
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** Title, channel, description and up next; scrolls between the video and the controls. */
@Composable
private fun VideoBody(details: VideoDetails, state: VideoUiState, navigation: FeedNavigation, listState: androidx.compose.foundation.lazy.LazyListState,
    offline: Boolean = false, videoSpace: androidx.compose.ui.unit.Dp? = null, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
        // Room for the unpinned video, which is drawn above the list at this row's offset.
        videoSpace?.let { item(key = "video") { Spacer(Modifier.fillMaxWidth().height(it)) } }
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
        Text(details.title, style = app.freshtuber.ui.theme.AppType.playerTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
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

/** A thin progress line shown in place of the hidden pad. */
@Composable
private fun MiniProgress(player: androidx.media3.common.Player) {
    val progress = rememberProgress(player)
    val fraction = if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(app.freshtuber.ui.theme.BrandColors.Red))
    }
}
