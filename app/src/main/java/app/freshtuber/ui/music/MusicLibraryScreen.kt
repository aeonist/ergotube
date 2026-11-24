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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(collection: String?, viewModel: MusicViewModel = hiltViewModel()) {
    MusicNotes(viewModel)
    if (collection == null) { MusicLibraryTab(viewModel); return }
    if (collection == "playlists") { MusicPlaylists(viewModel); return }
    MusicCollection(collection, viewModel)
}

/** The four sections of the «Библиотека» tab, most used first: the first lands by the thumb. */
private enum class LibraryShelf(val collection: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Favorites("fav", R.string.music_favorites, Icons.Rounded.Favorite),
    Downloads("downloads", R.string.music_shelf_downloads, Icons.Rounded.DownloadDone),
    Phone("phone", R.string.music_shelf_phone, Icons.Rounded.PhoneAndroid),
    Playlists("playlists", R.string.music_playlists, Icons.AutoMirrored.Rounded.QueueMusic),
}

/**
 * The music dock's «Библиотека» (variant B): one list, bottom-up, with a shelf of four tiles right
 * above its play/shuffle/sort bar. A tile swaps the list in place instead of opening a new page.
 */
@Composable
private fun MusicLibraryTab(viewModel: MusicViewModel) {
    var shelf by rememberSaveable { mutableStateOf(LibraryShelf.Favorites) }
    val left = LocalLeftHanded.current
    val tiles: @Composable ColumnScope.() -> Unit = { LibraryShelfRow(shelf, left) { shelf = it } }
    key(shelf) {
        if (shelf == LibraryShelf.Playlists) MusicPlaylists(viewModel, title = stringResource(R.string.music_library),
            subtitle = stringResource(shelf.labelRes), shelf = tiles)
        else MusicCollection(shelf.collection, viewModel, shelf = tiles, tabLabel = stringResource(shelf.labelRes))
    }
}

