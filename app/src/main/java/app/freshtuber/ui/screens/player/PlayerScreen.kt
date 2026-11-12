package app.freshtuber.ui.screens.player

import app.freshtuber.ui.motion.blurVisible
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
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
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
import androidx.compose.ui.unit.sp
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
import app.freshtuber.ui.player.PlaybackError
import app.freshtuber.ui.player.*
import app.freshtuber.ui.components.*
import app.freshtuber.ui.player.ClassicVideoControls
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
    onAdvanced: (String, Boolean) -> Unit,
    /** The page has fully arrived (the video surface can appear). */
    settled: Boolean = true,
    currentPage: Boolean = true,
    /** The page is leaving after navigation has committed. */
    departureCommitted: Boolean = false,
    viewModel: VideoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val gestures by viewModel.gesturesEnabled.collectAsStateWithLifecycle()
    val pinnedSetting by viewModel.videoPinned.collectAsStateWithLifecycle()
    val classic = LocalClassic.current
    // YouTube keeps the video on top; the classic layout has no pin.
    val pinned = pinnedSetting || classic
    val listState = rememberLazyListState()
    // The pad slides away while the list is read downward and comes back on the way up.
    val padHide = rememberScrollHide()
    val left = LocalLeftHanded.current
    val messages = LocalAppMessages.current
    val resources = LocalResources.current
    var qualityAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var qualityOpen by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    val initialFullscreen = viewModel.initialFullscreen
    val large = isLargeScreen()
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
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var lock by rememberSaveable {
        mutableStateOf(if (initialFullscreen && !large && !vertical) OrientationLock.Landscape else OrientationLock.None)
    }
    var uprightFull by rememberSaveable { mutableStateOf(initialFullscreen && vertical && !large) }
    var largeFull by rememberSaveable { mutableStateOf(initialFullscreen && large) }
    // Turning the phone opens fullscreen like YouTube; the button and the pad swipe ask for it.
    val fullscreen = if (large) largeFull else uprightFull || lock == OrientationLock.Landscape || (landscape && lock != OrientationLock.Portrait)
    val enterFullscreen = {
        if (large) largeFull = true
        else if (vertical && !landscape) uprightFull = true
        else { lock = OrientationLock.Landscape; rotateHint = !landscape }
    }
    val exitFullscreen = { largeFull = false; uprightFull = false; lock = if (landscape && !large) OrientationLock.Portrait else OrientationLock.None; rotateHint = false }
    val fold = LocalFoldPosture.current
    // A half-open phone with a horizontal fold: the video above the fold, the controls below.
    val tabletop = fold.tabletop && fold.hingeTop > 0 && !LocalInPictureInPicture.current
    // The next video is wide: it turns the phone like any other; a vertical one stays upright.
    LaunchedEffect(vertical) {
        if (uprightFull && !vertical) { uprightFull = false; lock = OrientationLock.Landscape }
        else if (lock == OrientationLock.Landscape && vertical) { lock = OrientationLock.None; uprightFull = true }
    }
    val padUi = remember { PadUi() }
    val pages = remember { StoryboardPages() }
    var lastSubtitle by remember(playerState.nowPlaying?.videoId) { mutableIntStateOf(-1) }

    // Coming back to this page (e.g. from a suggested video) brings its video back.
    LaunchedEffect(viewModel.videoId, currentPage) {
        if (currentPage) viewModel.startPlayback()
    }
    DisposableEffect(Unit) {
        viewModel.setVideoOnScreen(true)
        onDispose { viewModel.setVideoOnScreen(false) }
    }
    if (currentPage) {
        FullscreenEffect(fullscreen = fullscreen || tabletop, lock = lock, upright = uprightFull, onLock = { lock = it }, rotate = !large && !tabletop)
    }
    BackHandler(enabled = currentPage && fullscreen) { exitFullscreen() }

    val keepFullscreen by rememberUpdatedState(fullscreen)
    val advance by rememberUpdatedState(onAdvanced)
    LaunchedEffect(viewModel) {
        viewModel.playerEvents.collect { event ->
            when (event) {
                is PlayerEvent.SegmentSkipped -> messages?.show(resources.getString(R.string.player_sponsor_skipped))
                is PlayerEvent.Advanced -> advance(event.videoId, keepFullscreen)
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
    SideEffect { light?.setPlayerCovered(inPip) }

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
    val resuming = remember(videoId) { viewModel.player.currentMediaItem?.mediaId == videoId &&
        viewModel.player.playbackState != androidx.media3.common.Player.STATE_IDLE && now?.videoId == videoId }
    val surface = remember { VideoSurfaceHandle() }
    var surfaceOff by remember { mutableStateOf(false) }
    var hasArrived by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)

    CompositionLocalProvider(LocalQualityAnchor provides { qualityAnchor = it }) {
    BoxWithConstraints(Modifier.fillMaxSize().then(if (inPip || tabletop) Modifier.background(Color.Black) else Modifier)) {
        val details = state.details
        val pageHeight = maxHeight - with(density) { statusTop.toDp() }
        // A wide page (unfolded foldable held sideways, tablet): the video and its details on the
        // left, up next on the right.
        val twoPane = large && !fullscreen && !inPip && !tabletop && maxWidth > maxHeight
        val paneWidth = if (twoPane) maxWidth * 0.62f else maxWidth
        // Upright videos get more of the page, wide ones keep the top 40 %.
        // A near-square page (unfolded foldable held upright) gives a wide video more height, so it
        // fills the width instead of sitting between black bars.
        val squareish = !twoPane && maxHeight < maxWidth * 1.4f
        val share = when { twoPane -> 0.7f; videoAspect < 0.8f -> 0.56f; videoAspect < 1.2f -> 0.48f; squareish -> 0.6f; else -> 0.4f }
        val videoHeight by androidx.compose.animation.core.animateDpAsState(minOf(paneWidth / videoAspect, pageHeight * share),
            androidx.compose.animation.core.tween(320, easing = app.freshtuber.ui.motion.Motion.Emphasized), label = "videoHeight")
        val videoHeightPx = with(density) { videoHeight.roundToPx() }
        // Never wider than the picture: no black bars at the sides, the page shows around it.
        val videoWidth = minOf(paneWidth, videoHeight * videoAspect)
        val videoInset = (paneWidth - videoWidth) / 2
        val widthPx = with(density) { maxWidth.toPx() }
        val paneWidthPx = with(density) { paneWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val hingeTop = with(density) { fold.hingeTop.toDp() }.coerceAtMost(maxHeight)
        val belowHinge = (maxHeight - with(density) { fold.hingeBottom.toDp() }).coerceAtLeast(0.dp)
        // Unpinned, the video is the list's first row: it scrolls away and returns at the top.
        // The surface itself never moves in the composition, only its offset, so it never goes black.
        val scrolls = !pinned && details != null && !twoPane
        // Compose keeps keyed rows anchored. Only the video spacer and the very top need an override.
        var lastScrolls by remember { mutableStateOf(scrolls) }
        SideEffect {
            if (lastScrolls != scrolls) {
                if (listState.firstVisibleItemIndex == 0 && (!scrolls || listState.firstVisibleItemScrollOffset == 0)) {
                    listState.requestScrollToItem(0)
                }
                lastScrolls = scrolls
            }
        }
        val videoOffset by remember(scrolls, videoHeightPx) { derivedStateOf {
            if (!scrolls) 0 else if (listState.firstVisibleItemIndex == 0) -listState.firstVisibleItemScrollOffset else -videoHeightPx
        } }
        val big = fullscreen || inPip || tabletop
        val zoom = remember(videoId, big) { VideoZoom() }
        // Where the picture sits on screen, for pictures flying in and out.
        val videoRect = if (tabletop && !inPip) {
            val areaH = fold.hingeTop.toFloat()
            val fitW = minOf(widthPx, areaH * videoAspect)
            val fitH = fitW / videoAspect
            androidx.compose.ui.geometry.Rect((widthPx - fitW) / 2f, (areaH - fitH) / 2f, (widthPx + fitW) / 2f, (areaH + fitH) / 2f)
        } else if (big) {
            val fitW = minOf(widthPx, heightPx * videoAspect)
            val fitH = fitW / videoAspect
            androidx.compose.ui.geometry.Rect((widthPx - fitW) / 2f, (heightPx - fitH) / 2f, (widthPx + fitW) / 2f, (heightPx + fitH) / 2f)
        } else with(density) { androidx.compose.ui.geometry.Rect(videoInset.toPx(), statusTop.toFloat(), (videoInset + videoWidth).toPx(), statusTop.toFloat() + videoHeightPx) }
        LaunchedEffect(fullscreen, inPip, videoRect) {
            if (fullscreen && !inPip) light?.setVideoRect(videoRect)
        }
        val currentRect by rememberUpdatedState(videoRect.translate(0f, if (big) 0f else videoOffset.toFloat()))
        LaunchedEffect(handoff, videoRect, handoff?.flight) { handoff?.land(videoId, FlightTarget.Page, videoRect, 0f) }
        LaunchedEffect(settled) {
            if (settled) {
                hasArrived = true
                handoff?.arrived(videoId, FlightTarget.Page)
            }
        }
        // Leaving: keep a copy of the picture, drop the surface (it cannot fade) and, when the
        // floating window takes over, send the picture flying into it.
        LaunchedEffect(departureCommitted) {
            if (!departureCommitted) return@LaunchedEffect
            if (light != null && !lightLeft) { lightLeft = true; light.setPlayerCovered(false); light.exitPlayer() }
            val toMini = handoff?.miniExpected == true && viewModel.player.currentMediaItem?.mediaId == videoId
            val from = currentRect
            val frame = surface.capture()
            frame?.let { handoff?.setPoster(videoId, it) }
            surfaceOff = true
            val visible = from.bottom > statusTop + 1f
            if (toMini && visible) handoff?.launch(videoId, frame ?: poster, from, 0f, FlightTarget.Mini)
        }

        val pageSegments = playerState.segments.takeIf { now?.videoId == viewModel.videoId }.orEmpty()
        if (!big) Box(Modifier.width(paneWidth).fillMaxHeight()) {
            if (classic) ClassicPortraitPage(details, state, navigation, listState, viewModel, now, videoHeight, { moreOpen = true }, related = !twoPane)
            else PortraitPage(details, state, navigation, listState, padHide, padUi, playerState, viewModel, now, left, pinned, scrolls, videoHeight,
                captionsOn, onBack, onNext, onPrevious, enterFullscreen, onSpeed, onCaptions, onBoost, { qualityOpen = true }, { moreOpen = true }, pages, related = !twoPane)
        }
        if (twoPane && details != null) UpNextPane(details, navigation, Modifier.align(Alignment.TopEnd).width(maxWidth - paneWidth).fillMaxHeight())
        val fullControls: @Composable (Modifier, Boolean) -> Unit = { controlsModifier, below ->
            if (classic) ClassicVideoControls(viewModel.player, playerState, fullscreen = true, segments = pageSegments,
                hasPrevious = playerState.history.isNotEmpty(), hasNext = playerState.queue.isNotEmpty(),
                captionsOn = captionsOn, captionsAvailable = !now?.subtitles.isNullOrEmpty(),
                onPrevious = onPrevious, onNext = onNext, onFullscreen = exitFullscreen,
                onMinimize = onBack, onCaptions = { onCaptions() }, onMore = { moreOpen = true }, onQuality = { qualityOpen = true }, onScrubPosition = { padUi.scrubMs = it },
                title = now?.title ?: details?.title, alwaysShown = below, modifier = controlsModifier)
            else Box(controlsModifier) { FullscreenPlayer(
                player = viewModel.player,
                state = playerState,
                ui = padUi,
                pages = pages,
                left = left,
                gesturesEnabled = gestures,
                captionsOn = captionsOn,
                captionsAvailable = !now?.subtitles.isNullOrEmpty(),
                hasNext = playerState.queue.isNotEmpty(),
                showRotateHint = rotateHint && !below,
                onExit = exitFullscreen,
                onNext = onNext,
                onQuality = { qualityOpen = true },
                onSpeed = onSpeed,
                onCaptions = onCaptions,
                onBoost = onBoost,
            ) }
        }

        // The one video surface of this page: portrait, fullscreen and the system's floating
        // window all use it, so turning the phone never recreates it.
        Box((if (tabletop && !inPip) Modifier.fillMaxWidth().height(hingeTop) else if (big) Modifier.fillMaxSize()
            else Modifier.padding(start = videoInset, top = with(density) { statusTop.toDp() }).width(videoWidth).height(videoHeight)
            .offset { androidx.compose.ui.unit.IntOffset(0, videoOffset) }.clipToBounds())
            // Hidden while its picture is still flying in, or once it has flown off to the floating window.
            .graphicsLayer { alpha = if (handoff != null && (handoff.awaiting(videoId, FlightTarget.Page) || handoff.departed(videoId, FlightTarget.Mini))) 0f else 1f }
            .clipToBounds().videoZoom(zoom, enabled = !inPip && !tabletop, aspect = videoAspect, panAllowed = { point, viewport ->
                if (classic) point.y < viewport.y - with(density) { 72.dp.toPx() } else !fullscreen || !FanGeometry(viewport.x / density.density, viewport.y / density.density, left)
                    .inFan(point.x / density.density, point.y / density.density)
            })
            .background(if (inPip || tabletop) Color.Black else Color.Transparent)
            .onGloballyPositioned { c ->
                if (!fullscreen && !inPip) {
                    val p = c.positionInRoot()
                    light?.setVideoRect(androidx.compose.ui.geometry.Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height))
                }
            }) {
            VideoSurface(viewModel.player, Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom.scale; scaleY = zoom.scale
                translationX = zoom.offset.x; translationY = zoom.offset.y
            }, texture = true, onFrame = if (inPip) null else grab,
                active = (resuming || settled || hasArrived || inPip) && !surfaceOff, poster = poster, mediaId = videoId, handle = surface,
                posterScale = if (big) ContentScale.Fit else ContentScale.Crop)
            when {
                inPip -> Unit
                tabletop -> Unit
                fullscreen -> fullControls(Modifier.fillMaxSize(), false)
                classic -> ClassicVideoControls(viewModel.player, playerState, fullscreen = false, segments = pageSegments,
                    hasPrevious = playerState.history.isNotEmpty(), hasNext = playerState.queue.isNotEmpty(),
                    captionsOn = captionsOn, captionsAvailable = !now?.subtitles.isNullOrEmpty(),
                    onPrevious = onPrevious, onNext = onNext, onFullscreen = enterFullscreen,
                    onMinimize = onBack, onCaptions = { onCaptions() }, onMore = { moreOpen = true }, onQuality = { qualityOpen = true }, onScrubPosition = { padUi.scrubMs = it })
                else -> PortraitVideoLayer(viewModel.player, playerState, onMinimize = onBack)
            }
            // The pad shows the time in portrait and the controls do in full screen; only the classic
            // portrait layout, which has neither, keeps it over the picture.
            if (classic && !inPip && !tabletop && !fullscreen && now?.isLive != true) PlaybackTime(rememberProgress(viewModel.player), padUi.scrubMs,
                Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 56.dp))
        }
        // Below the fold the controls get the whole lower half.
        if (tabletop && !inPip) fullControls(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(belowHinge), true)
        val scrub = padUi.scrubMs
        if (scrub != null && !big) {
            val duration = viewModel.player.duration.coerceAtLeast(1)
            val fraction = (scrub.toFloat() / duration).coerceIn(0f, 1f)
            val x = (18.dp + (maxWidth - 36.dp) * fraction - 76.dp).coerceIn(8.dp, maxWidth - 160.dp)
            ScrubPreview(now?.storyboard, scrub, now?.chapters.orEmpty(), padUi.fine, fanHint = false, pages = pages,
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = padUi.zoneHeight + 8.dp).offset(x = x))
        }
        // What a pad gesture just did ("+10 с → 4:10") floats above the control zone, never over the buttons.
        if (!big && !classic) androidx.compose.animation.AnimatedVisibility(padUi.bubble != null,
            Modifier.align(Alignment.BottomCenter).padding(bottom = padUi.zoneHeight + 8.dp),
            enter = app.freshtuber.ui.motion.blurIn(180, fromScale = 0.85f),
            exit = app.freshtuber.ui.motion.blurOut(180)) {
            var shown by remember { mutableStateOf("") }
            padUi.bubble?.let { shown = it }
            Box(blurVisible(10.dp, 180, 180)) { Bubble(shown) }
        }
        if (currentPage && !inPip) {
            val streamError = playerState.error?.takeIf { controller.isRequested(videoId) }
            val error = streamError ?: state.error.takeIf { details == null }
            if (error != null) PlaybackError(error, onRetry = {
                if (details == null && state.error != null) viewModel.load()
                if (streamError != null) viewModel.retryPlayback()
            }, onSignIn = onSignIn)
        }
    }

    }
    if (inPip) return
    if (qualityOpen) QualityChooser(playerState, controller, qualityAnchor, fullscreen) { qualityOpen = false }
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
    captionsOn: Boolean, onBack: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onFullscreen: () -> Unit,
    onSpeed: () -> Unit, onCaptions: () -> Unit, onBoost: (Boolean) -> Unit, onQuality: () -> Unit, onMore: () -> Unit, pages: StoryboardPages,
    related: Boolean = true,
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
            androidx.compose.animation.AnimatedContent(when {
                state.loading -> 0
                details == null -> 1
                else -> 2
            }, transitionSpec = { app.freshtuber.ui.motion.blurSwap(fromScale = 0.98f) }, label = "pageBody") { phase ->
                Box(Modifier.fillMaxSize().then(blurVisible(14.dp))) { when {
                    phase == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    phase == 1 || details == null -> Unit
                    else -> VideoBody(details, state, navigation, listState, offline = now?.isOffline == true,
                        onTimestamp = viewModel::seekTo, onSubscribe = viewModel::toggleSubscription,
                        videoSpace = if (scrolls) videoHeight else null, related = related, modifier = Modifier.nestedScroll(padHide.connection))
                } }
            }
        }
        // Everything pressed sits here, under the thumb.
        Column(Modifier.fillMaxWidth()
            .onSizeChanged { padUi.zoneHeight = with(density) { it.height.toDp() } }
            .navigationBarsPadding().padding(bottom = 14.dp)) {
            val padShown = padHide.visible || padUi.active || padUi.scrubMs != null
            app.freshtuber.ui.motion.BlurVisibility(!padShown, collapse = true, max = 8.dp) {
                MiniProgress(viewModel.player, playerState.segments.takeIf { now?.videoId == viewModel.videoId }.orEmpty())
            }
            PlayerActions(state, viewModel, left, pinned = pinned, onPin = { viewModel.setVideoPinned(!pinned) }, onMore = onMore)
            app.freshtuber.ui.motion.BlurVisibility(padShown, collapse = true, max = 18.dp, enterMs = 320, exitMs = 240, fromScale = 0.96f) {
                PlayerPad(
                    player = viewModel.player,
                    ui = padUi,
                    speed = playerState.speed,
                    chapters = now?.chapters.orEmpty(),
                    live = now?.isLive == true,
                    left = left,
                    hasNext = playerState.queue.isNotEmpty(),
                    segments = playerState.segments.takeIf { now?.videoId == viewModel.videoId }.orEmpty(),
                    onNext = onNext,
                    hasPrevious = playerState.history.isNotEmpty(),
                    onPrevious = onPrevious,
                    onMinimize = onBack,
                    onFullscreen = onFullscreen,
                    onQuality = onQuality,
                    onSpeed = onSpeed,
                    onBoost = onBoost,
                )
            }
        }
    }
}

