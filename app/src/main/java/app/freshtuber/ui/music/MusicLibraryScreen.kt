package app.freshtuber.ui.music

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.freshtuber.R
import app.freshtuber.data.downloads.DownloadStatus
import app.freshtuber.data.music.MusicSource
import app.freshtuber.data.music.MusicTrack
import app.freshtuber.data.settings.AppFeature
import app.freshtuber.ui.components.ChoiceRows
import app.freshtuber.ui.components.ContextAction
import app.freshtuber.ui.components.ContextBar
import app.freshtuber.ui.components.LocalLeftHanded
import app.freshtuber.ui.components.MessageState
import app.freshtuber.ui.components.ScreenHeader
import kotlinx.coroutines.launch

/**
 * «Любимое», the one library: every song in one list, each with a small mark of where it lives
 * (downloaded, on the phone, or only online). [collection] is `fav` (or null) or `playlist:<id>`;
 * the old `downloads` and `phone` shelves open the same list with the matching filter. Without a
 * network the songs that are not on the phone are hidden and a note says how many. The list
 * grows from the bottom like the others; its panel keeps play, shuffle, sort and «⋯».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(collection: String?, viewModel: MusicViewModel = hiltViewModel()) {
    MusicNotes(viewModel)
    MusicFolderAccess(viewModel)
    val left = LocalLeftHanded.current
    val navigation = LocalMusicNavigation.current
    val context = LocalContext.current
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preferences = app.freshtuber.ui.theme.LocalAppPreferences.current
    val fromBottom = !preferences.enabled(AppFeature.TraditionalScroll)
    val shareTitle = stringResource(R.string.music_share)
    var filter by rememberSaveable { mutableStateOf(when (collection) { "downloads" -> LibraryFilter.Downloaded; "phone" -> LibraryFilter.Phone; else -> LibraryFilter.All }) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.New) }
    var menu by remember { mutableStateOf<MusicTrack?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val playlistId = collection?.removePrefix("playlist:")?.takeIf { collection.startsWith("playlist:") }
    menu?.let { TrackMenu(it, viewModel, onDismiss = { menu = null }, playlistId = playlistId) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> -> if (uris.isNotEmpty()) viewModel.importFiles(uris) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? -> uri?.let(viewModel::importFolder) }
    // «Показывать треки из Music» needs audio access once; without it the switch stays off.
    val showFolder = preferences.enabled(AppFeature.MusicShowFolder)
    val askAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) viewModel.showFolder(true) }
    val toggleFolder = { if (showFolder) viewModel.showFolder(false) else if (viewModel.folderReadable) viewModel.showFolder(true) else askAudio.launch(app.freshtuber.data.music.MusicFolder.readPermission) }
    LaunchedEffect(showFolder) { if (showFolder) viewModel.rescanFolder() }

    // Downloaded songs that are not in the library rows still belong in the list.
    val downloadedIds = remember(downloaded) { downloaded.values.filter { it.state == DownloadStatus.Complete }.map { it.videoId }.toSet() }
    val all = remember(tracks, downloaded) {
        val known = tracks.associateBy { it.id }
        val extra = downloaded.values.filter { it.state == DownloadStatus.Complete }.map { known["yt:${it.videoId}"] ?: viewModel.downloadedTrack(it) }
        (tracks + extra).distinctBy { it.id }
    }
    val playlistTracks by (playlistId?.let { viewModel.playlistTracks(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsStateWithLifecycle(emptyList())
    var playlistName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(playlistId, playlists) { playlistName = playlistId?.let { viewModel.playlistName(it) } }

    val base: List<MusicTrack> = if (playlistId != null) playlistTracks else MusicLibraryLogic.filter(all, filter, downloadedIds)
    val visible = remember(base, online, downloadedIds) { MusicLibraryLogic.visible(base, online, downloadedIds) }
    val shown = remember(visible, sort, playlistId) { if (playlistId != null) visible.tracks else MusicLibraryLogic.sort(visible.tracks, sort) }
    val title = if (playlistId != null) playlistName.orEmpty() else stringResource(R.string.music_favorites)
    val play: (Int) -> Unit = { index ->
        viewModel.music.playCollection(shown, index, title)
        navigation?.openPlayer()
    }
    val bytes = downloaded.values.filter { it.state == DownloadStatus.Complete }.sumOf { it.bytesTotal }
    val subtitle = if (playlistId == null) stringResource(R.string.music_library_summary, songsCount(all.size),
        android.text.format.Formatter.formatShortFileSize(context, bytes)) else songsAndMinutes(shown.size, shown.sumOf { it.durationMs })

    val list = rememberLazyListState()
    // The four big buttons step aside while the list is read and come back on the way back.
    val hide = app.freshtuber.ui.components.rememberScrollHide(inverted = fromBottom)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().nestedScroll(hide.connection), state = list, reverseLayout = fromBottom, contentPadding = PaddingValues(vertical = 8.dp)) {
                // In a bottom-up list the first item sits lowest, by the thumb.
                if (!online && visible.hidden > 0) item(key = "offline") {
                    Text(stringResource(R.string.music_offline_hidden, visible.hidden), Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(shown.size, key = { "t" + shown[it].id }) { index ->
                    val track = shown[index]
                    TrackRow(track, originOf(track, downloaded), current = track.id == state.queue.track?.id,
                        onClick = { play(index) }, onMore = { menu = track },
                        onFlick = { viewModel.flick(it, track, context, shareTitle) })
                }
                if (shown.isEmpty()) item(key = "empty") { Box(Modifier.height(220.dp)) { MessageState(stringResource(R.string.music_library_empty)) } }
                item(key = "header") { ScreenHeader(title, subtitle) }
            }
            if (shown.size > 30) FastScroll(list, shown.size, fromBottom, left, Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd))
        }
        // Controls by the thumb, right above the dock.
        val actions = buildList {
            add(ContextAction(Icons.Rounded.PlayArrow, stringResource(R.string.music_play_all), primary = true, enabled = shown.isNotEmpty()) { play(0) })
            add(ContextAction(Icons.Rounded.Shuffle, stringResource(R.string.music_shuffle), enabled = shown.isNotEmpty()) {
                viewModel.music.playCollection(shown, shown.indices.random(), title, shuffle = true)
                navigation?.openPlayer()
            })
            if (playlistId == null) add(ContextAction(Icons.Rounded.Sort, stringResource(R.string.music_sort)) { sortOpen = true })
            add(ContextAction(Icons.Rounded.MoreHoriz, stringResource(R.string.music_more)) { moreOpen = true })
        }
        app.freshtuber.ui.motion.BlurVisibility(hide.visible, collapse = true) { ContextBar(actions, left) }
    }
    if (sortOpen) app.freshtuber.ui.components.BlurSheet(onDismissRequest = { sortOpen = false }) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text(stringResource(R.string.music_sort), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            ChoiceRows(LibrarySort.entries.map { stringResource(it.labelRes) }, sort.ordinal) { sort = LibrarySort.entries[it]; sortOpen = false }
        }
    }
    if (moreOpen) app.freshtuber.ui.components.BlurSheet(onDismissRequest = { moreOpen = false }) {
        Column(Modifier.padding(bottom = 16.dp)) {
            if (playlistId != null) {
                MenuRow(Icons.Rounded.Edit, stringResource(R.string.music_rename)) { moreOpen = false; renaming = true }
                MenuRow(Icons.Rounded.DeleteOutline, stringResource(R.string.music_delete_playlist)) { moreOpen = false; confirmDelete = true }
            } else {
                Text(stringResource(R.string.music_show), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                ChoiceRows(LibraryFilter.entries.map { stringResource(it.labelRes) }, filter.ordinal) { filter = LibraryFilter.entries[it]; moreOpen = false }
                HorizontalDivider()
                MenuRow(Icons.Rounded.AudioFile, stringResource(R.string.music_add_files)) { moreOpen = false; pickFiles.launch(arrayOf("audio/*", "application/ogg")) }
                MenuRow(Icons.Rounded.Folder, stringResource(R.string.music_add_folder)) { moreOpen = false; pickFolder.launch(null) }
                MenuRow(if (showFolder) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, stringResource(R.string.music_show_folder)) { toggleFolder() }
                MenuRow(Icons.Rounded.CleaningServices, stringResource(R.string.music_remove_duplicates)) { moreOpen = false; viewModel.removeDuplicates() }
            }
        }
    }
    if (confirmDelete && playlistId != null) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.music_delete_playlist)) },
        text = { Text(playlistName.orEmpty()) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deletePlaylist(playlistId); navigation?.openMusicHome() }) { Text(stringResource(R.string.music_delete_playlist)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } })
    if (renaming && playlistId != null) {
        var name by remember { mutableStateOf(playlistName.orEmpty()) }
        AlertDialog(onDismissRequest = { renaming = false }, title = { Text(stringResource(R.string.music_rename)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.music_playlist_name)) }) },
            confirmButton = { TextButton(onClick = { viewModel.renamePlaylist(playlistId, name); renaming = false }, enabled = name.isNotBlank()) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel)) } })
    }
}

private val LibraryFilter.labelRes get() = when (this) {
    LibraryFilter.All -> R.string.music_filter_all
    LibraryFilter.Downloaded -> R.string.music_downloads
    LibraryFilter.Phone -> R.string.music_phone
}

private val LibrarySort.labelRes get() = when (this) {
    LibrarySort.New -> R.string.music_sort_new
    LibrarySort.Title -> R.string.music_sort_title
    LibrarySort.Artist -> R.string.music_sort_artist
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun songsCount(count: Int) = androidx.compose.ui.res.pluralStringResource(R.plurals.music_songs, count, count)

/**
 * A fast-scroll thumb right at the screen edge. The strip around the thumb is kept out of the
 * system Back gesture, so a drag that starts on it scrolls the list.
 */