@Composable
private fun LibraryShelfRow(current: LibraryShelf, left: Boolean, onPick: (LibraryShelf) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        app.freshtuber.ui.components.handOrder(LibraryShelf.entries, left).forEach { item ->
            val on = item == current
            val fg = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Column(Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(18.dp))
                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                .semantics { selected = on }
                .clickable(role = androidx.compose.ui.semantics.Role.Tab) { onPick(item) }
                .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(item.icon, null, Modifier.size(22.dp), tint = fg)
                Text(stringResource(item.labelRes), style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicCollection(collection: String, viewModel: MusicViewModel, shelf: (@Composable ColumnScope.() -> Unit)? = null, tabLabel: String? = null) {
    val device = collection in listOf("device", "phone", "downloads")
    if (device) MusicFolderAccess(viewModel)
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
    LaunchedEffect(showFolder) { if (device && showFolder) viewModel.rescanFolder() }

    // Downloaded songs that are not in the library rows still belong in the list.
    val downloadedIds = remember(downloaded) { downloaded.values.filter { it.state == DownloadStatus.Complete }.map { it.videoId }.toSet() }
    val localById = remember(tracks) { tracks.associateBy { it.id } }
    val all = remember(tracks, downloaded) {
        val known = localById
        val extra = downloaded.values.filter { it.state == DownloadStatus.Complete }.map { known["yt:${it.videoId}"] ?: viewModel.downloadedTrack(it) }
        (tracks + extra).distinctBy { it.id }
    }
    val playlistSource = remember(playlistId) { playlistId?.let(viewModel::playlistTracks) ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
    val playlistTracks by playlistSource.collectAsStateWithLifecycle(emptyList())
    var playlistName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(playlistId, playlists) { playlistName = playlistId?.let { viewModel.playlistName(it) } }

    val favoritesPage = !device && playlistId == null
    val split = favoritesPage && preferences.enabled(AppFeature.SeparateFavorites)
    var chosen by rememberSaveable(collection) { mutableStateOf(if (collection == "video-fav") FavoriteSection.Video else FavoriteSection.Music) }
    val section = if (split) chosen else FavoriteSection.All
    val videoMode = favoritesPage && (section == FavoriteSection.Video || (section == FavoriteSection.All && collection == "video-fav"))
    val localMusic = remember(tracks) { tracks.filter { it.favorite } }
    val videoMarks by viewModel.videoFavorites.collectAsStateWithLifecycle(emptyList())
    val account by viewModel.account.collectAsStateWithLifecycle(null)
    var retry by remember { mutableIntStateOf(0) }
    val likes = key(account, online, retry) {
        val profile = account
        if (favoritesPage && profile != null && section != FavoriteSection.Video) {
            val source = remember { viewModel.likedSongs(profile, online) }
            source.collectAsStateWithLifecycle(app.freshtuber.data.music.LikedSongs(loading = online)).value
        } else app.freshtuber.data.music.LikedSongs()
    }
    val base = remember(playlistTracks, collection, all, filter, downloadedIds, section, videoMarks, likes.tracks) { when {
        playlistId != null -> playlistTracks
        device -> MusicLibraryLogic.filter(all.filter { MusicLibraryLogic.isLocal(it, downloadedIds) }, filter, downloadedIds)
        else -> MusicLibraryLogic.favorites(localMusic, videoMarks, likes.tracks, section).map { mark ->
            // Preserve the downloaded URI when the account also contains this recording.
            localById[mark.id]?.let { local -> mark.copy(uri = local.uri ?: mark.uri) } ?: mark
        }
    }
    }
    val visible = remember(base, online, downloadedIds) { MusicLibraryLogic.visible(base, online, downloadedIds) }
    val shown = remember(visible, playlistId, sort) { if (playlistId != null) visible.tracks else MusicLibraryLogic.sort(visible.tracks, sort) }
    val title = if (playlistId != null) playlistName.orEmpty() else stringResource(if (device) R.string.music_on_device else R.string.music_favorites)
    val play: (Int) -> Unit = { index ->
        if (videoMode && shown[index].isYouTube) navigation?.watchVideo(shown[index], shown)
        else {
            viewModel.music.playCollection(shown, index, title)
            navigation?.openPlayer()
        }
    }
    val bytes = downloaded.values.filter { it.state == DownloadStatus.Complete }.sumOf { it.bytesTotal }
    val subtitle = if (device) stringResource(R.string.music_library_summary, songsCount(base.size),
        android.text.format.Formatter.formatShortFileSize(context, bytes)) else songsCount(shown.size)

    val list = rememberLazyListState()
    // The four big buttons step aside while the list is read and come back on the way back.
    val hide = app.freshtuber.ui.components.rememberScrollHide(inverted = fromBottom)
    Column(Modifier.fillMaxSize()) {
        if (tabLabel != null) ScreenHeader(stringResource(R.string.music_library), "$tabLabel · $subtitle") else ScreenHeader(title, subtitle)
        if (likes.failed) TextButton(onClick = { retry++ }) { Text(stringResource(R.string.music_likes_retry)) }
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
                if (shown.isEmpty() && likes.loading) item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                if (shown.isEmpty() && !likes.loading && !likes.failed) item(key = "empty") { Box(Modifier.height(220.dp)) { MessageState(stringResource(if (favoritesPage) R.string.favorites_empty else R.string.music_library_empty)) } }
            }
            if (shown.size > 30) FastScroll(list, shown.size, fromBottom, left, Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd))
        }
        // Filters and playback controls stay above the dock.
        if (split) app.freshtuber.ui.components.HandChips(listOf(
            app.freshtuber.ui.components.ChipItem(stringResource(R.string.search_source_music), chosen == FavoriteSection.Music) { chosen = FavoriteSection.Music },
            app.freshtuber.ui.components.ChipItem(stringResource(R.string.search_source_video), chosen == FavoriteSection.Video) { chosen = FavoriteSection.Video },
        ), left)
        if (device && shelf == null) app.freshtuber.ui.components.HandChips(LibraryFilter.entries.map { option ->
            app.freshtuber.ui.components.ChipItem(stringResource(option.labelRes), option == filter) { filter = option }
        }, left)
        val actions = buildList {
            add(ContextAction(Icons.Rounded.PlayArrow, stringResource(R.string.music_play_all), primary = true, enabled = shown.isNotEmpty()) { play(0) })
            add(ContextAction(Icons.Rounded.Shuffle, stringResource(R.string.music_shuffle), enabled = shown.isNotEmpty()) {
                val index = shown.indices.random()
                if (videoMode && shown[index].isYouTube) navigation?.watchVideo(shown[index], listOf(shown[index]) + shown.filterIndexed { i, _ -> i != index }.shuffled())
                else {
                    viewModel.music.playCollection(shown, index, title, shuffle = true)
                    navigation?.openPlayer()
                }
            })
            if (playlistId == null) add(ContextAction(Icons.Rounded.Sort, stringResource(R.string.music_sort)) { sortOpen = true })
            if (device || playlistId != null) add(ContextAction(Icons.Rounded.MoreHoriz, stringResource(R.string.music_more)) { moreOpen = true })
        }
        app.freshtuber.ui.motion.BlurVisibility(hide.visible, collapse = true) { ContextBar(actions, left, above = shelf) }
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
