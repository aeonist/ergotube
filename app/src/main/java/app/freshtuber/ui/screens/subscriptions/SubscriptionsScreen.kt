package app.freshtuber.ui.screens.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.freshtuber.ui.components.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.freshtuber.R
import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.extras.CommunityExtras
import app.freshtuber.data.feed.FeedRepository
import app.freshtuber.data.feed.FeedSource
import app.freshtuber.data.feed.withoutShorts
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.domain.model.ChannelSummary
import app.freshtuber.domain.model.FeedItem
import app.freshtuber.ui.feed.AccountScopedFeed
import app.freshtuber.ui.feed.FeedList
import app.freshtuber.ui.feed.FeedNavigation
import app.freshtuber.ui.feed.FeedPager
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    feeds: FeedRepository,
    accounts: AccountRepository,
    settings: AppSettings,
    extras: CommunityExtras,
    val local: app.freshtuber.data.subscriptions.LocalSubscriptions,
) : ViewModel() {

    val refreshing = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val failure = kotlinx.coroutines.flow.MutableStateFlow(false)
    val localChannels = local.channels.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val localState = kotlinx.coroutines.flow.combine(local.videos, refreshing, failure, settings.shortsInFeeds) { videos, loading, failed, shorts ->
        app.freshtuber.ui.feed.FeedUiState(items = videos.filter { shorts || !it.isShort }.map { FeedItem.Video(it.summary()) }, loading = loading && videos.isEmpty(),
            error = if (failed && videos.isEmpty()) app.freshtuber.ui.feed.LoadError.Network else null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, app.freshtuber.ui.feed.FeedUiState())
    fun refreshLocal() { if (refreshing.value) return; viewModelScope.launch {
        refreshing.value = true
        try { failure.value = local.refresh() > 0 } finally { refreshing.value = false }
    } }

    val feed = AccountScopedFeed(viewModelScope, accounts) { signedIn ->
        signedIn ?: return@AccountScopedFeed null
        val source = FeedSource.Browse(FeedRepository.SUBSCRIPTIONS)
        FeedPager(
            scope = viewModelScope,
            first = { feeds.load(source) },
            more = { token -> feeds.loadMore(source, token) },
            transform = { if (settings.shortsInFeeds.first()) it else it.withoutShorts() },
            extras = extras,
            deArrowEnabled = { settings.deArrowEnabled.first() },
        )
    }

    /** The followed channels, shown as a row above the feed. */
    val channels = AccountScopedFeed(viewModelScope, accounts) { signedIn ->
        signedIn ?: return@AccountScopedFeed null
        val source = FeedSource.Browse(FeedRepository.CHANNELS)
        FeedPager(viewModelScope, first = { feeds.load(source) }, more = { feeds.loadMore(source, it) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    navigation: FeedNavigation,
    onSignIn: () -> Unit,
    onSearch: () -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val signedIn by viewModel.feed.signedIn.collectAsStateWithLifecycle()
    val state by viewModel.feed.state.collectAsStateWithLifecycle()
    val channelsState by viewModel.channels.state.collectAsStateWithLifecycle()
    val channels = channelsState.items.filterIsInstance<FeedItem.Channel>().map { it.channel }
    val left = LocalLeftHanded.current

    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val localState by viewModel.localState.collectAsStateWithLifecycle()
    val localChannels by viewModel.localChannels.collectAsStateWithLifecycle()
    // With an account the feed is YouTube's; without one it is the channels followed on this device.
    val localMode = signedIn != true
    var selectedChannel by rememberSaveable(localMode) { mutableStateOf<String?>(null) }
    var listOpen by remember { mutableStateOf(false) }
    LaunchedEffect(localMode) { if (localMode) viewModel.refreshLocal() }
    val strip = if (localMode) localChannels.map { ChannelSummary(it.id, it.name, it.avatarUrl) } else channels
    val source = if (localMode) localState else state
    val shown = selectedChannel?.let { id -> source.copy(items = source.items.filter { (it as? FeedItem.Video)?.video?.channelId == id }) } ?: source
    val selectedName = strip.firstOrNull { it.id == selectedChannel }?.name

    // The strip slides away while reading down and comes back on the way up.
    val hide = rememberScrollHide()

    Column(Modifier.fillMaxSize()) {
        FeedList(
            state = shown,
            navigation = navigation,
            onRetry = { if (localMode) viewModel.refreshLocal() else { viewModel.feed.refresh() } },
            onLoadMore = { if (!localMode) { viewModel.feed.loadMore() } },
            emptyMessage = stringResource(if (localMode) R.string.local_subscriptions_empty else R.string.feed_empty),
            onSignInAgain = onSignIn,
            videoActions = true,
            modifier = Modifier.weight(1f).nestedScroll(hide.connection),
            header = {
                item(key = "header") {
                    ScreenHeader(selectedName ?: stringResource(R.string.nav_subscriptions),
                        when {
                            selectedName != null -> stringResource(R.string.subscriptions_open_hint)
                            localMode && localChannels.any { it.syncFailed } -> stringResource(R.string.local_sync_failed)
                            else -> null
                        })
                }
            },
        )
        val controlsShown = localMode
        if (controlsShown || strip.isNotEmpty()) BottomBar {
            if (controlsShown) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                val controls: List<@Composable () -> Unit> = buildList {
                    add {
                        TextButton(onClick = viewModel::refreshLocal, enabled = !refreshing, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(if (refreshing) R.string.action_refreshing else R.string.action_refresh))
                        }
                    }
                    if (localMode && localChannels.isEmpty()) add {
                        TextButton(onClick = onSearch, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.local_find_channels)) }
                    }
                }
                if (!left) Spacer(Modifier.weight(1f))
                handOrder(controls, left).forEach { it() }
            }
            if (strip.isNotEmpty()) androidx.compose.animation.AnimatedVisibility(hide.visible) {
                ChannelStrip(strip, selectedChannel, left,
                    onPick = { id -> if (id != null && id == selectedChannel) navigation.openChannel(id) else selectedChannel = id },
                    onList = { listOpen = true })
            }
        }
    }
    if (listOpen) ModalBottomSheet(onDismissRequest = { listOpen = false }) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth()) {
            items(strip, key = { it.id }) { channel ->
                ListItem(headlineContent = { Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { AsyncImage(channel.avatarUrl, null, Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest), contentScale = ContentScale.Crop) },
                    trailingContent = if (channel.id == selectedChannel) ({ Icon(Icons.Rounded.Check, null) }) else null,
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clickable { selectedChannel = channel.id; listOpen = false })
            }
        }
    }
}

/** «Все», then the channels, then the full list; starts at the working-hand edge. */
@Composable
private fun ChannelStrip(channels: List<ChannelSummary>, selected: String?, left: Boolean, onPick: (String?) -> Unit, onList: () -> Unit) {
    LazyRow(
        reverseLayout = !left,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "all") { StripButton(stringResource(R.string.subscriptions_all), null, Icons.Rounded.Apps, selected == null) { onPick(null) } }
        items(channels, key = { it.id }) { channel ->
            StripButton(channel.name, channel.avatarUrl, null, channel.id == selected) { onPick(channel.id) }
        }
        item(key = "list") { StripButton(stringResource(R.string.subscriptions_list), null, Icons.AutoMirrored.Rounded.List, false, onList) }
    }
}

@Composable
private fun StripButton(name: String, avatar: String?, icon: androidx.compose.ui.graphics.vector.ImageVector?, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(64.dp)
            .clip(MaterialTheme.shapes.medium)
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val ring = if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape).padding(4.dp) else Modifier.padding(4.dp)
        Box(ring) {
            if (icon != null) Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Icon(icon, null)
            } else AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}
