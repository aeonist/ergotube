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
import app.freshtuber.ui.screens.SignInPrompt
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

    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val localState by viewModel.localState.collectAsStateWithLifecycle()
    val localChannels by viewModel.localChannels.collectAsStateWithLifecycle()
    var localMode by rememberSaveable(signedIn) { mutableStateOf(signedIn != true) }
    LaunchedEffect(localMode) { if (localMode) viewModel.refreshLocal() }
    FeedList(
        state = if (localMode) localState else state,
        navigation = navigation,
        onRetry = { if (localMode) viewModel.refreshLocal() else { viewModel.feed.refresh() } },
        onLoadMore = { if (!localMode) { viewModel.feed.loadMore() } },
        emptyMessage = stringResource(if (localMode) R.string.local_subscriptions_empty else R.string.feed_empty),
        onSignInAgain = onSignIn,
        header = {
            item(key = "local-controls") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.nav_subscriptions), style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (signedIn == true) {
                            FilterChip(selected = !localMode, onClick = { localMode = false }, label = { Text(stringResource(R.string.source_youtube)) })
                            FilterChip(selected = localMode, onClick = { localMode = true }, label = { Text(stringResource(R.string.local_on_device)) })
                        } else Text(stringResource(R.string.local_on_device), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        if (localMode) TextButton(onClick = viewModel::refreshLocal, enabled = !refreshing) { Text(stringResource(if (refreshing) R.string.action_refreshing else R.string.action_refresh)) }
                    }
                    if (localMode && localChannels.any { it.syncFailed }) Text(stringResource(R.string.local_sync_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (localMode && localChannels.isEmpty()) item(key = "find-channels") {
                TextButton(onClick = onSearch, modifier = Modifier.padding(horizontal = 12.dp)) { Text(stringResource(R.string.local_find_channels)) }
            }
            val strip = if (localMode) localChannels.map { ChannelSummary(it.id, it.name, it.avatarUrl) } else channels
            if (strip.isNotEmpty()) {
                item(key = "channels") { ChannelStrip(strip, navigation::openChannel) }
            }
        },
    )
}

@Composable
private fun ChannelStrip(channels: List<ChannelSummary>, onOpen: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(channels, key = { it.id }) { channel ->
            Column(
                Modifier
                    .width(72.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onOpen(channel.id) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = channel.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Text(
                    channel.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}
