package app.freshtuber.ui.music

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.data.downloads.Download
import app.freshtuber.data.downloads.DownloadStatus
import app.freshtuber.data.music.MusicSource
import app.freshtuber.data.music.MusicTrack
import app.freshtuber.ui.components.LocalAppMessages
import app.freshtuber.ui.components.LocalLeftHanded
import app.freshtuber.ui.components.TileGrid
import app.freshtuber.ui.components.TileItem
import coil3.compose.AsyncImage
import kotlin.math.abs

/** Where music screens can go; provided by the app root. */
interface MusicNavigation {
    fun openPlayer()
    /** A library collection: `fav` or `playlist:<id>`. */
    fun openCollection(collection: String)
    /** The «Музыка» page. */
    fun openMusicHome()
    /** «Смотреть клип»: the video opens at the song's current second; the song pauses. */
    fun watchVideo(track: MusicTrack)
}

val LocalMusicNavigation = staticCompositionLocalOf<MusicNavigation?> { null }

/** A square cover; phone files without art get a quiet note. */
@Composable
fun Cover(url: String?, modifier: Modifier = Modifier, corner: Dp = 10.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (url == null) Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Lets this picture light the background while it is in focus, like a feed card. */
@Composable
fun Modifier.lightSource(url: String?): Modifier {
    val light = app.freshtuber.ui.liquid.LocalLiquidLight.current ?: return this
    val key = remember { Any() }
    DisposableEffect(light, key) { onDispose { light.removeCard(key) } }
    return onGloballyPositioned { c ->
        val p = c.positionInRoot()
        light.reportCard(key, url, Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height))
    }
}

enum class SongOrigin(val icon: ImageVector, val labelRes: Int) {
    Downloaded(Icons.Rounded.DownloadDone, R.string.music_source_downloaded),
    Phone(Icons.Rounded.PhoneAndroid, R.string.music_source_phone),
    Online(Icons.Rounded.Cloud, R.string.music_source_online),
}

fun originOf(track: MusicTrack, downloads: Map<String, Download>): SongOrigin = when {
    track.source == MusicSource.Phone -> SongOrigin.Phone
    downloads[track.videoId]?.state == DownloadStatus.Complete -> SongOrigin.Downloaded
    else -> SongOrigin.Online
}

/**
 * One song: cover, title, artist (and length), a mark of where it lives, and «⋮». Hold and
 * flick: up «Играть следующей», right «Скачать», left «В конец очереди», down «Поделиться».
 */
@Composable
fun TrackRow(
    track: MusicTrack,
    origin: SongOrigin,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    current: Boolean = false,
    dimmed: Boolean = false,
    detail: String? = null,
    onFlick: ((Flick) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showMore: Boolean = true,
) {
    val flick = LocalFlickHost.current
    val haptics = LocalHapticFeedback.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentFlick by rememberUpdatedState(onFlick)
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).alpha(if (dimmed) 0.42f else 1f)
            .onGloballyPositioned { coords = it }
            .then(if (onFlick != null && flick != null) Modifier.pointerInput(track.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> coords?.let { flick.start(it.localToRoot(offset)) } },
                    onDrag = { change, _ -> change.consume(); coords?.let { if (flick.move(it.localToRoot(change.position))) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) } },
                    onDragEnd = { flick.finish()?.let { currentFlick?.invoke(it) } ?: onMore() },
                    onDragCancel = { flick.cancel() },
                )
            } else Modifier)
            .clickable(onClick = onClick)
            .background(if (current) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.artwork, Modifier.size(48.dp), corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(track.artist?.takeIf { it.isNotBlank() }, detail ?: track.durationMs.takeIf { it > 0 }?.let { app.freshtuber.ui.util.formatDuration(it / 1000) })
                .joinToString(" · ")
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
        val originLabel = stringResource(origin.labelRes)
        Icon(origin.icon, null, Modifier.padding(horizontal = 6.dp).size(18.dp).semantics { contentDescription = originLabel },
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (showMore) IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.music_more)) }
    }
}

