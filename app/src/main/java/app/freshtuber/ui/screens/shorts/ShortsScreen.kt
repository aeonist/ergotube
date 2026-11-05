package app.freshtuber.ui.screens.shorts

import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.freshtuber.R
import app.freshtuber.data.video.VideoRepository
import app.freshtuber.data.extras.CommunityExtras
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.ui.screens.player.*
import app.freshtuber.ui.player.*
import app.freshtuber.ui.feed.FeedNavigation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.media3.common.Player
import app.freshtuber.player.PlayerController
import app.freshtuber.ui.player.VideoSurface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.account.AccountState
import app.freshtuber.data.shorts.ShortItem
import app.freshtuber.data.shorts.ShortsRepository
import app.freshtuber.ui.feed.LoadError
import app.freshtuber.ui.feed.LoadErrorState
import app.freshtuber.ui.feed.toLoadError
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShortsUiState(
    val items: List<ShortItem> = emptyList(),
    val loading: Boolean = true,
    val error: LoadError? = null,
    val next: String? = null,
    val loadingMore: Boolean = false,
)

@HiltViewModel
class ShortsViewModel @Inject constructor(
    private val shorts: ShortsRepository,
    private val accounts: AccountRepository,
    private val videos: VideoRepository,
    private val extras: CommunityExtras,
    private val settings: AppSettings,
    val controller: PlayerController,
    private val downloads: app.freshtuber.data.downloads.Downloads, private val localSubscriptions: app.freshtuber.data.subscriptions.LocalSubscriptions,
) : ViewModel() {
    private val _state = MutableStateFlow(ShortsUiState())
    val state: StateFlow<ShortsUiState> = _state.asStateFlow()

    init {
        // Start over when the account changes: signed in, the sequence follows the account.
        viewModelScope.launch {
            accounts.state
                .filterNot { it is AccountState.Loading }
                .map { (it as? AccountState.SignedIn)?.active?.id }
                .distinctUntilChanged()
                .collect { refresh() }
        }
    }

    fun refresh() {
        _state.value = ShortsUiState()
        viewModelScope.launch {
            try {
                val page = shorts.first()
                _state.value = ShortsUiState(items = page.items, loading = false, next = page.next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ShortsUiState(loading = false, error = e.toLoadError())
            }
        }
    }

    fun playAt(index: Int) {
        val item = _state.value.items.getOrNull(index) ?: return
        controller.play(item.videoId, isShort = true)
        controller.prefetchShort(_state.value.items.getOrNull(index + 1)?.videoId)
        controller.warmShort(_state.value.items.getOrNull(index + 2)?.videoId)
    }

    fun actionsFactory(id: String) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VideoViewModel(SavedStateHandle(mapOf("videoId" to id)), videos, accounts, extras, settings, controller, localSubscriptions, downloads) as T
    }

    /** Shorts stop when the tab is left; regular videos keep playing in the mini player. */
    fun leave() {
        if (controller.state.value.nowPlaying?.isShort == true || controller.state.value.loadingId != null) controller.stop()
    }

    fun loadMore() {
        val current = _state.value
        val next = current.next ?: return
        if (current.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = shorts.more(next)
                _state.update {
                    it.copy(items = (it.items + page.items).distinctBy(ShortItem::videoId), next = page.next, loadingMore = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }
}

/** Pictures wider than this (width / height) are shown whole, with the light above and below. */
private const val FitAspect = 0.65f

@Composable
fun ShortsScreen(navigation: FeedNavigation, onSignIn: () -> Unit, viewModel: ShortsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by viewModel.controller.state.collectAsStateWithLifecycle()
    val inPip = LocalInPictureInPicture.current
    val light = app.freshtuber.ui.liquid.LocalLiquidLight.current
    DisposableEffect(Unit) {
        // Leaving the app from a Short also turns it into a floating window.
        viewModel.controller.setVideoScreen(true)
        onDispose { viewModel.controller.setVideoScreen(false); viewModel.leave() }
    }
    // The Short lights the page like the player does, always in night tones.
    DisposableEffect(light) {
        light?.enterPlayer()
        light?.setImmersive(true)
        onDispose { light?.setPlayerCovered(false); light?.setImmersive(false); light?.exitPlayer() }
    }
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.error != null -> LoadErrorState(state.error!!, viewModel::refresh, null)
        else -> {
            val pagerState = rememberPagerState { state.items.size }
            LaunchedEffect(pagerState.settledPage, state.items.size) {
                viewModel.playAt(pagerState.settledPage)
                if (pagerState.settledPage >= state.items.size - 5) viewModel.loadMore()
            }
            val settledItem = state.items.getOrNull(pagerState.settledPage)
            LaunchedEffect(light, settledItem?.videoId) { light?.setVideoThumbnail(settledItem?.thumbnailUrl) }
            // Next / previous from the floating window's buttons.
            val count by rememberUpdatedState(state.items.size)
            LaunchedEffect(pagerState) {
                viewModel.controller.shortSteps.collect { step ->
                    val target = (pagerState.settledPage + step).coerceIn(0, (count - 1).coerceAtLeast(0))
                    if (target != pagerState.settledPage) pagerState.scrollToPage(target)
                }
            }
            val dockSpace = app.freshtuber.ui.components.Reach.CompactDockHeight +
                with(androidx.compose.ui.platform.LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
            var doubleTapLike by remember { mutableStateOf<(() -> Unit)?>(null) }
            Box(Modifier.fillMaxSize()) {
                VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { state.items[it].videoId }) { page ->
                    val item = state.items[page]
                    val now = playerState.nowPlaying?.takeIf { it.videoId == item.videoId }
                    ShortPage(item = item,
                        player = if (now != null && page == pagerState.settledPage) viewModel.controller.player else null,
                        loading = playerState.loadingId == item.videoId,
                        active = page == pagerState.settledPage,
                        inPip = inPip,
                        bottomSpace = if (inPip) 0.dp else dockSpace,
                        onTap = viewModel.controller::togglePlayPause,
                        onDoubleTap = { if (page == pagerState.settledPage) doubleTapLike?.invoke() })
                }
                if (!inPip) state.items.getOrNull(pagerState.settledPage)?.let { item ->
                    key(item.videoId) {
                        val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
                        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
                        val actions: VideoViewModel = viewModel(viewModelStoreOwner = owner, factory = viewModel.actionsFactory(item.videoId))
                        DisposableEffect(actions) { doubleTapLike = actions::like; onDispose { doubleTapLike = null } }
                        ShortActions(item.videoId, actions, navigation, onSignIn, dockSpace)
                    }
                }
                playerState.error?.let { error ->
                    LoadErrorState(error, viewModel.controller::retry, onSignIn)
                }
            }
        }
    }
}

/**
 * One Short. A tall picture fills the screen (cropped a little at the sides, as on YouTube);
 * a square or wide one is shown whole and the background light fills the space around it.
 */
@Composable
private fun ShortPage(
    item: ShortItem,
    player: Player?,
    loading: Boolean,
    active: Boolean,
    inPip: Boolean,
    bottomSpace: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val progress = player?.let { rememberProgress(it) }
    val tapLabel = stringResource(if (progress?.isPlaying == true) R.string.player_pause else R.string.player_play)
    val light = app.freshtuber.ui.liquid.LocalLiquidLight.current
    val aspect = player?.let { rememberVideoAspect(it, item.videoId) }
    val fit = !inPip && aspect != null && aspect > FitAspect
    // A full-screen picture hides the light anyway, so it is not drawn at all.
    if (active) SideEffect { light?.setPlayerCovered(!fit) }
    val grab: ((android.graphics.Bitmap) -> Unit)? = if (active) light?.let { l -> { bitmap -> l.pushVideoFrame(bitmap) } } else null
    Box(
        Modifier
            .fillMaxSize()
            .background(if (fit) Color.Transparent else Color.Black)
            .semantics { contentDescription = tapLabel; role = Role.Button; onClick(label = tapLabel) { onTap(); true } }
            .pointerInput(onTap, onDoubleTap) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { onDoubleTap() }) },
    ) {
        if (player != null) {
            // One surface in both shapes, so switching never recreates it.
            Box((if (fit && aspect != null) Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(aspect) else Modifier.fillMaxSize())
                .onGloballyPositioned { if (active && fit) light?.setVideoRect(it.boundsInRoot()) }) {
                VideoSurface(player, Modifier.fillMaxSize(), zoomToFill = !fit, onFrame = grab)
            }
        } else {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!inPip && player != null && progress != null && progress.durationMs > 0)
            ShortsTimeline(player, progress, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomSpace))
        if (loading || progress?.buffering == true) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        else if (!inPip && player != null && progress != null && !progress.isPlaying && !player.playWhenReady && progress.durationMs > 0) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(72.dp).background(Color(0xAA000000), CircleShape).padding(16.dp))
        }

    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortActions(id: String, viewModel: VideoViewModel, navigation: FeedNavigation, onSignIn: () -> Unit, bottomSpace: androidx.compose.ui.unit.Dp) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val left = app.freshtuber.ui.components.LocalLeftHanded.current
    val messages = app.freshtuber.ui.components.LocalAppMessages.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var more by remember { mutableStateOf(false) }
    var descriptionOpen by remember { mutableStateOf(false) }
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        messages?.show(resources.getString(current.textRes()),
            if (current == VideoMessage.NeedsSignIn) resources.getString(R.string.welcome_sign_in) else null) { if (it) onSignIn() }
        viewModel.consumeMessage()
    }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val details = state.details
    val liked = state.likeStatus == app.freshtuber.domain.model.LikeStatus.Like
    val disliked = state.likeStatus == app.freshtuber.domain.model.LikeStatus.Dislike
    val likeCount = (if (liked) details?.likeCountTextLiked ?: details?.likeCountText else details?.likeCountText)?.takeIf { it.any(Char::isDigit) }
    val side = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    // Buttons take the Short's own color: a deep tint of the hue the light took from the video.
    val hue = app.freshtuber.ui.liquid.LocalLiquidLight.current?.hue ?: app.freshtuber.ui.liquid.LiquidLight.DefaultHue
    val glass = remember(hue) { app.freshtuber.ui.theme.Oklab.lch(0.30f, 0.07f, hue, 0.62f) }
    Box(Modifier.fillMaxSize().windowInsetsPadding(side)) {
        // Caption card: channel with Subscribe, then the title; tap opens the description.
        details?.let { d ->
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(start = if (left) 80.dp else 12.dp, end = if (left) 12.dp else 80.dp, bottom = bottomSpace + 14.dp)
                .background(glass, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .clickable { descriptionOpen = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                d.channel?.let { channel ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(channel.avatarUrl, stringResource(R.string.channel_group), contentScale = ContentScale.Crop,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0x33FFFFFF))
                                .clickable { navigation.openChannel(channel.id) })
                        Spacer(Modifier.width(8.dp))
                        Text(channel.name, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).clickable { navigation.openChannel(channel.id) })
                        Spacer(Modifier.width(8.dp))
                        val subscribed = state.subscribed == true
                        Surface(onClick = viewModel::toggleSubscription, shape = CircleShape, modifier = Modifier.heightIn(min = 48.dp),
                            color = Color.Transparent) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(stringResource(if (subscribed) R.string.channel_subscribed else R.string.channel_subscribe),
                                    color = if (subscribed) Color.White else Color(0xFF0F0F0F), style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.background(if (subscribed) Color(0x38FFFFFF) else Color.White, CircleShape)
                                        .padding(horizontal = 14.dp, vertical = 9.dp))
                            }
                        }
                    }
                }
                Text(d.title, color = Color(0xFFDDDDDD), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Action column on the working-hand side; the most used sits lowest.
        Column(Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd).padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = bottomSpace + 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShortButton(Icons.Rounded.ChatBubbleOutline, stringResource(R.string.video_comments), null, false, details != null, glass, viewModel::openComments)
            ShortButton(if (disliked) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt, stringResource(R.string.video_dislike),
                state.dislikes?.let { app.freshtuber.ui.components.compactNumber(it, locale) }, disliked, details != null, glass, viewModel::toggleDislike)
            ShortButton(if (liked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt, stringResource(R.string.video_like), likeCount, liked, details != null, glass, viewModel::toggleLike)
            ShortButton(Icons.Rounded.MoreHoriz, stringResource(R.string.video_more), null, false, true, glass) { more = true }
        }
    }
    if (descriptionOpen) ModalBottomSheet(onDismissRequest = { descriptionOpen = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.shorts_description), style = MaterialTheme.typography.titleLarge)
            state.details?.let { d ->
                Text(d.title, style = MaterialTheme.typography.titleMedium)
                d.channel?.let { channel -> TextButton(onClick = { descriptionOpen = false; navigation.openChannel(channel.id) }) { Text(channel.name) } }
                d.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
    if (more) ModalBottomSheet(onDismissRequest = { more = false }) {
        val share = stringResource(R.string.video_share)
        val downloadNotifications = app.freshtuber.ui.components.rememberDownloadNotifications()
        val tiles = buildList {
            add(app.freshtuber.ui.components.TileItem(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.video_save)) { more = false; viewModel.openPlaylistPicker() })
            add(app.freshtuber.ui.components.TileItem(Icons.Rounded.Download, stringResource(R.string.feed_download)) { more = false; downloadNotifications(); viewModel.download() })
            add(app.freshtuber.ui.components.TileItem(Icons.Rounded.Share, share) { more = false; app.freshtuber.ui.feed.shareVideo(context, id, share) })
            details?.channel?.let { channel -> add(app.freshtuber.ui.components.TileItem(Icons.Rounded.AccountCircle, stringResource(R.string.channel_group)) {
                more = false; navigation.openChannel(channel.id) }) }
        }
        app.freshtuber.ui.components.TileGrid(tiles, 4, left, Modifier.padding(bottom = 16.dp))
    }
    if (comments.opened) CommentsSheet(comments, viewModel, navigation::openChannel)
    if (picker.opened) PlaylistPickerSheet(picker, viewModel)
}

