package app.freshtuber.ui.screens.login

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.freshtuber.R
import app.freshtuber.data.account.LoginCapture
import app.freshtuber.ui.components.MessageState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onClose: () -> Unit,
    onDone: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var progress by remember { mutableIntStateOf(0) }
    var webCanGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state == LoginUiState.Done) onDone()
    }
    BackHandler(enabled = webCanGoBack && state is LoginUiState.Browsing) {
        webView?.goBack()
    }

    val events = remember(viewModel) {
        object : LoginWebViewEvents {
            override fun onCaptured(capture: LoginCapture) = viewModel.onCaptured(capture)
            override fun onRejected() = viewModel.onRejected()
            override fun onLoadFailed() = viewModel.onPageLoadFailed()
            override fun onProgress(percent: Int) {
                progress = percent
            }
            override fun onCanGoBack(canGoBack: Boolean) {
                webCanGoBack = canGoBack
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.login_close))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.login_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (viewModel.isCompatMode) R.string.login_standard_mode
                                            else R.string.login_compat_mode,
                                        ),
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    viewModel.toggleCompatMode()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val current = state) {
                is LoginUiState.Browsing -> key(current.attempt) {
                    LoginWebView(
                        compatMode = current.compatMode,
                        events = events,
                        onWebView = { webView = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (progress in 1..99) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                LoginUiState.Preparing -> BusyState(stringResource(R.string.login_preparing))
                LoginUiState.Verifying, LoginUiState.Done -> BusyState(stringResource(R.string.login_verifying))
                is LoginUiState.Failed -> MessageState(
                    message = stringResource(current.reason.messageRes()),
                    action = {
                        FilledTonalButton(onClick = viewModel::retry) {
                            Text(stringResource(R.string.action_retry))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun BusyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun LoginFailure.messageRes(): Int = when (this) {
    LoginFailure.Network -> R.string.login_error_network
    LoginFailure.Rejected -> R.string.login_error_rejected
    LoginFailure.NotAccepted -> R.string.login_error_not_accepted
    LoginFailure.Captcha -> R.string.login_error_captcha
    LoginFailure.Unknown -> R.string.login_error_unknown
}