@Composable
private fun FastScroll(list: LazyListState, count: Int, reversed: Boolean, left: Boolean, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var height by remember { mutableIntStateOf(1) }
    val first = list.firstVisibleItemIndex
    val fraction = (first.toFloat() / count.coerceAtLeast(1)).coerceIn(0f, 1f)
    val shown = if (reversed) 1f - fraction else fraction
    var dragging by remember { mutableStateOf(false) }
    val width by androidx.compose.animation.core.animateDpAsState(if (dragging) 8.dp else 5.dp, label = "thumb")
    Box(modifier.fillMaxHeight(0.7f).width(32.dp)
        .onSizeChanged { height = it.height }
        .pointerInput(count, reversed) {
            detectVerticalDragGestures(onDragStart = { dragging = true }, onDragEnd = { dragging = false }, onDragCancel = { dragging = false }) { change, _ ->
                change.consume()
                val f = (change.position.y / size.height).coerceIn(0f, 1f)
                val index = ((if (reversed) 1f - f else f) * (count - 1)).toInt()
                scope.launch { list.scrollToItem(index) }
            }
        }) {
        Box(Modifier.align(if (left) Alignment.TopStart else Alignment.TopEnd)
            .offset { androidx.compose.ui.unit.IntOffset(0, ((height - 64.dp.toPx()) * shown).toInt()) }
            .size(32.dp, 64.dp).systemGestureExclusion(), contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd) {
            Box(Modifier.padding(horizontal = 2.dp).size(width, 48.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (dragging) 0.6f else 0.35f), RoundedCornerShape(4.dp)))
        }
    }
}