/** «Как музыку»: the video goes on as a song in the music player; provided by the app root. */
val LocalAsMusic = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/** Like, dislike, comments, subscribe, pin, more; the first one sits under the thumb. */
@Composable
private fun PlayerActions(state: VideoUiState, viewModel: VideoViewModel, left: Boolean, pinned: Boolean, onPin: () -> Unit, onMore: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val details = state.details
    val activity = LocalActivity.current as? app.freshtuber.MainActivity
    val asMusic = LocalAsMusic.current
    val liked = state.likeStatus == LikeStatus.Like
    val disliked = state.likeStatus == LikeStatus.Dislike
    val likeCount = (if (liked) details?.likeCountTextLiked ?: details?.likeCountText else details?.likeCountText)?.takeIf { it.any(Char::isDigit) }
    data class Action(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val description: String,
        val selected: Boolean, val enabled: Boolean, val onClick: () -> Unit)
    val likeLabel = stringResource(R.string.video_like)
    val dislikeLabel = stringResource(R.string.video_dislike)
    val actions = listOf(
        Action(if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp, likeCount ?: likeLabel, likeLabel, liked, details != null, viewModel::toggleLike),
        Action(if (disliked) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt,
            state.dislikes?.let { compactNumber(it, locale) } ?: dislikeLabel, dislikeLabel, disliked, details != null, viewModel::toggleDislike),
        Action(Icons.Rounded.ChatBubbleOutline, stringResource(R.string.player_comments_short), stringResource(R.string.video_comments), false, details != null, viewModel::openComments),
        Action(if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, stringResource(R.string.player_pin_short),
            stringResource(if (pinned) R.string.player_unpin else R.string.player_pin), pinned, true, onPin),
        Action(Icons.Rounded.Headphones, stringResource(R.string.player_background_short), stringResource(R.string.player_audio_only), false, true) {
            activity?.playInBackground()
        },
        Action(Icons.Rounded.MusicNote, stringResource(R.string.player_as_music_short), stringResource(R.string.player_as_music), false,
            asMusic != null) { asMusic?.invoke() },
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
                    // Seven labels share the width: on a narrow screen they get smaller instead of losing letters.
                    Text(action.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
                        modifier = Modifier.padding(top = 3.dp, start = 1.dp, end = 1.dp))
                }
            }
        }
    }
}