@Composable
private fun ShortButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: String?, selected: Boolean, enabled: Boolean,
    glass: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = if (selected) Color.White else glass,
        contentColor = if (selected) Color(0xFF0F0F0F) else Color.White,
        modifier = Modifier.size(56.dp).semantics { contentDescription = listOfNotNull(label, count).joinToString(", ") }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(if (count != null) 20.dp else 24.dp))
            count?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)) }
        }
    }
}

/**
 * The Short's timeline along the bottom edge: thin while watching, thick with the time while the
 * finger drags or taps it. Its own touches never reach the pager.
 */
@Composable
private fun ShortsTimeline(player: Player, progress: Progress, modifier: Modifier = Modifier) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    val duration = progress.durationMs
    val fraction = scrub ?: (progress.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val label = stringResource(R.string.shorts_timeline)
    Box(modifier.fillMaxWidth().height(26.dp)
        .semantics { contentDescription = label }
        .pointerInput(player, duration) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                scrub = (down.position.x / size.width).coerceIn(0f, 1f)
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    scrub = (change.position.x / size.width).coerceIn(0f, 1f)
                }
                scrub?.let { player.seekTo((it * duration).toLong()) }
                scrub = null
            }
        }) {
        val thick = scrub != null
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(if (thick) 6.dp else 3.dp).background(Color(0x55FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(app.freshtuber.ui.theme.BrandColors.Red))
        }
        if (thick) Text("${app.freshtuber.ui.util.formatDuration((fraction * duration / 1000).toLong())} / ${app.freshtuber.ui.util.formatDuration(duration / 1000)}",
            color = Color.White, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                .background(Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
    }
}
