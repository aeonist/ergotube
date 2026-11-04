package app.freshtuber.ui.screens.home

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import app.freshtuber.domain.model.FeedPage
import app.freshtuber.domain.model.FeedFilter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import app.freshtuber.ui.components.*
import app.freshtuber.ui.feed.AccountScopedFeed
import app.freshtuber.ui.feed.FeedList
import app.freshtuber.ui.feed.FeedNavigation
import app.freshtuber.ui.feed.FeedPager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
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

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(settings.shortsInFeeds, settings.deArrowEnabled) { shorts, branding -> shorts to branding }
                .drop(1).collect { feed.refresh() }
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
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.feed.state.collectAsStateWithLifecycle()
    val left = LocalLeftHanded.current
    val selected = state.filters.firstOrNull { it.selected }?.title
    Column(Modifier.fillMaxSize()) {
        FeedList(
            state = state,
            navigation = navigation,
            videoActions = true,
            onRetry = viewModel.feed::refresh,
            onLoadMore = viewModel.feed::loadMore,
            emptyMessage = stringResource(R.string.feed_empty),
            onSignInAgain = onSignIn,
            modifier = Modifier.weight(1f),
            header = {
                item(key = "header") {
                    ScreenHeader(stringResource(R.string.nav_home), selected ?: stringResource(R.string.home_subtitle))
                }
            },
        )
        // Filters live at the bottom, starting at the working-hand edge.
        if (state.filters.size > 1) BottomBar {
            HandChips(state.filters.map { filter -> ChipItem(filter.title, filter.selected) { viewModel.feed.selectFilter(filter) } }, left,
                Modifier.padding(vertical = 4.dp))
        }
    }
}