/** A flick after holding a song. */
enum class Flick(val icon: ImageVector, val labelRes: Int) {
    Up(Icons.AutoMirrored.Rounded.QueueMusic, R.string.music_play_next),
    Right(Icons.Rounded.Download, R.string.music_download),
    Left(Icons.AutoMirrored.Rounded.PlaylistAdd, R.string.music_play_last),
    Down(Icons.Rounded.Share, R.string.music_share),
}

/** The four-way hold-and-flick for songs, drawn above everything by [FlickOverlay]. */
class FlickHost {
    var center by mutableStateOf<Offset?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var selected by mutableStateOf<Flick?>(null)
        private set
    internal var density = 1f

    fun start(at: Offset) { center = at; pointer = at; selected = null }

    /** True when the selection changed to a direction. */
    fun move(to: Offset): Boolean {
        val c = center ?: return false
        pointer = to
        val d = to - c
        val pick = when {
            d.getDistance() < DEAD_DP * density -> null
            abs(d.x) > abs(d.y) -> if (d.x > 0) Flick.Right else Flick.Left
            else -> if (d.y < 0) Flick.Up else Flick.Down
        }
        val changed = pick != selected && pick != null
        selected = pick
        return changed
    }

    fun finish(): Flick? = selected.also { center = null; selected = null }
    fun cancel() { center = null; selected = null }

    companion object { const val DEAD_DP = 34f; const val REACH_DP = 86f }
}

val LocalFlickHost = staticCompositionLocalOf<FlickHost?> { null }

