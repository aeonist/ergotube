package app.freshtuber.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import app.freshtuber.ui.components.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.freshtuber.R
import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.account.AccountState
import app.freshtuber.data.extras.CommunityExtras
import app.freshtuber.data.feed.ChannelHeader
import app.freshtuber.data.feed.FeedRepository
import app.freshtuber.data.feed.FeedSource
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.data.video.VideoRepository
import app.freshtuber.domain.model.FeedPage
import app.freshtuber.innertube.InnerTubeCommand
import app.freshtuber.ui.feed.FeedList
import app.freshtuber.ui.feed.FeedNavigation
import app.freshtuber.ui.feed.FeedPager
import app.freshtuber.ui.feed.LoadError
import app.freshtuber.ui.feed.messageRes
import app.freshtuber.ui.feed.toLoadError
import app.freshtuber.ui.navigation.ChannelRoute
import app.freshtuber.ui.navigation.LibraryListRoute
import app.freshtuber.ui.navigation.PlaylistRoute
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One library section, a playlist, or a channel: all are a YouTube list behind a top bar. */
@HiltViewModel
class ListViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val feeds: FeedRepository,
    private val videos: VideoRepository,
    private val accounts: AccountRepository,
    private val localSubscriptions: app.freshtuber.data.subscriptions.LocalSubscriptions,
    settings: AppSettings,
    extras: CommunityExtras,
) : ViewModel() {

    private val section: LibrarySection? = runCatching { savedState.toRoute<LibraryListRoute>() }.getOrNull()
        ?.let { LibrarySection.valueOf(it.section) }
    private val playlist: PlaylistRoute? = runCatching { savedState.toRoute<PlaylistRoute>() }.getOrNull()
    private val channelId: String? = runCatching { savedState.toRoute<ChannelRoute>() }.getOrNull()?.channelId

    private val _header = MutableStateFlow<ChannelHeader?>(null)
    val channelHeader: StateFlow<ChannelHeader?> = _header.asStateFlow()

    private val _actionError = MutableStateFlow<LoadError?>(null)
    val actionError: StateFlow<LoadError?> = _actionError.asStateFlow()

    val titleText: String? = playlist?.title

    private val source: FeedSource = when {
        section != null -> FeedSource.Browse(section.browseId)
        playlist != null -> FeedRepository.playlist(playlist.playlistId, playlist.seedVideoId)
        else -> FeedSource.Browse(checkNotNull(channelId), FeedRepository.CHANNEL_VIDEOS_TAB)
    }

    val pager = FeedPager(
        scope = viewModelScope,
        first = ::loadFirst,
        more = { token -> feeds.loadMore(source, token) },
        extras = extras,
        deArrowEnabled = { settings.deArrowEnabled.first() },
    ).also { it.refresh() }

    val sectionTitleRes: Int? = section?.titleRes
    val isWatchLater = section == LibrarySection.WatchLater
    val isPlaylists = section == LibrarySection.Playlists
    val isChannel = channelId != null
    val isPlaylist = playlist != null

    /** Account lists cannot load without an account; show a sign-in prompt instead of an error. */
    val needsAccount = kotlinx.coroutines.flow.combine(accounts.state, kotlinx.coroutines.flow.flowOf(section)) { state, list ->
        list != null && state is AccountState.SignedOut
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    private suspend fun loadFirst(): FeedPage {
        val id = channelId ?: return feeds.load(source)
        val (header, page) = feeds.channel(id)
        _header.value = if (header != null && accounts.state.value !is AccountState.SignedIn)
            header.copy(subscribed = localSubscriptions.channel(id).first() != null) else header
        return page
    }

    /** Subscribe or unsubscribe, optimistic; rolls back if YouTube refuses. */
    fun toggleSubscription() {
        val header = _header.value ?: return
        if (accounts.state.value !is AccountState.SignedIn) {
            val subscribe = header.subscribed != true
            _header.value = header.copy(subscribed = subscribe)
            viewModelScope.launch {
                try { localSubscriptions.toggle(header.channel); localSubscriptions.refresh() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { _header.value = header; _actionError.value = e.toLoadError() }
            }
            return
        }
        val subscribe = header.subscribed != true
        _header.value = header.copy(subscribed = subscribe)
        viewModelScope.launch {
            try {
                val id = header.channel.id
                videos.perform(if (subscribe) InnerTubeCommand.subscribe(id) else InnerTubeCommand.unsubscribe(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _header.value = header
                _actionError.value = e.toLoadError()
            }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }
}

@Composable
fun ListScreen(
    navigation: FeedNavigation,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onLiked: (() -> Unit)? = null,
    viewModel: ListViewModel = hiltViewModel(),
) {
    val state by viewModel.pager.state.collectAsStateWithLifecycle()
    val header by viewModel.channelHeader.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val needsAccount by viewModel.needsAccount.collectAsStateWithLifecycle()
    val left = LocalLeftHanded.current
    val title = viewModel.titleText
        ?: viewModel.sectionTitleRes?.let { stringResource(it) }
        ?: ""
    val firstVideo = state.items.firstNotNullOfOrNull { (it as? app.freshtuber.domain.model.FeedItem.Video)?.video?.id }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (needsAccount) Column(Modifier.fillMaxSize()) {
                ScreenHeader(title)
                MessageState(stringResource(R.string.action_needs_sign_in), Modifier.weight(1f))
            } else FeedList(
                state = state,
                navigation = navigation,
                onRetry = viewModel.pager::refresh,
                onLoadMore = viewModel.pager::loadMore,
                emptyMessage = stringResource(R.string.feed_empty),
                showChannel = header == null,
                onSignInAgain = onSignIn,
                header = {
                    val channel = header
                    if (channel != null) item(key = "channel-header") { ChannelHeaderRow(channel, actionError, onSignIn) }
                    else if (!viewModel.isChannel) item(key = "header") { ScreenHeader(title) }
                },
            )
        }
        val channel = header
        ContextBar(buildList {
            add(backAction(onBack))
            if (needsAccount) add(ContextAction(Icons.AutoMirrored.Rounded.Login, stringResource(R.string.welcome_sign_in), primary = true, onClick = onSignIn))
            else {
                if ((viewModel.isWatchLater || viewModel.isPlaylist) && firstVideo != null)
                    add(ContextAction(Icons.Rounded.PlayArrow, stringResource(R.string.list_play), primary = true) { navigation.openVideo(firstVideo) })
                if (viewModel.isPlaylists && onLiked != null)
                    add(ContextAction(Icons.Rounded.ThumbUp, stringResource(R.string.library_liked), onClick = onLiked))
                if (channel != null) add(ContextAction(
                    if (channel.subscribed == true) Icons.Rounded.HowToReg else Icons.Rounded.PersonAdd,
                    stringResource(if (channel.subscribed == true) R.string.channel_subscribed else R.string.channel_subscribe),
                    primary = channel.subscribed != true, onClick = viewModel::toggleSubscription))
            }
        }, left)
    }
}

@Composable
private fun ChannelHeaderRow(
    header: ChannelHeader,
    actionError: LoadError?,
    onSignIn: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().heightIn(min = Reach.HeaderHeight).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Bottom) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = header.channel.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                header.channel.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (actionError == LoadError.SessionExpired) {
            ActionHint(stringResource(R.string.action_needs_sign_in), onSignIn)
        } else if (actionError != null) {
            ActionHint(stringResource(actionError.messageRes()), null)
        }
    }
}

@Composable
fun ActionHint(text: String, onSignIn: (() -> Unit)?) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        onSignIn?.let {
            TextButton(onClick = it) { Text(stringResource(R.string.welcome_sign_in)) }
        }
    }
}
