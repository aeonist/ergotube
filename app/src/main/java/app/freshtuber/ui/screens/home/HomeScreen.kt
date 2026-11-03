package app.freshtuber.ui.screens.home

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.freshtuber.R
import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.extractor.ExtractorSearchSource.Kiosk
import app.freshtuber.data.extras.CommunityExtras
import app.freshtuber.data.feed.FeedRepository
import app.freshtuber.data.feed.FeedSource
import app.freshtuber.data.feed.withoutShorts
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.domain.model.FeedFilter
import app.freshtuber.domain.model.FeedPage
import app.freshtuber.ui.radial.PublishRadialFilters
import app.freshtuber.ui.radial.RadialItem
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import app.freshtuber.ui.components.FanMark
import app.freshtuber.ui.feed.AccountScopedFeed
import app.freshtuber.ui.feed.FeedList
import app.freshtuber.ui.feed.FeedNavigation
import app.freshtuber.ui.feed.FeedPager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    feeds: FeedRepository,
    accounts: AccountRepository,
    private val settings: AppSettings,
    extras: CommunityExtras,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val kioskFilters = Kiosk.entries.mapIndexed { index, kiosk ->
        FeedFilter(context.getString(kiosk.titleRes()), KIOSK_PREFIX + kiosk.name, selected = index == 0)
    }

    private suspend fun hideShorts(page: FeedPage) =
        if (settings.shortsInFeeds.first()) page else page.withoutShorts()

    val feed = AccountScopedFeed(viewModelScope, accounts) { signedIn ->
        if (signedIn != null) {
            val source = FeedSource.Browse(FeedRepository.HOME)
            FeedPager(
                scope = viewModelScope,
                first = { feeds.load(source) },
                more = { token -> feeds.loadMore(source, token) },
                transform = ::hideShorts,
                extras = extras,
                deArrowEnabled = { settings.deArrowEnabled.first() },
            )
        } else {
            // YouTube shows signed-out users an empty home, so trending kiosks stand in.
            FeedPager(
                scope = viewModelScope,
                first = { feeds.load(FeedSource.Kiosk(Kiosk.Music)).copy(filters = kioskFilters) },
                more = { token ->
                    val kiosk = Kiosk.valueOf(token.removePrefix(KIOSK_PREFIX))
                    feeds.load(FeedSource.Kiosk(kiosk))
                },
                transform = ::hideShorts,
                extras = extras,
                deArrowEnabled = { settings.deArrowEnabled.first() },
            )
        }
    }

    private companion object {
        const val KIOSK_PREFIX = "kiosk:"
    }
}

fun Kiosk.titleRes(): Int = when (this) {
    Kiosk.Music -> R.string.kiosk_music
    Kiosk.Gaming -> R.string.kiosk_gaming
    Kiosk.Podcasts -> R.string.kiosk_podcasts
    Kiosk.Movies -> R.string.kiosk_movies
    Kiosk.Live -> R.string.kiosk_live
}

@Composable
fun HomeScreen(
    navigation: FeedNavigation,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.feed.state.collectAsStateWithLifecycle()
    PublishRadialFilters(state.filters.map { filter ->
        RadialItem("filter:${filter.title}", filter.title, selected = filter.selected,
            shortTitle = if (filter.token == "kiosk:Live") stringResource(R.string.radial_live_short) else filter.title) { viewModel.feed.selectFilter(filter) }
    })
    FeedList(
        state = state,
        navigation = navigation,
        onRetry = viewModel.feed::refresh,
        onLoadMore = viewModel.feed::loadMore,
        emptyMessage = stringResource(R.string.feed_empty),
        onSignInAgain = onSignIn,
        header = {
            item(key = "header") {
                HomeHeader(state.filters, onAccount)
            }
        },
    )
}

/** Wordmark and quiet active-filter label; choices live on the thumb arc. */
@Composable
private fun HomeHeader(filters: List<FeedFilter>, onAccount: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FanMark(Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            filters.firstOrNull { it.selected }?.let {
                Text(it.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onAccount) {
            Icon(Icons.Rounded.AccountCircle, contentDescription = stringResource(R.string.nav_account))
        }
    }
}
