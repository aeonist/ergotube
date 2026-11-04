package app.freshtuber.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.account.AccountState
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.player.PlayerController
import app.freshtuber.ui.navigation.HomeRoute
import app.freshtuber.ui.navigation.WelcomeRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: AppSettings,
    private val accounts: AccountRepository,
    val player: PlayerController,
    network: app.freshtuber.core.connectivity.NetworkMonitor,
    health: app.freshtuber.data.account.SessionHealth,
    downloads: app.freshtuber.data.downloads.Downloads,
) : ViewModel() {

    val online = network.online
    val expiredSession = combine(accounts.state, health.expiredProfiles) { account, ids -> (account as? AccountState.SignedIn)?.active?.id in ids }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val shortsEnabled = settings.shortsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val accountState = accounts.state
    val leftHanded = settings.leftHanded.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    /** Finished downloads, shown next to «Скачанное». */
    val completedDownloads = downloads.items.map { list -> list.count { it.state == app.freshtuber.data.downloads.DownloadStatus.Complete } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Where the app opens: null while settings and sessions load. */
    val startRoute: StateFlow<Any?> =
        combine(settings.welcomeDone, accounts.state) { welcomeDone, account ->
            when (account) {
                AccountState.Loading -> null
                is AccountState.SignedIn -> HomeRoute
                AccountState.SignedOut -> if (welcomeDone) HomeRoute else WelcomeRoute
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun continueWithoutAccount() {
        viewModelScope.launch { settings.markWelcomeDone() }
    }
}
