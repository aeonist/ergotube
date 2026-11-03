package app.freshtuber.ui.feed

import app.freshtuber.data.account.TooManyActionsException
import app.freshtuber.data.extras.Branding
import app.freshtuber.data.extras.CommunityExtras
import app.freshtuber.domain.model.FeedFilter
import app.freshtuber.domain.model.FeedItem
import app.freshtuber.domain.model.FeedPage
import app.freshtuber.innertube.InnerTubeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

enum class LoadError { Network, Captcha, SessionExpired, TooManyActions, Unknown }

fun Throwable.toLoadError(): LoadError = when (this) {
    is ReCaptchaException, is InnerTubeException.RateLimited -> LoadError.Captcha
    is InnerTubeException.SessionExpired -> LoadError.SessionExpired
    is TooManyActionsException -> LoadError.TooManyActions
    // YouTube answered, so the connection is fine; it refused or changed something.
    is InnerTubeException.Http, is InnerTubeException.BadResponse -> LoadError.Unknown
    is IOException -> LoadError.Network
    else -> LoadError.Unknown
}

data class FeedUiState(
    val items: List<FeedItem> = emptyList(),
    val filters: List<FeedFilter> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    /** Error of the first load; the list is empty then. */
    val error: LoadError? = null,
    /** Error while loading more; the list stays. */
    val moreFailed: Boolean = false,
    val endReached: Boolean = false,
    /** DeArrow replacements by video id. */
    val branding: Map<String, Branding> = emptyMap(),
)

/**
 * Paging for one YouTube list. Owned by a ViewModel, which passes its scope.
 *
 * [first] loads the default view; [more] loads a continuation token, which is also how a
 * filter chip's view is loaded.
 */
class FeedPager(
    private val scope: CoroutineScope,
    private val first: suspend () -> FeedPage,
    private val more: suspend (String) -> FeedPage,
    private val transform: suspend (FeedPage) -> FeedPage = { it },
    private val extras: CommunityExtras? = null,
    private val deArrowEnabled: suspend () -> Boolean = { false },
) {
    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var continuation: String? = null
    private var job: Job? = null
    private val brandingLimit = Semaphore(4)

    fun refresh() = start(filterToken = null, keepFilters = false)

    fun selectFilter(filter: FeedFilter) {
        if (filter.selected) return
        val filters = _state.value.filters.map { it.copy(selected = it.title == filter.title) }
        _state.update { it.copy(filters = filters) }
        start(filter.token, keepFilters = true)
    }

    fun loadMore() {
        val state = _state.value
        val token = continuation ?: return
        if (state.loading || state.loadingMore || state.endReached) return
        _state.update { it.copy(loadingMore = true, moreFailed = false) }
        job = scope.launch {
            try {
                val page = transform(more(token))
                continuation = page.continuation
                _state.update {
                    it.copy(
                        items = (it.items + page.items).distinctBy(FeedItem::key),
                        loadingMore = false,
                        endReached = page.continuation == null,
                    )
                }
                fetchBranding(page.items)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loadingMore = false, moreFailed = true) }
            }
        }
    }

    private fun start(filterToken: String?, keepFilters: Boolean) {
        job?.cancel()
        val keepItems = filterToken == null && !keepFilters
        _state.update {
            it.copy(loading = !keepItems || it.items.isEmpty(), error = null, items = if (keepItems) it.items else emptyList(), moreFailed = false, endReached = false)
        }
        job = scope.launch {
            try {
                val page = transform(if (filterToken != null) more(filterToken) else first())
                continuation = page.continuation
                _state.update {
                    it.copy(
                        items = page.items,
                        filters = if (keepFilters || page.filters.isEmpty()) it.filters.ifEmpty { page.filters } else page.filters,
                        loading = false,
                        endReached = page.continuation == null,
                    )
                }
                fetchBranding(page.items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toLoadError()) }
            }
        }
    }

    /** Replace titles and thumbnails with DeArrow's as they arrive. */
    private fun fetchBranding(items: List<FeedItem>) {
        val community = extras ?: return
        scope.launch {
            if (!deArrowEnabled()) return@launch
            items.filterIsInstance<FeedItem.Video>().filterNot { it.video.isShort }.forEach { item ->
                launch {
                    val branding = brandingLimit.withPermit { community.branding(item.video.id) } ?: return@launch
                    _state.update { it.copy(branding = it.branding + (item.video.id to branding)) }
                }
            }
        }
    }
}
