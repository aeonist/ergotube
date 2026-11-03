package app.freshtuber.ui.screens.shorts

import androidx.compose.runtime.*
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
import app.freshtuber.ui.screens.settings.MenuSettingsViewModel
import app.freshtuber.ui.player.*
import app.freshtuber.ui.feed.FeedNavigation
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
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
            VideoViewModel(SavedStateHandle(mapOf("videoId" to id)), videos, accounts, extras, settings, controller) as T
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

@Composable
fun ShortsScreen(navigation: FeedNavigation, onSignIn: () -> Unit, viewModel: ShortsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by viewModel.controller.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { viewModel.leave() } }
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
            var doubleTapLike by remember { mutableStateOf<(() -> Unit)?>(null) }
            Box(Modifier.fillMaxSize()) {
                VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { state.items[it].videoId }) { page ->
                    val item = state.items[page]
                    val now = playerState.nowPlaying?.takeIf { it.videoId == item.videoId }
                    ShortPage(item = item,
                        player = if (now != null && page == pagerState.settledPage) viewModel.controller.player else null,
                        loading = playerState.loadingId == item.videoId,
                        onTap = viewModel.controller::togglePlayPause,
                        onDoubleTap = { if (page == pagerState.settledPage) doubleTapLike?.invoke() })
                }
                state.items.getOrNull(pagerState.settledPage)?.let { item ->
                    key(item.videoId) {
                        val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
                        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
                        val actions: VideoViewModel = viewModel(viewModelStoreOwner = owner, factory = viewModel.actionsFactory(item.videoId))
                        DisposableEffect(actions) { doubleTapLike = actions::like; onDispose { doubleTapLike = null } }
                        ShortActions(item.videoId, actions, navigation, onSignIn)
                    }
                }
                playerState.error?.let { error ->
                    LoadErrorState(error, viewModel.controller::retry, onSignIn)
                }
            }
        }
    }
}

@Composable
private fun ShortPage(
    item: ShortItem,
    player: Player?,
    loading: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(onTap, onDoubleTap) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { onDoubleTap() }) },
    ) {
        if (player != null) {
            VideoSurface(player, Modifier.fillMaxSize(), zoomToFill = true)
        } else {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val buffering = player?.let { rememberProgress(it).buffering } ?: false
        if (loading || buffering) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)

    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortActions(id: String, viewModel: VideoViewModel, navigation: FeedNavigation, onSignIn: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val menu: MenuSettingsViewModel = hiltViewModel()
    val left by menu.leftHanded.collectAsStateWithLifecycle()
    val arc by menu.arc.collectAsStateWithLifecycle()
    var more by remember { mutableStateOf(false) }
    var descriptionOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val messageText = message?.let { stringResource(it.textRes()) }
    val signIn = stringResource(R.string.welcome_sign_in)
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(messageText.orEmpty(), if (message == VideoMessage.NeedsSignIn) signIn else null)
        viewModel.consumeMessage()
        if (result == SnackbarResult.ActionPerformed) onSignIn()
    }
    val safe = WindowInsets.safeGestures.asPaddingValues()
    val leftEdge = safe.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
    val rightEdge = safe.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
    Box(Modifier.fillMaxSize()) {
        state.details?.let { details ->
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(start = leftEdge + if (left) VideoRailExclusion else 12.dp,
                    end = rightEdge + if (left) 12.dp else VideoRailExclusion, bottom = 12.dp)
                .background(Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .clickable { descriptionOpen = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                details.channel?.let { channel ->
                    Text(channel.name, color = Color.White, style = MaterialTheme.typography.labelLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(details.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        VideoActionRail(state, viewModel, { more = true },
            Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(start = if (left) 4.dp else 0.dp, end = if (left) 0.dp else 4.dp, bottom = 76.dp), overVideo = true, leftHanded = left)
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter))
    }
    if (descriptionOpen) ModalBottomSheet(onDismissRequest = { descriptionOpen = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.shorts_description), style = MaterialTheme.typography.titleLarge)
            state.details?.let { details ->
                Text(details.title, style = MaterialTheme.typography.titleMedium)
                details.channel?.let { channel -> TextButton(onClick = { descriptionOpen = false; navigation.openChannel(channel.id) }) { Text(channel.name) } }
                details.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
    if (more) VideoMoreMenu(id, viewModel, left, arc) { more = false }
    if (comments.opened) CommentsSheet(comments, viewModel, navigation::openChannel)
    if (picker.opened) PlaylistPickerSheet(picker, viewModel)
}
