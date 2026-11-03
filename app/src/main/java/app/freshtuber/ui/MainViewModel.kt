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
) : ViewModel() {

    val online = network.online
    val expiredSession = combine(accounts.state, health.expiredProfiles) { account, ids -> (account as? AccountState.SignedIn)?.active?.id in ids }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val shortsEnabled = settings.shortsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val accountState = accounts.state
    val leftHanded = settings.leftHanded.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val thumbArc = settings.thumbArc.stateIn(viewModelScope, SharingStarted.Eagerly, app.freshtuber.ui.radial.ThumbArc())
    fun switchProfile(id: String) { viewModelScope.launch { accounts.switchProfile(id) } }
    fun switchChannel(channel: app.freshtuber.data.account.ChannelIdentity) { viewModelScope.launch { accounts.switchChannel(channel) } }

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
