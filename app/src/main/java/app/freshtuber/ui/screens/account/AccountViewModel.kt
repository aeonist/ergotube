package app.freshtuber.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.account.AccountState
import app.freshtuber.data.account.ChannelIdentity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accounts: AccountRepository,
) : ViewModel() {

    val state: StateFlow<AccountState> = accounts.state

    fun switchProfile(id: String) {
        viewModelScope.launch { accounts.switchProfile(id) }
    }

    fun switchChannel(channel: ChannelIdentity) {
        viewModelScope.launch { accounts.switchChannel(channel) }
    }

    fun signOut(id: String) {
        viewModelScope.launch { accounts.signOut(id) }
    }
}
