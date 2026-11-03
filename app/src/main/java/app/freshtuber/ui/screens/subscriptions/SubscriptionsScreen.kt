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
) : ViewModel() {

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
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val signedIn by viewModel.feed.signedIn.collectAsStateWithLifecycle()
    if (signedIn == false) {
        SignInPrompt(stringResource(R.string.subscriptions_signed_out), onSignIn)
        return
    }
    val state by viewModel.feed.state.collectAsStateWithLifecycle()
    val channelsState by viewModel.channels.state.collectAsStateWithLifecycle()
    val channels = channelsState.items.filterIsInstance<FeedItem.Channel>().map { it.channel }

    FeedList(
        state = state,
        navigation = navigation,
        onRetry = viewModel.feed::refresh,
        onLoadMore = viewModel.feed::loadMore,
        emptyMessage = stringResource(R.string.feed_empty),
        onSignInAgain = onSignIn,
        header = {
            if (channels.isNotEmpty()) {
                item(key = "channels") { ChannelStrip(channels, navigation::openChannel) }
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
