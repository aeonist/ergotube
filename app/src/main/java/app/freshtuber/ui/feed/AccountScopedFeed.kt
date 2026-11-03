package app.freshtuber.ui.feed

import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.account.AccountState
import app.freshtuber.domain.model.FeedFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A feed that starts over whenever the account, or the channel it acts as, changes.
 * [factory] returns null when the screen has no feed for that state (e.g. signed out).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountScopedFeed(
    scope: CoroutineScope,
    accounts: AccountRepository,
    private val factory: (AccountState.SignedIn?) -> FeedPager?,
) {
    private val pager = MutableStateFlow<FeedPager?>(null)

    private val _signedIn = MutableStateFlow<Boolean?>(null)
    /** Null until the account state is known. */
    val signedIn: StateFlow<Boolean?> = _signedIn.asStateFlow()

    val state: StateFlow<FeedUiState> = pager
        .flatMapLatest { it?.state ?: flowOf(FeedUiState(loading = false)) }
        .stateIn(scope, SharingStarted.Eagerly, FeedUiState())

    init {
        scope.launch {
            accounts.state
                .filterNot { it is AccountState.Loading }
                .map { it as? AccountState.SignedIn }
                .distinctUntilChanged { old, new ->
                    old?.active?.id == new?.active?.id && old?.active?.activePageId == new?.active?.activePageId
                }
                .collect { signedIn ->
                    _signedIn.value = signedIn != null
                    pager.value = factory(signedIn)?.also { it.refresh() }
                }
        }
    }

    fun refresh() = pager.value?.refresh()
    fun loadMore() = pager.value?.loadMore()
    fun selectFilter(filter: FeedFilter) = pager.value?.selectFilter(filter)
}