@Composable
fun FlickOverlay(host: FlickHost) {
    val density = LocalDensity.current
    host.density = density.density
    val center = host.center
    val phase by animateFloatAsState(if (center != null) 1f else 0f, label = "flick")
    var last by remember { mutableStateOf(Offset.Zero) }
    if (center != null) last = center
    if (phase <= 0f) return
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(center != null) { if (center != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    Box(Modifier.fillMaxSize().graphicsLayer {
        alpha = phase
        val r = (1f - phase) * 16.dp.toPx()
        if (android.os.Build.VERSION.SDK_INT >= 31) renderEffect = if (r > 0.5f)
            androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Decal) else null
    }) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)))
        Text(host.selected?.let { stringResource(it.labelRes) } ?: stringResource(R.string.music_flick_hint),
            Modifier.fillMaxWidth().padding(top = 72.dp, start = 24.dp, end = 24.dp), color = Color.White, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium)
        val reach = FlickHost.REACH_DP * density.density
        Flick.entries.forEach { flick ->
            val p = when (flick) {
                Flick.Up -> Offset(last.x, last.y - reach)
                Flick.Down -> Offset(last.x, last.y + reach)
                Flick.Left -> Offset(last.x - reach, last.y)
                Flick.Right -> Offset(last.x + reach, last.y)
            }
            val selected = host.selected == flick
            val scale by animateFloatAsState(if (selected) 1.14f else 1f, label = "flick-item")
            Column(Modifier.offset { IntOffset((p.x - 38.dp.toPx()).toInt(), (p.y - 29.dp.toPx()).toInt()) }.size(76.dp, 58.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp)),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
                Icon(flick.icon, null, tint = color)
                Text(stringResource(flick.labelRes), color = color, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                    textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.offset { IntOffset((last.x - 24.dp.toPx()).toInt(), (last.y - 24.dp.toPx()).toInt()) }.size(48.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

fun shareTrack(context: Context, track: MusicTrack, title: String) {
    if (!track.isYouTube) return
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${track.videoId}")
        .putExtra(Intent.EXTRA_SUBJECT, track.title)
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Runs a flick on a song. */
fun MusicViewModel.flick(flick: Flick, track: MusicTrack, context: Context, shareTitle: String) {
    when (flick) {
        Flick.Up -> music.playNext(track)
        Flick.Left -> music.playLast(track)
        Flick.Right -> download(track)
        Flick.Down -> shareTrack(context, track, shareTitle)
    }
}

/** Shows the view model's notes in the app snackbar. */
@Composable
fun MusicNotes(viewModel: MusicViewModel) {
    val messages = LocalAppMessages.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel, messages) {
        viewModel.notes.collect { note ->
            val text = when {
                note.count != null -> resources.getQuantityString(note.res, note.count, note.count)
                note.arg != null -> resources.getString(note.res, note.arg)
                else -> resources.getString(note.res)
            }
            messages?.show(text)
        }
    }
}

/**
 * The song menu: a short sheet of tiles, the most used lowest under the thumb. The YouTube
 * like is its own tile and only acts when tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenu(track: MusicTrack, viewModel: MusicViewModel, onDismiss: () -> Unit, playlistId: String? = null) {
    val left = LocalLeftHanded.current
    val context = LocalContext.current
    val navigation = LocalMusicNavigation.current
    val libraryIds by viewModel.libraryIds.collectAsState()
    val downloaded by viewModel.downloaded.collectAsState()
    val favorite = MusicLibraryLogic.hearted(track, libraryIds)
    val download = downloaded[track.videoId]
    var picker by remember { mutableStateOf(false) }
    val shareTitle = stringResource(R.string.music_share)
    if (picker) { PlaylistPicker(track, viewModel) { picker = false; onDismiss() }; return }
    app.freshtuber.ui.components.BlurSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(track.artwork, Modifier.size(44.dp), corner = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    track.artist?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                }
            }
            val tiles = buildList {
                add(TileItem(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.music_play_next)) { viewModel.music.playNext(track); onDismiss() })
                add(TileItem(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    stringResource(if (favorite) R.string.music_unfavorite else R.string.music_favorite), selected = favorite) { viewModel.toggleHeart(track); onDismiss() })
                add(TileItem(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.music_play_last)) { viewModel.music.playLast(track); onDismiss() })
                if (track.isYouTube) {
                    if (download == null) add(TileItem(Icons.Rounded.Download, stringResource(R.string.music_download)) { viewModel.download(track); onDismiss() })
                    else add(TileItem(Icons.Rounded.DeleteOutline, stringResource(R.string.music_remove_download)) { viewModel.removeDownload(track); onDismiss() })
                }
                add(TileItem(Icons.Rounded.PlaylistAdd, stringResource(R.string.music_add_to_playlist)) { picker = true })
                if (track.isYouTube) add(TileItem(Icons.Rounded.Radio, stringResource(R.string.music_radio)) { viewModel.music.playRadio(track); navigation?.openPlayer(); onDismiss() })
                if (track.isYouTube) add(TileItem(Icons.Rounded.SmartDisplay, stringResource(R.string.music_watch_video)) { onDismiss(); navigation?.watchVideo(track) })
                if (track.isYouTube) add(TileItem(Icons.Rounded.Share, shareTitle) { shareTrack(context, track, shareTitle); onDismiss() })
                if (playlistId != null) add(TileItem(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.music_remove_from_playlist)) { viewModel.removeFromPlaylist(playlistId, track); onDismiss() })
                if (track.isYouTube) add(TileItem(Icons.Rounded.ThumbUp, stringResource(R.string.music_like_youtube)) { viewModel.likeOnYouTube(track); onDismiss() })
            }
            TileGrid(tiles, columns = 4, left = left)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPicker(track: MusicTrack?, viewModel: MusicViewModel, onDismiss: () -> Unit) {
    val playlists by viewModel.playlists.collectAsState()
    var naming by remember { mutableStateOf(playlists.isEmpty() && track == null) }
    var name by remember { mutableStateOf("") }
    app.freshtuber.ui.components.BlurSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            playlists.forEach { playlist ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { track?.let { viewModel.addToPlaylist(playlist, it) }; onDismiss() }
                    .padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null)
                    Spacer(Modifier.width(16.dp))
                    Text(playlist.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(playlist.count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (naming) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.music_playlist_name)) })
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.createPlaylist(name, track); onDismiss() }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_create)) }
            } else Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { naming = true }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.music_new_playlist), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** «12 песен · 47 мин». */
@Composable
fun songsAndMinutes(count: Int, durationMs: Long, known: Boolean = true): String {
    val songs = pluralStringResource(R.plurals.music_songs, count, count)
    val minutes = (durationMs / 60_000).toInt()
    return if (minutes > 0 && known) "$songs · ${stringResource(R.string.music_minutes, minutes)}" else songs
}
