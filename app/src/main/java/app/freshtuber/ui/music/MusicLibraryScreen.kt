package app.freshtuber.ui.music

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import app.freshtuber.ui.components.ChipItem
import app.freshtuber.ui.components.ContextAction
import app.freshtuber.ui.components.ContextBar
import app.freshtuber.ui.components.HandChips
import app.freshtuber.ui.components.LocalLeftHanded
import app.freshtuber.ui.components.MessageState
import app.freshtuber.ui.components.ScreenHeader
import app.freshtuber.ui.components.BottomBar
import kotlinx.coroutines.launch

private enum class LibraryFilter(val labelRes: Int) { All(R.string.music_filter_all), Downloaded(R.string.music_downloads), Phone(R.string.music_phone) }
private enum class LibrarySort(val labelRes: Int) { New(R.string.music_sort_new), Title(R.string.music_sort_title), Artist(R.string.music_sort_artist) }

/**
 * The one library: phone files and YouTube songs in the same list, each with a mark of where
 * it lives. Filters and sort sit by the thumb; the list grows from the bottom like the others.
 * [collection] opens one shelf instead: `fav`, `downloads`, `phone` or `playlist:<id>`.
 */
@Composable
fun MusicLibraryScreen(collection: String?, viewModel: MusicViewModel = hiltViewModel()) {
    MusicNotes(viewModel)
    val left = LocalLeftHanded.current
    val navigation = LocalMusicNavigation.current
    val context = LocalContext.current
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preferences = app.freshtuber.ui.theme.LocalAppPreferences.current
    val fromBottom = !preferences.enabled(AppFeature.TraditionalScroll)
    val shareTitle = stringResource(R.string.music_share)
    var picked by rememberSaveable { mutableStateOf<LibraryFilter?>(null) }
    // Without a network the library shows what can play.
    val filter = picked ?: if (online) LibraryFilter.All else LibraryFilter.Downloaded
    var sort by rememberSaveable { mutableStateOf(LibrarySort.New) }
    var menu by remember { mutableStateOf<MusicTrack?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    val playlistId = collection?.removePrefix("playlist:")?.takeIf { collection.startsWith("playlist:") }
    menu?.let { TrackMenu(it, viewModel, onDismiss = { menu = null }, playlistId = playlistId) }
    if (newPlaylist) PlaylistPicker(null, viewModel) { newPlaylist = false }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> -> if (uris.isNotEmpty()) viewModel.importFiles(uris) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? -> uri?.let(viewModel::importFolder) }

    // Downloaded songs that are not in the library rows still belong on the shelf.
    val downloadedTracks = remember(tracks, downloaded) {
        val known = tracks.associateBy { it.id }
        downloaded.values.filter { it.state == DownloadStatus.Complete }.map { known["yt:${it.videoId}"] ?: viewModel.downloadedTrack(it) }
    }
    val all = remember(tracks, downloadedTracks) { (tracks + downloadedTracks).distinctBy { it.id } }
    val playlistTracks by (playlistId?.let { viewModel.playlistTracks(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsStateWithLifecycle(emptyList())
    var playlistName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(playlistId, playlists) { playlistName = playlistId?.let { viewModel.playlistName(it) } }

    val base: List<MusicTrack> = when {
        collection == "fav" -> all.filter { it.id in favorites }
        collection == "downloads" -> downloadedTracks
        collection == "phone" -> all.filter { it.source == MusicSource.Phone }
        playlistId != null -> playlistTracks
        else -> when (filter) {
            LibraryFilter.All -> all
            LibraryFilter.Downloaded -> downloadedTracks
            LibraryFilter.Phone -> all.filter { it.source == MusicSource.Phone }
        }
    }
    val shown = remember(base, sort, playlistId) {
        if (playlistId != null) base else when (sort) {
            LibrarySort.New -> base.sortedByDescending { it.addedAt }
            LibrarySort.Title -> base.sortedBy { it.title.lowercase() }
            LibrarySort.Artist -> base.sortedBy { (it.artist ?: "").lowercase() }
        }
    }
    val shownTitle = collectionTitle(collection, playlistName)
    val playable: (MusicTrack) -> Boolean = { online || it.source == MusicSource.Phone || downloaded[it.videoId]?.state == DownloadStatus.Complete }
    val play: (Int) -> Unit = { index ->
        val list = shown.filter(playable)
        val target = shown[index]
        val start = list.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
        viewModel.music.playCollection(list, start, shownTitle)
        navigation?.openPlayer()
    }

    val title = when {
        collection == "fav" -> stringResource(R.string.music_favorites)
        collection == "downloads" -> stringResource(R.string.music_downloads)
        collection == "phone" -> stringResource(R.string.music_phone)
        playlistId != null -> playlistName.orEmpty()
        else -> stringResource(R.string.music_library)
    }
    val bytes = downloaded.values.filter { it.state == DownloadStatus.Complete }.sumOf { it.bytesTotal }
    val subtitle = if (collection == null) stringResource(R.string.music_library_summary, songsCount(all.size),
        android.text.format.Formatter.formatShortFileSize(context, bytes)) else songsAndMinutes(shown.size, shown.sumOf { it.durationMs })

    val list = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), state = list, reverseLayout = fromBottom, contentPadding = PaddingValues(vertical = 8.dp)) {
                // In a bottom-up list the first item sits lowest, by the thumb.
                if (collection == null) item(key = "shelf") {
                    Shelf(all.count { it.id in favorites }, downloadedTracks.size, all.count { it.source == MusicSource.Phone }, playlists, left,
                        onOpen = { navigation?.openCollection(it) }, onNewPlaylist = { newPlaylist = true })
                }
                if (!online) item(key = "offline") {
                    Text(stringResource(R.string.music_offline_library), Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (collection == null && shown.isNotEmpty()) item(key = "label") {
                    Text(stringResource(if (sort == LibrarySort.New) R.string.music_recently_added else sort.labelRes), Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(shown.size, key = { "t" + shown[it].id }) { index ->
                    val track = shown[index]
                    TrackRow(track, originOf(track, downloaded), current = track.id == state.queue.track?.id, dimmed = !playable(track),
                        onClick = { if (playable(track)) play(index) }, onMore = { menu = track },
                        onFlick = { viewModel.flick(it, track, context, shareTitle) })
                }
                if (shown.isEmpty()) item(key = "empty") { Box(Modifier.height(220.dp)) { MessageState(stringResource(R.string.music_library_empty)) } }
                item(key = "header") { ScreenHeader(title, subtitle) }
            }
            if (shown.size > 30) FastScroll(list, shown.size, fromBottom, left, Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd))
        }
        // Controls by the thumb, right above the dock.
        if (collection == null) BottomBar {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val chips = LibraryFilter.entries.map { ChipItem(stringResource(it.labelRes), filter == it) { picked = it } } +
                    ChipItem("⇅ " + stringResource(sort.labelRes), false) { sort = LibrarySort.entries[(sort.ordinal + 1) % LibrarySort.entries.size] }
                HandChips(chips, left, Modifier.weight(1f), natural = true)
                Box {
                    IconButton(onClick = { addMenu = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.music_add_files)) }
                    DropdownMenu(addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.music_add_files)) }, leadingIcon = { Icon(Icons.Rounded.AudioFile, null) },
                            onClick = { addMenu = false; pickFiles.launch(arrayOf("audio/*", "application/ogg")) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.music_add_folder)) }, leadingIcon = { Icon(Icons.Rounded.Folder, null) },
                            onClick = { addMenu = false; pickFolder.launch(null) })
                    }
                }
            }
        } else {
            val actions = buildList {
                add(ContextAction(Icons.Rounded.PlayArrow, stringResource(R.string.music_play_all), primary = true, enabled = shown.any(playable)) { if (shown.isNotEmpty()) play(0) })
                add(ContextAction(Icons.Rounded.Shuffle, stringResource(R.string.music_shuffle), enabled = shown.any(playable)) {
                    viewModel.music.playCollection(shown.filter(playable), (0 until shown.count(playable)).random(), shownTitle, shuffle = true)
                    navigation?.openPlayer()
                })
                if (collection == "phone") {
                    add(ContextAction(Icons.Rounded.AudioFile, stringResource(R.string.music_add_files)) { pickFiles.launch(arrayOf("audio/*", "application/ogg")) })
                    add(ContextAction(Icons.Rounded.Folder, stringResource(R.string.music_add_folder)) { pickFolder.launch(null) })
                }
                if (playlistId != null) {
                    add(ContextAction(Icons.Rounded.Edit, stringResource(R.string.music_rename)) { renaming = true })
                    add(ContextAction(Icons.Rounded.DeleteOutline, stringResource(R.string.music_delete_playlist)) { viewModel.deletePlaylist(playlistId); navigation?.openTab(app.freshtuber.ui.navigation.MusicTab.Library) })
                }
            }
            ContextBar(actions, left)
        }
    }
    if (renaming && playlistId != null) {
        var name by remember { mutableStateOf(playlistName.orEmpty()) }
        AlertDialog(onDismissRequest = { renaming = false }, title = { Text(stringResource(R.string.music_rename)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.music_playlist_name)) }) },
            confirmButton = { TextButton(onClick = { viewModel.renamePlaylist(playlistId, name); renaming = false }, enabled = name.isNotBlank()) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel)) } })
    }
}

