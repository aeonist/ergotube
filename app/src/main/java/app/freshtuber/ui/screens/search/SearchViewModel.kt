package app.freshtuber.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.freshtuber.data.extras.CommunityExtras
import app.freshtuber.data.feed.FeedRepository
import app.freshtuber.data.feed.FeedSource
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.ui.feed.FeedPager
import app.freshtuber.ui.feed.FeedUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Search as the account when signed in (personalised), through NewPipeExtractor otherwise. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val feeds: FeedRepository,
    private val settings: AppSettings,
    private val extras: CommunityExtras,
) : ViewModel() {

    private val pager = MutableStateFlow<FeedPager?>(null)

    val state: StateFlow<FeedUiState> = pager
        .flatMapLatest { it?.state ?: flowOf(FeedUiState(loading = false)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeedUiState(loading = false))

    val hasQuery: StateFlow<Boolean> = pager.map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val source = FeedSource.Search(trimmed)
        pager.value = FeedPager(
            scope = viewModelScope,
            first = { feeds.load(source) },
            more = { token -> feeds.loadMore(source, token) },
            extras = extras,
            deArrowEnabled = { settings.deArrowEnabled.first() },
        ).also { it.refresh() }
    }

    fun retry() = pager.value?.refresh()
    fun loadMore() = pager.value?.loadMore()
}
