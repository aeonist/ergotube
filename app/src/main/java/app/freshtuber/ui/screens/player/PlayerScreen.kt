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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
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
    val menuSettings: app.freshtuber.ui.screens.settings.MenuSettingsViewModel = hiltViewModel()
    val leftHanded by menuSettings.leftHanded.collectAsStateWithLifecycle()
    val pinned by menuSettings.pinned.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(pinned) { listState.scrollToItem(0) }
    val groupPositions = remember { mutableStateMapOf<VideoActionGroup, androidx.compose.ui.geometry.Offset>() }
    var actionGroup by remember { mutableStateOf<VideoActionGroup?>(null) }
    val thumbArc by menuSettings.arc.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var settingsPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var fullscreenRequested by rememberSaveable { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullscreen = fullscreenRequested || landscape

    // Coming back to this page (e.g. from a suggested video) brings its video back.
    LaunchedEffect(viewModel.videoId) { viewModel.startPlayback() }
    DisposableEffect(Unit) {
        viewModel.setVideoOnScreen(true)
        onDispose { viewModel.setVideoOnScreen(false) }
    }
    FullscreenEffect(fullscreen = fullscreen, requested = fullscreenRequested)
    BackHandler(enabled = fullscreenRequested) { fullscreenRequested = false }

    val skippedText = stringResource(R.string.player_sponsor_skipped)
    LaunchedEffect(Unit) {
        viewModel.playerEvents.collect { event ->
            when (event) {
                is PlayerEvent.SegmentSkipped -> snackbar.showSnackbar(skippedText, duration = SnackbarDuration.Short)
                is PlayerEvent.Advanced -> onAdvanced(event.videoId)
            }
        }
    }

    val messageText = message?.let { stringResource(it.textRes()) }
    val signInLabel = stringResource(R.string.welcome_sign_in)
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = messageText.orEmpty(),
            actionLabel = if (current == VideoMessage.NeedsSignIn) signInLabel else null,
            duration = SnackbarDuration.Short,
        )
        viewModel.consumeMessage()
        if (result == SnackbarResult.ActionPerformed) onSignIn()
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
    val playerArea: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.background(Color.Black)) {
            VideoSurface(viewModel.player, Modifier.fillMaxSize())
            PlayerOverlay(
                player = viewModel.player,
                state = playerState,
                fullscreen = fullscreen,
                gesturesEnabled = gestures,
                onBack = { if (fullscreenRequested) fullscreenRequested = false else onBack() },
                onToggleFullscreen = { fullscreenRequested = !fullscreen },
                onOpenSettings = { position -> settingsPosition = position; settingsOpen = true },
                onNext = if (playerState.queue.isNotEmpty()) viewModel::playNext else null,
                onRetry = viewModel::retryPlayback,
                onOpenActions = { actionGroup = VideoActionGroup.All },
                actionsExpanded = actionGroup != null,
                leftHanded = leftHanded,
                thumbArc = thumbArc,
            )
        }
    }

    if (LocalInPictureInPicture.current) {
        VideoSurface(viewModel.player, Modifier.fillMaxSize().background(Color.Black))
        return
    }

    Scaffold(modifier = Modifier.semantics { if (settingsOpen || actionGroup != null) hideFromAccessibility() },
        contentWindowInsets = WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (fullscreen) {
            playerArea(Modifier.fillMaxSize().consumeWindowInsets(padding))
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().consumeWindowInsets(padding).background(Color.Black).statusBarsPadding()
                .navigationBarsPadding().background(MaterialTheme.colorScheme.surface).then(Modifier.clipToBounds())) {
                val details = state.details
                val videoHeight = minOf(maxWidth / videoAspect, maxHeight * 0.6f)
                val density = androidx.compose.ui.platform.LocalDensity.current
                val videoHeightPx = with(density) { videoHeight.roundToPx() }
                val videoOffset by remember(pinned, videoHeightPx, details) { derivedStateOf {
                    if (pinned || details == null) 0 else if (listState.firstVisibleItemIndex == 0) -listState.firstVisibleItemScrollOffset else -videoHeightPx
                } }
                Column(Modifier.fillMaxSize()) {
                    if (pinned || details == null) Spacer(Modifier.fillMaxWidth().height(videoHeight))
                    if (pinned && details != null) TitleBlock(details, playerState.nowPlaying?.isOffline == true)
                    Box(Modifier.weight(1f)) {
                        when {
                            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            details == null -> LoadErrorState(state.error ?: LoadError.Unknown, viewModel::load, onSignIn)
                            else -> VideoBody(details, navigation, listState,
                                showTitle = !pinned,
                                offline = playerState.nowPlaying?.isOffline == true,
                                videoHeader = if (!pinned) ({ Spacer(Modifier.fillMaxWidth().height(videoHeight)) }) else null)
                        }
                    }
                }
                playerArea(Modifier.fillMaxWidth().height(videoHeight).offset { androidx.compose.ui.unit.IntOffset(0, videoOffset) })
                PortraitControls(viewModel.player, state, leftHanded, pinned, playerState.queue.isNotEmpty(),
                    viewModel::playNext, { fullscreenRequested = true }, {
                        listState.requestScrollToItem(0)
                        menuSettings.setPinned(!pinned)
                    }, { position -> settingsPosition = position; settingsOpen = true },
                    { actionGroup = it }, { group, position -> groupPositions[group] = position })
            }
        }
    }

    actionGroup?.let { group ->
        VideoActionsMenu(state, viewModel, group, navigation::openChannel, leftHanded, thumbArc, anchorRoot = if (fullscreen) null else groupPositions[group]) { actionGroup = null }
    }
    if (comments.opened) CommentsSheet(comments, viewModel, navigation::openChannel)
    if (picker.opened) PlaylistPickerSheet(picker, viewModel)
    if (settingsOpen) PlayerSettingsSheet(playerState, viewModel.controller, leftHanded, thumbArc, centerOnScreen = settingsPosition, onDismiss = { settingsOpen = false })
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

@Composable
private fun VideoBody(
    details: VideoDetails,
    navigation: FeedNavigation,
    listState: LazyListState,
    showTitle: Boolean,
    offline: Boolean = false,
    videoHeader: (@Composable () -> Unit)? = null,
) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 152.dp)) {
        videoHeader?.let { item(key = "video") { it() } }
        if (showTitle) item(key = "info") { TitleBlock(details, offline) }
        if (details.related.isNotEmpty()) {
            item(key = "related-title") {
                Text(stringResource(R.string.video_related), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
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
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
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