/** Hidden system bars while full screen; the orientation follows [lock] and the phone's turns. */
@Composable
private fun FullscreenEffect(fullscreen: Boolean, lock: OrientationLock, upright: Boolean, onLock: (OrientationLock) -> Unit, rotate: Boolean = true) {
    val activity = LocalActivity.current ?: return
    val currentLock by rememberUpdatedState(lock)
    val holdUpright by rememberUpdatedState(upright || !rotate)
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
    LaunchedEffect(lock, upright, rotate) {
        activity.requestedOrientation = if (!rotate) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        else if (upright) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else when (lock) {
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
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
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
    offline: Boolean = false, onTimestamp: (Long) -> Unit, onSubscribe: () -> Unit,
    videoSpace: androidx.compose.ui.unit.Dp? = null, related: Boolean = true, modifier: Modifier = Modifier) {
    val columns = feedColumns()
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
        // Room for the unpinned video, which is drawn above the list at this row's offset.
        videoSpace?.let { item(key = "video") { Spacer(Modifier.fillMaxWidth().height(it)) } }
        item(key = "info") { TitleBlock(details, offline, onTimestamp = onTimestamp) }
        details.channel?.let { channel ->
            item(key = "channel") {
                Row(Modifier.fillMaxWidth().clickable { navigation.openChannel(channel.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    coil3.compose.AsyncImage(channel.avatarUrl, null, Modifier.size(36.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(10.dp))
                    Text(channel.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    ChannelSubscribeButton(state.subscribed == true, onSubscribe)
                }
            }
        }
        if (related && details.related.isNotEmpty()) {
            item(key = "related-title") {
                Text(stringResource(R.string.player_up_next), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp))
            }
            relatedRows(details.related, columns, navigation)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TitleBlock(details: VideoDetails, offline: Boolean = false, compact: Boolean = false, onTimestamp: (Long) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(details.title, style = app.freshtuber.ui.theme.AppType.playerTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(if (offline) stringResource(R.string.download_complete) else null, details.viewsText, details.dateText).joinToString(" · ") +
                if (compact && !details.description.isNullOrBlank()) "  …" + stringResource(R.string.video_more).lowercase() else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        details.description?.takeIf { it.isNotBlank() && !compact }?.let {
            VideoText(
                it, details.descriptionLinks, details.id, onTimestamp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
    if (expanded) app.freshtuber.ui.components.BlurSheet(onDismissRequest = { expanded = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(details.title, style = MaterialTheme.typography.titleLarge)
            details.description?.let { VideoText(it, details.descriptionLinks, details.id, onTimestamp = { ms -> expanded = false; onTimestamp(ms) }) }
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
private fun MiniProgress(player: androidx.media3.common.Player, segments: List<app.freshtuber.data.extras.SkipSegment>) {
    val progress = rememberProgress(player)
    val fraction = if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) else 0f
    val spans = remember(segments, progress.durationMs) { app.freshtuber.ui.player.SegmentMarks.spans(segments, progress.durationMs) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawRect(track)
        drawRect(app.freshtuber.ui.theme.BrandColors.Red, size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height))
        spans.forEach { (a, b, color) -> drawRect(color, androidx.compose.ui.geometry.Offset(size.width * a, 0f), androidx.compose.ui.geometry.Size(size.width * (b - a), size.height)) }
    }
}

/** The classic watch page: the video on top, then YouTube's own order of title, channel, actions, comments and up next. */
@Composable
private fun ClassicPortraitPage(details: VideoDetails?, state: VideoUiState, navigation: FeedNavigation, listState: androidx.compose.foundation.lazy.LazyListState,
    viewModel: VideoViewModel, now: app.freshtuber.player.NowPlaying?, videoHeight: androidx.compose.ui.unit.Dp, onMore: () -> Unit,
    related: Boolean = true) {
    val statusBar = WindowInsets.statusBars
    Column(Modifier.fillMaxSize()
        .drawBehind { drawRect(Color.Black, size = androidx.compose.ui.geometry.Size(size.width, statusBar.getTop(this).toFloat())) }
        .statusBarsPadding()) {
        Spacer(Modifier.fillMaxWidth().height(videoHeight))
        androidx.compose.animation.AnimatedContent(when {
            state.loading -> 0
            details == null -> 1
            else -> 2
        }, Modifier.weight(1f), transitionSpec = { app.freshtuber.ui.motion.blurSwap(fromScale = 0.98f) }, label = "classicBody") { phase ->
            Box(Modifier.fillMaxSize().then(blurVisible(14.dp))) { when {
                phase == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                phase == 1 || details == null -> Unit
                else -> ClassicWatchBody(details, state, navigation, listState, viewModel, offline = now?.isOffline == true, onMore = onMore, related = related)
            } }
        }
    }
}

@Composable
internal fun ClassicWatchBody(details: VideoDetails, state: VideoUiState, navigation: FeedNavigation, listState: androidx.compose.foundation.lazy.LazyListState,
    viewModel: VideoViewModel, offline: Boolean, onMore: () -> Unit, related: Boolean = true, modifier: Modifier = Modifier) {
    val columns = feedColumns()
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
        item(key = "info") { TitleBlock(details, offline, compact = true, onTimestamp = viewModel::seekTo) }
        details.channel?.let { channel ->
            item(key = "channel") {
                val subscribed = state.subscribed == true
                Row(Modifier.fillMaxWidth().clickable { navigation.openChannel(channel.id) }.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    coil3.compose.AsyncImage(channel.avatarUrl, null, Modifier.size(36.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(channel.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        channel.detailText?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                    }
                    ChannelSubscribeButton(subscribed, viewModel::toggleSubscription)
                }
            }
        }
        item(key = "actions") { ClassicActions(state, viewModel, onMore) }
        item(key = "comments") {
            Surface(onClick = viewModel::openComments, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.video_comments), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.UnfoldMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (related) relatedRows(details.related, columns, navigation)
    }
}

/** YouTube's row of grey pills under the channel: like | dislike, share, save, download, more. */
@Composable
private fun ClassicActions(state: VideoUiState, viewModel: VideoViewModel, onMore: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val details = state.details
    val liked = state.likeStatus == LikeStatus.Like
    val disliked = state.likeStatus == LikeStatus.Dislike
    val likeCount = (if (liked) details?.likeCountTextLiked ?: details?.likeCountText else details?.likeCountText)?.takeIf { it.any(Char::isDigit) }
    val resources = LocalResources.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = LocalActivity.current as? app.freshtuber.MainActivity
    val shareTitle = stringResource(R.string.video_share)
    val download = app.freshtuber.ui.components.rememberNotificationPermission { viewModel.download() }
    val pill = MaterialTheme.colorScheme.surfaceContainerHigh
    val asMusic = LocalAsMusic.current
    androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "rate") {
            Row(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(pill), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.fillMaxHeight().clickable(enabled = details != null, onClick = viewModel::toggleLike).padding(start = 12.dp, end = 10.dp)
                    .semantics { contentDescription = "${likeCount ?: ""}"; selected = liked }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp, stringResource(R.string.video_like), Modifier.size(20.dp))
                    likeCount?.let { Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp)) }
                }
                Box(Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
                Row(Modifier.fillMaxHeight().clickable(enabled = details != null, onClick = viewModel::toggleDislike).padding(start = 10.dp, end = 12.dp)
                    .semantics { selected = disliked }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (disliked) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt, stringResource(R.string.video_dislike), Modifier.size(20.dp))
                    state.dislikes?.let { Text(compactNumber(it, locale), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp)) }
                }
            }
        }
        val pills = listOfNotNull(
            asMusic?.let { Triple(Icons.Rounded.MusicNote, resources.getString(R.string.player_as_music)) { it() } },
            Triple(Icons.Rounded.Headphones, resources.getString(R.string.player_audio_only)) { activity?.playInBackground(); Unit },
            Triple(Icons.Rounded.Share, shareTitle) { app.freshtuber.ui.feed.shareVideo(context, viewModel.videoId, shareTitle) },
            Triple(Icons.AutoMirrored.Rounded.PlaylistAdd, resources.getString(R.string.video_save)) { viewModel.openPlaylistPicker() },
            Triple(Icons.Rounded.Download, resources.getString(R.string.feed_download)) { download() },
            Triple(Icons.Rounded.MoreHoriz, resources.getString(R.string.video_more), onMore),
        )
        items(pills.size, key = { "p$it" }) { index ->
            val (icon, label, onClick) = pills[index]
            Row(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(pill).clickable(onClick = onClick).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** Up next as its own column beside the video on a wide page. */
@Composable
private fun UpNextPane(details: VideoDetails, navigation: FeedNavigation, modifier: Modifier = Modifier) {
    LazyColumn(modifier.statusBarsPadding(), contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
        item(key = "title") {
            Text(stringResource(R.string.player_up_next), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp))
        }
        items(details.related, key = { "n:${it.id}" }) { video ->
            VideoCard(video, onClick = { navigation.openVideo(video.id) }, onChannelClick = navigation::openChannel, enableMenu = true)
        }
    }
}

/** Up next under the video; side by side on a wide single-pane page. */
private fun androidx.compose.foundation.lazy.LazyListScope.relatedRows(related: List<app.freshtuber.domain.model.VideoSummary>, columns: Int, navigation: FeedNavigation) {
    items(related.chunked(columns), key = { "r:${it.first().id}" }) { row ->
        Row(Modifier.fillMaxWidth().padding(horizontal = if (columns > 1) 4.dp else 0.dp)) {
            row.forEach { video ->
                VideoCard(video, onClick = { navigation.openVideo(video.id) }, onChannelClick = navigation::openChannel, enableMenu = true,
                    modifier = Modifier.weight(1f))
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
