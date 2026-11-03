package app.freshtuber.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.account.LoginCapture
import app.freshtuber.data.account.WebDataCleaner
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.innertube.InnerTubeException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

sealed interface LoginUiState {
    /** Wiping old WebView data so every login starts from a clean jar. */
    data object Preparing : LoginUiState

    /** [attempt] changes whenever the WebView must be rebuilt. */
    data class Browsing(val attempt: Int, val compatMode: Boolean) : LoginUiState

    data object Verifying : LoginUiState
    data object Done : LoginUiState
    data class Failed(val reason: LoginFailure) : LoginUiState
}

enum class LoginFailure { Network, Rejected, NotAccepted, Captcha, Unknown }

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val settings: AppSettings,
    private val webData: WebDataCleaner,
) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Preparing)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var attempt = 0
    private var compatMode = false

    init {
        start()
    }

    fun onCaptured(capture: LoginCapture) {
        if (_state.value !is LoginUiState.Browsing) return
        _state.value = LoginUiState.Verifying
        viewModelScope.launch {
            accounts.completeLogin(capture).fold(
                onSuccess = {
                    // The encrypted copy is now the only one; the WebView must not keep (or rotate) it.
                    webData.clearWebViewData()
                    settings.markWelcomeDone()
                    _state.value = LoginUiState.Done
                },
                onFailure = { _state.value = LoginUiState.Failed(it.toFailure()) },
            )
        }
    }

    /** Google refused the embedded browser: retry once with the Chrome identity, then give up. */
    fun onRejected() {
        if (_state.value !is LoginUiState.Browsing) return
        if (compatMode) {
            _state.value = LoginUiState.Failed(LoginFailure.Rejected)
        } else {
            compatMode = true
            start()
        }
    }

    fun onPageLoadFailed() {
        if (_state.value is LoginUiState.Browsing) _state.value = LoginUiState.Failed(LoginFailure.Network)
    }

    fun toggleCompatMode() {
        compatMode = !compatMode
        start()
    }

    val isCompatMode: Boolean get() = compatMode

    fun retry() = start()

    private fun start() {
        _state.value = LoginUiState.Preparing
        viewModelScope.launch {
            webData.clearWebViewData()
            attempt++
            _state.value = LoginUiState.Browsing(attempt, compatMode)
        }
    }

    private fun Throwable.toFailure(): LoginFailure = when (this) {
        is InnerTubeException.SessionExpired -> LoginFailure.NotAccepted
        is InnerTubeException.RateLimited -> LoginFailure.Captcha
        is IOException -> LoginFailure.Network
        else -> LoginFailure.Unknown
    }
}
