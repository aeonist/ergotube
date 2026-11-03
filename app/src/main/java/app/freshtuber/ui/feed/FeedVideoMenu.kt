package app.freshtuber.ui.feed

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.freshtuber.R
import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.extras.CommunityExtras
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.data.video.VideoRepository
import app.freshtuber.domain.model.VideoSummary
import app.freshtuber.player.PlayerController
import app.freshtuber.ui.radial.*
import app.freshtuber.ui.screens.player.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Popup

@HiltViewModel
class FeedVideoActions @Inject constructor(private val videos: VideoRepository, private val accounts: AccountRepository,
    private val extras: CommunityExtras, private val settings: AppSettings, private val controller: PlayerController) : ViewModel() {
    val hiddenVideos = settings.hiddenVideos.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    fun hideVideo(id: String) { viewModelScope.launch { settings.hideVideo(id) } }
    fun factory(id: String) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VideoViewModel(SavedStateHandle(mapOf("videoId" to id)), videos, accounts, extras, settings, controller) as T
    }
}

@Composable
fun FeedVideoMenu(video: VideoSummary, anchor: Offset, onHidden: () -> Unit, onDismiss: () -> Unit) {
    val factory: FeedVideoActions = hiltViewModel()
    val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val actions: VideoViewModel = viewModel(viewModelStoreOwner = owner, factory = factory.factory(video.id))
    val picker by actions.picker.collectAsStateWithLifecycle()
    val message by actions.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(true) }
    val snackbar = remember { SnackbarHostState() }
    val text = message?.let { stringResource(if (it == VideoMessage.NeedsSignIn && !menuOpen) R.string.feed_save_sign_in else it.textRes()) }
    LaunchedEffect(message) {
        if (message != null) { snackbar.showSnackbar(text.orEmpty()); actions.consumeMessage(); if (!menuOpen) onDismiss() }
    }
    LaunchedEffect(picker.opened) { if (!menuOpen && !picker.opened && message == null) onDismiss() }
    val share = stringResource(R.string.video_share)
    if (menuOpen) TouchRadialMenu(listOf(
        RadialItem("not-interested", stringResource(R.string.feed_not_interested), Icons.Rounded.NotInterested,
            shortTitle = stringResource(R.string.feed_not_interested)) {
            actions.notInterested(video.notInterested) { onHidden(); onDismiss() }
        },
        RadialItem("save", stringResource(R.string.video_save), Icons.Rounded.BookmarkBorder, shortTitle = stringResource(R.string.video_save)) {
            actions.openPlaylistPicker(); menuOpen = false
        },
        RadialItem("share", share, Icons.Rounded.Share, shortTitle = share) {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${video.id}"), share)); onDismiss()
        },
        RadialItem("download", stringResource(R.string.feed_download), Icons.Rounded.Download, enabled = false) {},
    ), anchor, onDismiss)
    if (message != null) Popup(alignment = Alignment.TopCenter) { SnackbarHost(snackbar) }
    if (picker.opened) PlaylistPickerSheet(picker, actions)
}
