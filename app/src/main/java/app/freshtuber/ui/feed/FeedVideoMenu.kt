package app.freshtuber.ui.feed

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.freshtuber.R
import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.account.AccountState
import app.freshtuber.data.extras.CommunityExtras
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.data.video.VideoRepository
import app.freshtuber.domain.model.VideoSummary
import app.freshtuber.innertube.InnerTubeCommand
import app.freshtuber.player.PlayerController
import app.freshtuber.ui.components.LocalAppMessages
import app.freshtuber.ui.components.LocalLeftHanded
import app.freshtuber.ui.components.TileGrid
import app.freshtuber.ui.components.TileItem
import app.freshtuber.ui.screens.player.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Quick actions on a feed card, shared by the hold-and-flick fan and the video menu. */
@HiltViewModel
class FeedVideoActions @Inject constructor(private val videos: VideoRepository, private val accounts: AccountRepository,
    private val extras: CommunityExtras, private val settings: AppSettings, private val controller: PlayerController, private val downloads: app.freshtuber.data.downloads.Downloads, private val localSubscriptions: app.freshtuber.data.subscriptions.LocalSubscriptions) : ViewModel() {
    val hiddenVideos = settings.hiddenVideos.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val signedIn get() = accounts.state.value is AccountState.SignedIn

    fun hideVideo(id: String) { viewModelScope.launch { settings.hideVideo(id) } }
    fun unhideVideo(id: String) { viewModelScope.launch { settings.unhideVideo(id) } }

    /** Tells YouTube only after the Undo window closed, so Undo is honest. Goes through ActionGuard. */
    fun confirmNotInterested(command: InnerTubeCommand?) {
        if (command == null || !signedIn) return
        viewModelScope.launch { runCatching { videos.perform(command) } }
    }

    fun watchLater(video: VideoSummary, done: (VideoMessage) -> Unit) {
        if (!signedIn) { done(VideoMessage.NeedsSignIn); return }
        viewModelScope.launch {
            try { videos.perform(InnerTubeCommand.addToPlaylist(WATCH_LATER, video.id)); done(VideoMessage.WatchLaterAdded) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { done(if (e.toLoadError() == LoadError.TooManyActions) VideoMessage.TooManyActions else VideoMessage.ActionFailed) }
        }
    }

    fun download(video: VideoSummary, done: (VideoMessage) -> Unit) {
        viewModelScope.launch {
            try { done(if (downloads.enqueue(video)) VideoMessage.DownloadQueued else VideoMessage.DownloadExists) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { done(VideoMessage.ActionFailed) }
        }
    }

    fun factory(id: String) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VideoViewModel(SavedStateHandle(mapOf("videoId" to id)), videos, accounts, extras, settings, controller, localSubscriptions, downloads) as T
    }

    private companion object {
        /** YouTube's own id for the Watch later playlist. */
        const val WATCH_LATER = "WL"
    }
}

fun shareVideo(context: Context, videoId: String, title: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/$videoId"), title))
}

/** Runs one card action and reports it in the app snackbar; Not interested gets Undo. */
@Composable
fun rememberCardActions(video: VideoSummary, actions: FeedVideoActions?, onHide: (Boolean) -> Unit): (FanAction) -> Unit {
    val messages = LocalAppMessages.current
    val resources = LocalResources.current
    val context = LocalContext.current
    val downloadNotifications = app.freshtuber.ui.components.rememberDownloadNotifications()
    val shareTitle = stringResource(R.string.video_share)
    val report: (VideoMessage) -> Unit = { messages?.show(resources.getString(it.textRes())) }
    return remember(video, actions, messages) {
        { action ->
            when (action) {
                FanAction.WatchLater -> actions?.watchLater(video) { message ->
                    if (message == VideoMessage.NeedsSignIn) messages?.show(resources.getString(R.string.feed_later_sign_in)) else report(message)
                }
                FanAction.Download -> { downloadNotifications(); actions?.download(video, report) }
                FanAction.Share -> shareVideo(context, video.id, shareTitle)
                FanAction.NotInterested -> {
                    onHide(true)
                    actions?.hideVideo(video.id)
                    messages?.show(resources.getString(R.string.feed_hidden), resources.getString(R.string.action_undo)) { undone ->
                        if (undone) { onHide(false); actions?.unhideVideo(video.id) } else actions?.confirmNotInterested(video.notInterested)
                    }
                }
            }
        }
    }
}

/** The normal video menu: a short sheet of tiles, the most used one under the thumb. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedVideoMenu(video: VideoSummary, recommendation: Boolean, act: (FanAction) -> Unit, onChannel: ((String) -> Unit)?, onDismiss: () -> Unit) {
    val factory: FeedVideoActions = hiltViewModel()
    val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val actions: VideoViewModel = viewModel(viewModelStoreOwner = owner, factory = factory.factory(video.id))
    val picker by actions.picker.collectAsStateWithLifecycle()
    val message by actions.message.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val resources = LocalResources.current
    var sheetOpen by remember { mutableStateOf(true) }
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        messages?.show(resources.getString(if (current == VideoMessage.NeedsSignIn) R.string.feed_save_sign_in else current.textRes()))
        actions.consumeMessage()
        if (!sheetOpen) onDismiss()
    }
    LaunchedEffect(picker.opened) { if (!sheetOpen && !picker.opened && message == null) onDismiss() }
    val left = LocalLeftHanded.current
    if (sheetOpen) ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(video.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))
        val labels = FanAction.entries.associateWith { stringResource(it.labelRes) }
        val saveLabel = stringResource(R.string.video_save)
        val channelLabel = stringResource(R.string.channel_group)
        val tiles = buildList {
            fun tile(action: FanAction) = TileItem(action.icon, labels.getValue(action)) { onDismiss(); act(action) }
            add(tile(FanAction.WatchLater))
            add(tile(FanAction.Download))
            add(tile(FanAction.Share))
            add(TileItem(Icons.AutoMirrored.Rounded.PlaylistAdd, saveLabel) { sheetOpen = false; actions.openPlaylistPicker() })
            val channelId = video.channelId
            if (channelId != null && onChannel != null) add(TileItem(Icons.Rounded.AccountCircle, channelLabel) { onDismiss(); onChannel(channelId) })
            if (recommendation) add(tile(FanAction.NotInterested))
        }
        TileGrid(tiles, 3, left, Modifier.padding(bottom = 16.dp))
    }
    if (picker.opened) PlaylistPickerSheet(picker, actions)
}