@Composable
private fun collectionTitle(collection: String?, playlistName: String?): String? = when {
    collection == "fav" -> stringResource(R.string.music_favorites)
    collection == "downloads" -> stringResource(R.string.music_downloads)
    collection == "phone" -> stringResource(R.string.music_phone)
    collection?.startsWith("playlist:") == true -> playlistName
    else -> stringResource(R.string.music_library)
}

@Composable
private fun songsCount(count: Int) = androidx.compose.ui.res.pluralStringResource(R.plurals.music_songs, count, count)

/** «Любимое», «Скачанное», «С телефона», «+ Плейлист», then the playlists. */
@Composable
private fun Shelf(favorites: Int, downloads: Int, phone: Int, playlists: List<app.freshtuber.data.music.PlaylistCount>, left: Boolean,
    onOpen: (String) -> Unit, onNewPlaylist: () -> Unit) {
    data class Tile(val icon: ImageVector, val label: String, val count: String, val onClick: () -> Unit)
    val tiles = listOf(
        Tile(Icons.Rounded.Favorite, stringResource(R.string.music_favorites), favorites.toString()) { onOpen("fav") },
        Tile(Icons.Rounded.DownloadDone, stringResource(R.string.music_downloads), downloads.toString()) { onOpen("downloads") },
        Tile(Icons.Rounded.PhoneAndroid, stringResource(R.string.music_phone), phone.toString()) { onOpen("phone") },
        Tile(Icons.Rounded.Add, stringResource(R.string.music_playlist_add), " ") { onNewPlaylist() },
    ) + playlists.map { p -> Tile(Icons.AutoMirrored.Rounded.QueueMusic, p.name, p.count.toString()) { onOpen("playlist:${p.id}") } }
    LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), reverseLayout = !left) {
        items(tiles, key = { it.label + it.count }) { tile ->
            Surface(onClick = tile.onClick, modifier = Modifier.width(84.dp), shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)) {
                Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(tile.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(tile.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(tile.count, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A fast-scroll thumb, set in from the screen edge so it never fights the Back gesture. */
@Composable
private fun FastScroll(list: LazyListState, count: Int, reversed: Boolean, left: Boolean, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var height by remember { mutableIntStateOf(1) }
    val first = list.firstVisibleItemIndex
    val fraction = (first.toFloat() / count.coerceAtLeast(1)).coerceIn(0f, 1f)
    val shown = if (reversed) 1f - fraction else fraction
    Box(modifier.fillMaxHeight(0.7f).width(44.dp).padding(horizontal = if (left) 14.dp else 0.dp).padding(end = if (left) 0.dp else 14.dp)
        .onSizeChanged { height = it.height }
        .pointerInput(count, reversed) {
            detectVerticalDragGestures { change, _ ->
                change.consume()
                val f = (change.position.y / size.height).coerceIn(0f, 1f)
                val index = ((if (reversed) 1f - f else f) * (count - 1)).toInt()
                scope.launch { list.scrollToItem(index) }
            }
        }) {
        Box(Modifier.align(Alignment.TopCenter).offset { androidx.compose.ui.unit.IntOffset(0, ((height - 48.dp.toPx()) * shown).toInt()) }
            .size(8.dp, 48.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(4.dp)))
    }
}
