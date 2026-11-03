package app.freshtuber.ui.screens.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.freshtuber.R
import app.freshtuber.data.downloads.*
import app.freshtuber.player.PlayerController
import app.freshtuber.ui.feed.LocalFeedBottomClearance
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(val repository: Downloads, private val controller: PlayerController) : ViewModel() {
    val items = repository.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun pause(id: String) { viewModelScope.launch { repository.pause(id) } }
    fun resume(id: String) { viewModelScope.launch { repository.resume(id) } }
    fun delete(id: String) {
        if (controller.state.value.nowPlaying?.videoId == id) controller.stop()
        repository.delete(id)
    }
}

@Composable
fun DownloadsScreen(onBack: () -> Unit, onVideo: (String) -> Unit, onSearch: () -> Unit, viewModel: DownloadsViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Download?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
            Text(stringResource(R.string.library_downloads), style = MaterialTheme.typography.headlineSmall)
        }
        if (items.isEmpty()) Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.Download, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.download_empty), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.download_empty_hint), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onSearch) { Text(stringResource(R.string.nav_search)) }
        } else LazyColumn(contentPadding = PaddingValues(bottom = LocalFeedBottomClearance.current)) {
            item { Text(stringResource(R.string.download_storage_hint), Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(items, key = { it.videoId }) { item ->
                Column(Modifier.fillMaxWidth().clickable(enabled = item.state == DownloadStatus.Complete) { onVideo(item.videoId) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(viewModel.repository.thumbnail(item), null, Modifier.width(112.dp).aspectRatio(16f / 9).clip(MaterialTheme.shapes.small), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        Column(Modifier.weight(1f)) {
                            Text(item.title.ifBlank { stringResource(R.string.library_downloads) }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            item.channelName?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text(downloadStatus(item), style = MaterialTheme.typography.bodySmall, color = if (item.state == DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (item.state == DownloadStatus.Downloading) {
                        Spacer(Modifier.height(10.dp))
                        if (item.bytesTotal > 0) LinearProgressIndicator(progress = { (item.bytesDone.toFloat() / item.bytesTotal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (item.bytesTotal > 0) "${size(item.bytesDone)} / ${size(item.bytesTotal)}" else size(item.bytesDone), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                        when (item.state) {
                            DownloadStatus.Queued, DownloadStatus.Downloading -> TextButton(onClick = { viewModel.pause(item.videoId) }) { Text(stringResource(R.string.download_pause)) }
                            DownloadStatus.Paused, DownloadStatus.Failed -> TextButton(onClick = { viewModel.resume(item.videoId) }) { Text(stringResource(if (item.state == DownloadStatus.Paused) R.string.download_resume else R.string.action_retry)) }
                            DownloadStatus.Complete -> TextButton(onClick = { onVideo(item.videoId) }) { Text(stringResource(R.string.download_play)) }
                            DownloadStatus.Deleting -> Unit
                        }
                        IconButton(onClick = { deleting = item }, enabled = item.state != DownloadStatus.Deleting) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.download_delete)) }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
    deleting?.let { item -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.download_delete)) },
        text = { Text(stringResource(R.string.download_delete_confirm, item.title)) },
        confirmButton = { TextButton(onClick = { viewModel.delete(item.videoId); deleting = null }) { Text(stringResource(R.string.download_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } }) }
}

@Composable
private fun downloadStatus(item: Download): String {
    val text = stringResource(when (item.state) {
        DownloadStatus.Queued -> R.string.download_waiting
        DownloadStatus.Downloading -> R.string.download_downloading
        DownloadStatus.Paused -> R.string.download_paused
        DownloadStatus.Complete -> R.string.download_complete
        DownloadStatus.Deleting -> R.string.download_deleting
        DownloadStatus.Failed -> when (item.error) { "background" -> R.string.download_error_background; "space" -> R.string.download_error_space; "storage" -> R.string.download_error_storage; "unsupported" -> R.string.download_error_unsupported; else -> R.string.download_error_network }
    })
    return if (item.height > 0) "$text · ${item.height}p" else text
}

@Composable
private fun size(bytes: Long): String = android.text.format.Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, bytes)
