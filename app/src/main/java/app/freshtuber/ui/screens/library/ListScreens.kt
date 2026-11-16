package app.freshtuber.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.freshtuber.ui.components.rememberNotificationPermission
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
import app.freshtuber.innertube.parse.ChannelTab
import app.freshtuber.innertube.parse.ChannelTabKind
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
import kotlinx.coroutines.flow.map
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

    private val _tabs = MutableStateFlow<List<ChannelTab>>(emptyList())
    /** The channel's sections, in YouTube's order; the open one is selected. */
    val channelTabs: StateFlow<List<ChannelTab>> = _tabs.asStateFlow()
    private var channelParams: String = FeedRepository.CHANNEL_VIDEOS_TAB

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
    val isHistory = section == LibrarySection.History
    val isPlaylists = section == LibrarySection.Playlists
    val isChannel = channelId != null
    val isPlaylist = playlist != null

    /** Account lists cannot load without an account; show a sign-in prompt instead of an error. */
    val needsAccount = kotlinx.coroutines.flow.combine(accounts.state, kotlinx.coroutines.flow.flowOf(section)) { state, list ->
        list != null && state is AccountState.SignedOut
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    private suspend fun loadFirst(): FeedPage {
        val id = channelId ?: return feeds.load(source)
        val channel = feeds.channel(id, channelParams)
        // A tab switch keeps the header already shown, with any subscription change just made.
        if (_header.value == null) {
            val header = channel.header
            _header.value = if (header != null && accounts.state.value !is AccountState.SignedIn)
                header.copy(subscribed = localSubscriptions.channel(id).first() != null) else header
        }
        if (channel.tabs.isNotEmpty()) _tabs.value = channel.tabs
        return channel.page
    }

    /** Opens another section of the channel. */
    fun selectTab(tab: ChannelTab) {
        if (tab.selected || channelId == null) return
        channelParams = tab.params
        _tabs.value = _tabs.value.map { it.copy(selected = it.kind == tab.kind) }
        pager.reload()
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

    /** ErgoTube's own new-video notifications for this channel (the bell); they follow it locally too. */
    val channelNotifications: StateFlow<Boolean> = localSubscriptions.channel(channelId)
        .map { it?.notifications == true }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false)

    fun setNotifications(enabled: Boolean) {
        val header = _header.value ?: return
        viewModelScope.launch {
            try { localSubscriptions.notify(header.channel, enabled); if (enabled) localSubscriptions.refresh() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _actionError.value = e.toLoadError() }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }
}

@Composable
fun ListScreen(
    navigation: FeedNavigation,
    onSignIn: () -> Unit,
    onLiked: (() -> Unit)? = null,
    viewModel: ListViewModel = hiltViewModel(),
) {
    val state by viewModel.pager.state.collectAsStateWithLifecycle()
    val header by viewModel.channelHeader.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val tabs by viewModel.channelTabs.collectAsStateWithLifecycle()
    val needsAccount by viewModel.needsAccount.collectAsStateWithLifecycle()
    val bellOn by viewModel.channelNotifications.collectAsStateWithLifecycle()
    val enableBell = rememberNotificationPermission { viewModel.setNotifications(true) }
    val left = LocalLeftHanded.current
    val title = viewModel.titleText
        ?: viewModel.sectionTitleRes?.let { stringResource(it) }
        ?: ""
    val firstVideo = state.items.firstNotNullOfOrNull { (it as? app.freshtuber.domain.model.FeedItem.Video)?.video?.id }

    Column(Modifier.fillMaxSize()) {
        val preferences = app.freshtuber.ui.theme.LocalAppPreferences.current
        val fromBottom = !preferences.enabled(app.freshtuber.data.settings.AppFeature.TraditionalScroll)
        val hide = rememberScrollHide(inverted = fromBottom)
        val classic = LocalClassic.current
        val tabChips = tabs.map { tab -> ChipItem(tab.label(), tab.selected) { hide.visible = true; viewModel.selectTab(tab) } }
        // Classic: the channel's tabs right under the top bar, as on YouTube.
        if (classic && tabChips.size > 1) app.freshtuber.ui.motion.BlurVisibility(hide.visible, collapse = true) {
            app.freshtuber.ui.classic.ClassicChips(tabChips)
        }
        Box(Modifier.weight(1f)) {
            if (needsAccount) Column(Modifier.fillMaxSize()) {
                if (!fromBottom) ScreenHeader(title)
                MessageState(stringResource(R.string.library_needs_account), Modifier.weight(1f))
                if (fromBottom) ScreenHeader(title)
            } else FeedList(
                fromBottom = fromBottom,
                state = state,
                navigation = navigation,
                onRetry = viewModel.pager::refresh,
                onLoadMore = viewModel.pager::loadMore,
                emptyMessage = stringResource(R.string.feed_empty),
                showChannel = header == null,
                onSignInAgain = onSignIn,
                modifier = Modifier.nestedScroll(hide.connection),
                // History gathers its Shorts into shelves like YouTube, even a single one.
                shortsShelf = if (viewModel.isHistory) 1 else 2,
                header = {
                    val channel = header
                    if (channel != null) item(key = "channel-header") {
                        ChannelHeaderRow(channel, actionError, onSignIn, viewModel::toggleSubscription, bellOn, left) {
                            if (bellOn) viewModel.setNotifications(false) else enableBell()
                        }
                    }
                    else if (!viewModel.isChannel) item(key = "header") { ScreenHeader(title) }
                },
            )
        }
        val actions = buildList {
            if (needsAccount) add(ContextAction(Icons.AutoMirrored.Rounded.Login, stringResource(R.string.welcome_sign_in), primary = true, onClick = onSignIn))
            else {
                if ((viewModel.isWatchLater || viewModel.isPlaylist) && firstVideo != null)
                    add(ContextAction(Icons.Rounded.PlayArrow, stringResource(R.string.list_play), primary = true) { navigation.openVideo(firstVideo) })
                if (viewModel.isPlaylists && onLiked != null)
                    add(ContextAction(Icons.Rounded.ThumbUp, stringResource(R.string.library_liked), onClick = onLiked))
            }
        }
        if (actions.isNotEmpty()) {
            app.freshtuber.ui.motion.BlurVisibility(hide.visible, collapse = true) {
                ContextBar(actions, left)
            }
        } else if (!classic && tabChips.size > 1) BottomBar {
            // The channel's sections sit under the thumb, in YouTube's reading order from the hand edge.
            app.freshtuber.ui.motion.BlurVisibility(hide.visible, collapse = true) {
                HandChips(tabChips, left, Modifier.padding(vertical = 4.dp), natural = true)
            }
        }
    }
}

@Composable
private fun ChannelTab.label(): String = when (kind) {
    ChannelTabKind.Home -> stringResource(R.string.channel_tab_home)
    ChannelTabKind.Videos -> stringResource(R.string.channel_tab_videos)
    ChannelTabKind.Shorts -> stringResource(R.string.channel_tab_shorts)
    ChannelTabKind.Live -> stringResource(R.string.channel_tab_live)
    ChannelTabKind.Releases -> stringResource(R.string.channel_tab_releases)
    ChannelTabKind.Podcasts -> stringResource(R.string.channel_tab_podcasts)
    ChannelTabKind.Playlists -> stringResource(R.string.channel_tab_playlists)
    ChannelTabKind.Courses -> stringResource(R.string.channel_tab_courses)
}

@Composable
private fun ChannelHeaderRow(
    header: ChannelHeader,
    actionError: LoadError?,
    onSignIn: () -> Unit,
    onSubscribe: () -> Unit,
    bellOn: Boolean,
    left: Boolean,
    onBell: () -> Unit,
) {
    val facts = header.facts
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = header.channel.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    header.channel.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                facts?.handle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp))
                }
                val counts = listOfNotNull(facts?.subscribers, facts?.videos).joinToString(" · ")
                if (counts.isNotEmpty()) Text(counts, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp).testTag("channel:subscribers"))
            }
        }
        // Subscribe and the bell, side by side on the hand's side, as on YouTube.
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, if (left) Alignment.Start else Alignment.End)) {
            val bell = @Composable {
                val label = stringResource(if (bellOn) R.string.channel_bell_on else R.string.channel_bell_off)
                FilledTonalIconButton(onClick = onBell, modifier = Modifier.testTag("channel:bell").semantics { contentDescription = label },
                    colors = if (bellOn) IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface) else IconButtonDefaults.filledTonalIconButtonColors()) {
                    Icon(if (bellOn) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone, null)
                }
            }
            val subscribe = @Composable { ChannelSubscribeButton(header.subscribed == true, onSubscribe, Modifier) }
            (if (left) listOf(bell, subscribe) else listOf(subscribe, bell)).forEach { it() }
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
