package app.freshtuber.ui.music

import app.freshtuber.ui.motion.blurVisible
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.freshtuber.R
import app.freshtuber.data.music.MusicTrack
import app.freshtuber.player.Chapter
import app.freshtuber.player.music.MusicSleep
import app.freshtuber.player.music.MusicState
import app.freshtuber.player.music.QueueLayer
import app.freshtuber.player.music.RepeatMode
import app.freshtuber.ui.components.ChipItem
import app.freshtuber.ui.components.HandChips
import app.freshtuber.ui.components.LocalLeftHanded
import app.freshtuber.ui.components.handOrder
import app.freshtuber.ui.player.Bubble
import app.freshtuber.ui.player.rememberProgress
import app.freshtuber.ui.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The one place on the player screen: the cover, the lyrics or the queue take turns in it. */
private enum class Face { Cover, Lyrics, Queue }

/**
 * «Сейчас играет»: a scene with three faces (cover, lyrics, queue) on top to look at, the pad
 * at the bottom to touch. The light comes from the cover; swiping the cover changes the song
 * and the light flows to the next one. A tap on the cover shows the lyrics in its place.
 */
@Composable
fun MusicPlayerScreen(onCollapse: () -> Unit, viewModel: MusicViewModel = hiltViewModel()) {
    MusicNotes(viewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val libraryIds by viewModel.libraryIds.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val track = state.queue.track
    val left = LocalLeftHanded.current
    val light = app.freshtuber.ui.liquid.LocalLiquidLight.current
    val haptics = LocalHapticFeedback.current
    val music = viewModel.music
    var face by rememberSaveable { mutableStateOf(Face.Cover) }
    var menuOpen by remember { mutableStateOf(false) }
    var direction by remember { mutableIntStateOf(1) }

    // The cover is the light's picture, as a video is on the watch page.
    DisposableEffect(light) {
        light?.enterPlayer(); light?.musicMode = true
        onDispose { light?.exitPlayer(); light?.musicMode = false }
    }
    LaunchedEffect(track?.artwork) { light?.setVideoThumbnail(track?.artwork) }
    LaunchedEffect(track == null) { if (track == null) onCollapse() }
    BackHandler(face != Face.Cover) { face = Face.Cover }
    if (track == null) return
    // One sheet for any song, here and in the lists.
    if (menuOpen) TrackMenu(track, viewModel, onDismiss = { menuOpen = false })

    val next = { direction = 1; haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); music.next() }
    val previous = { direction = -1; haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); music.previous() }
    val long = track.durationMs > 20 * 60_000L || state.chapters.isNotEmpty()
    val hearted = MusicLibraryLogic.hearted(track, libraryIds)

    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    val download = downloaded[track.videoId]

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .navigationBarsPadding()) {
        // Top: what plays and where from (or the page's name). Swiping down here collapses into the dock line.
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).pointerInput(Unit) {
            var drag = 0f
            detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                if (drag > 80.dp.toPx()) { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd); onCollapse() }
            }) { change, amount -> drag += amount; if (drag > 0) change.consume() }
        }, verticalAlignment = Alignment.CenterVertically) {
            // On the lyrics and queue pages the arrow goes back to the cover, Back does the same.
            val collapse: @Composable () -> Unit = {
                PadIcon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.music_collapse),
                    onClick = { if (face == Face.Cover) onCollapse() else face = Face.Cover })
            }
            if (!left) collapse()
            if (face == Face.Cover) Text(contextLine(state, online), Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Text(stringResource(if (face == Face.Queue) R.string.music_queue else R.string.music_lyrics), Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1)
            if (left) collapse()
        }
        if (face == Face.Cover) {
            // The big view: the cover, the title with the heart, and the fan remote in the corner under the thumb.
            // Spare height goes above the cover, so the cover, the title and the remote stay together.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.BottomCenter) {
                val side = minOf(maxWidth, maxHeight)
                CoverArea(track, false, direction, long, state, viewModel, Modifier.size(side),
                    onTap = { face = Face.Lyrics }, onNext = next, onPrevious = previous,
                    onSeek = { seconds -> music.seekBy(seconds * 1000L); haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) })
            }
            TitleRow(track, left, hearted, onHeart = { viewModel.toggleHeart(track); haptics.performHapticFeedback(HapticFeedbackType.Confirm) })
            MusicFan(state, music.player, left, download = download, youTube = track.isYouTube,
                onShuffle = { music.toggleShuffle(); haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                onRepeat = { music.cycleRepeat(); haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                onDownload = { viewModel.download(track) },
                onLyrics = { face = Face.Lyrics },
                onQueue = { face = Face.Queue },
                onMore = { menuOpen = true },
                onPrevious = previous, onNext = next, onToggle = music::toggle,
                onScan = { on -> music.scan(on); if (on) haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                onSeekTo = music::seekTo)
        } else {
            // Lyrics and the queue open as whole pages; the song stays only in the small line at
            // the bottom, and a tap on it brings the big view back.
            if (face == Face.Queue) QueueFace(state, viewModel, left, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp))
            else LyricsView(track, viewModel, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp))
            Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)) {
                MusicLine(state, music.player, offline = !online, onOpen = { face = Face.Cover }, onToggle = music::toggle,
                    onNext = next, onPrevious = previous, onStop = music::stop)
            }
        }
    }
}

@Composable
private fun contextLine(state: MusicState, online: Boolean): String {
    if (!online) return stringResource(R.string.music_offline_note)
    val queue = state.queue
    val title = queue.title
    val position = queue.collectionPosition
    return when {
        queue.current?.layer == QueueLayer.Radio -> stringResource(R.string.music_layer_radio)
        title != null && position != null && position.second > 1 -> stringResource(R.string.music_from, title, position.first, position.second)
        title != null -> stringResource(R.string.music_from_short, title)
        else -> ""
    }
}

@Composable
private fun CoverArea(track: MusicTrack, showLyrics: Boolean, direction: Int, long: Boolean, state: MusicState, viewModel: MusicViewModel,
    modifier: Modifier, onTap: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onSeek: (Int) -> Unit) {
    val light = app.freshtuber.ui.liquid.LocalLiquidLight.current
    var drag by remember { mutableFloatStateOf(0f) }
    val shift by androidx.compose.animation.core.animateFloatAsState(drag, label = "coverDrag")
    val lyricsLabel = stringResource(R.string.music_lyrics)
    Box(modifier
        .onGloballyPositioned { light?.setVideoRect(it.boundsInRoot()) }
        .semantics { contentDescription = lyricsLabel }
        .pointerInput(track.id) {
            detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                val threshold = size.width * 0.22f
                if (drag < -threshold) onNext() else if (drag > threshold) onPrevious()
                drag = 0f
            }, onDragCancel = { drag = 0f }) { change, amount -> change.consume(); drag += amount }
        }
        .pointerInput(track.id, long) {
            // Double taps on the sides skip ±10 s, only in long mixes, so short songs open lyrics at once.
            detectTapGestures(onTap = { onTap() }, onDoubleTap = if (long) { { at -> onSeek(if (at.x < size.width / 2) -10 else 10) } } else null)
        }
        .graphicsLayer { translationX = shift * 0.5f; rotationZ = shift / 80f }) {
        // A new song or the lyrics come into focus out of a blur where the cover was; nothing slides.
        AnimatedContent(track to showLyrics, transitionSpec = {
            if (initialState.first.id != targetState.first.id) app.freshtuber.ui.motion.blurSwap(340, 240, 0.9f)
            else app.freshtuber.ui.motion.blurSwap(260, 200, 0.97f)
        }, label = "cover") { (shown, lyrics) ->
            Box(Modifier.fillMaxSize().then(blurVisible(24.dp, 340, 240))) {
                if (lyrics) LyricsView(shown, viewModel, Modifier.fillMaxSize())
                else Cover(shown.artwork, Modifier.fillMaxSize(), corner = 26.dp)
            }
        }
    }
}

@Composable
private fun LyricsView(track: MusicTrack, viewModel: MusicViewModel, modifier: Modifier) {
    LaunchedEffect(track.id) { viewModel.loadLyrics(track) }
    val lyrics by viewModel.lyricsState.collectAsStateWithLifecycle()
    val progress = rememberProgress(viewModel.music.player)
    val state = lyrics?.takeIf { it.first == track.id }?.second ?: LyricsState.Loading
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        when (state) {
            LyricsState.Loading -> Text(stringResource(R.string.music_lyrics_loading), style = MaterialTheme.typography.bodyLarge)
            LyricsState.Missing -> Text(stringResource(R.string.music_lyrics_missing), style = MaterialTheme.typography.titleMedium)
            LyricsState.Off -> Text(stringResource(R.string.music_lyrics_off), Modifier.padding(24.dp), textAlign = TextAlign.Center)
            LyricsState.Failed -> Text(stringResource(R.string.music_lyrics_error), Modifier.padding(24.dp), textAlign = TextAlign.Center)
            is LyricsState.Found -> {
                val lines = state.lyrics.lines
                val current = state.lyrics.lineAt(progress.positionMs)
                val list = rememberLazyListState()
                LaunchedEffect(current) { if (current >= 0) list.animateScrollToItem((current - 2).coerceAtLeast(0)) }
                LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(vertical = 24.dp, horizontal = 20.dp)) {
                    itemsIndexed(lines) { index, line ->
                        val active = index == current
                        Text(line.text.ifEmpty { "♪" }, Modifier.fillMaxWidth().padding(vertical = 5.dp)
                            .then(if (line.timeMs != null) Modifier.clickable { viewModel.music.seekTo(line.timeMs) } else Modifier),
                            style = if (state.lyrics.synced) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (!state.lyrics.synced || active) 1f else 0.45f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleRow(track: MusicTrack, left: Boolean, hearted: Boolean, onHeart: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val heart: @Composable () -> Unit = {
            PadIcon(if (hearted) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                stringResource(if (hearted) R.string.music_unfavorite else R.string.music_favorite), selected = hearted, onClick = onHeart)
        }
        if (left) heart()
        Column(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalAlignment = if (left) Alignment.End else Alignment.Start) {
            Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!left) heart()
    }
}

@Composable
private fun PadIcon(icon: ImageVector, label: String, selected: Boolean = false, big: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(if (big) 52.dp else 44.dp).semantics { this.selected = selected },
        colors = if (selected) IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary) else IconButtonDefaults.iconButtonColors()) {
        Icon(icon, label, Modifier.size(if (big) 32.dp else 24.dp))
    }
}

private val SleepChoices = listOf(MusicSleep.Off, MusicSleep.Minutes(15), MusicSleep.Minutes(30), MusicSleep.Minutes(45),
    MusicSleep.Minutes(60), MusicSleep.Minutes(90), MusicSleep.EndOfSong)

/**
 * «Режим»: «Бесконечно» and the sleep timer (shuffle and repeat are buttons on the player itself) (minutes or «До конца песни»; the
 * last 30 seconds fade out) in one sheet, opened from the queue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSheet(state: MusicState, music: app.freshtuber.player.music.MusicPlayer, onDismiss: () -> Unit) {
    val left = LocalLeftHanded.current
    val queue = state.queue
    app.freshtuber.ui.components.BlurSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.music_mode), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            ModeRow(Icons.Rounded.AllInclusive, stringResource(R.string.music_endless), queue.endless) { music.setEndless(!queue.endless) }
            Text(stringResource(R.string.music_sleep), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
            val remaining = (state.sleep as? MusicSleep.Minutes)?.let { (state.sleepEndsAt - System.currentTimeMillis()).coerceAtLeast(0) }
            Text(remaining?.let { stringResource(R.string.music_sleep_left, formatDuration(it / 1000)) } ?: stringResource(R.string.music_sleep_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            HandChips(SleepChoices.map { choice ->
                val label = when (choice) {
                    MusicSleep.Off -> stringResource(R.string.music_sleep_off)
                    MusicSleep.EndOfSong -> stringResource(R.string.music_sleep_end)
                    is MusicSleep.Minutes -> stringResource(R.string.music_sleep_minutes, choice.value)
                }
                ChipItem(label, choice == state.sleep) { music.setSleep(choice) }
            }, left, natural = true)
        }
    }
}

@Composable
private fun ModeRow(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { selected = on }.clickable(onClick = onClick).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(on, onCheckedChange = null)
    }
}

/**
 * The queue as the third face of the scene: the playing song lowest, by the thumb, the next ones
 * above it. First what the listener added, then the collection, then pale «Похожее». Drag a
 * handle to reorder within a layer; swipe a song to the edge to remove it (with «Вернуть»).
 * One «Режим» button at the bottom opens shuffle, repeat, the sleep timer and «Бесконечно».
 */
@Composable
private fun QueueFace(state: MusicState, viewModel: MusicViewModel, left: Boolean, modifier: Modifier) {
    val queue = state.queue
    val current = queue.track ?: return
    val music = viewModel.music
    val messages = app.freshtuber.ui.components.LocalAppMessages.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    var modeOpen by remember { mutableStateOf(false) }
    if (modeOpen) ModeSheet(state, music) { modeOpen = false }
    Column(modifier.clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = if (left) Alignment.End else Alignment.Start) {
            // The page's name is in the top row; here only what is left to play.
            Text(songsAndMinutes(queue.upcoming.size, queue.upcomingDurationMs), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val upcoming = queue.upcoming
        val list = rememberLazyListState()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, reverseLayout = true) {
            item(key = "current") {
                QueueRow(current, originOf(current, downloaded), current = true, playing = state.playing, layer = null)
            }
            for (layer in QueueLayer.entries) {
                val group = upcoming.withIndex().filter { it.value.layer == layer }
                if (group.isEmpty()) continue
                group.forEachIndexed { withinLayer, (index, entry) ->
                    item(key = "${layer.name}:${entry.track.id}:$withinLayer") {
                        QueueRow(entry.track, originOf(entry.track, downloaded), current = false, playing = false, layer = layer,
                            dimmed = layer == QueueLayer.Radio || entry.track.id in state.unavailable,
                            indexInLayer = withinLayer, layerSize = group.size,
                            onClick = { music.jump(index) },
                            onMove = { from, to -> music.move(layer, from, to) },
                            onRemove = {
                                music.remove(layer, withinLayer)
                                messages?.show(resources.getString(R.string.music_removed), resources.getString(R.string.action_return)) { undo ->
                                    if (undo) music.restore(layer, withinLayer, entry.track)
                                }
                            })
                    }
                }
                item(key = "label:${layer.name}") {
                    val label = when (layer) {
                        QueueLayer.Added -> stringResource(R.string.music_layer_added)
                        QueueLayer.Collection -> queue.title?.let { stringResource(R.string.music_layer_collection, it) } ?: stringResource(R.string.music_layer_collection_plain)
                        QueueLayer.Radio -> stringResource(R.string.music_layer_radio)
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp))
                }
            }
            if (state.radioLoading) item(key = "radioLoading") {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
            }
        }
        // «Режим» at the bottom, under the thumb, on the hand's side.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = if (left) Arrangement.Start else Arrangement.End) {
            FilledTonalButton(onClick = { modeOpen = true }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.music_mode))
            }
        }
    }
}

@Composable
private fun QueueRow(track: MusicTrack, origin: SongOrigin, current: Boolean, playing: Boolean, layer: QueueLayer?, dimmed: Boolean = false,
    indexInLayer: Int = 0, layerSize: Int = 1, onClick: () -> Unit = {}, onMove: (Int, Int) -> Unit = { _, _ -> }, onRemove: () -> Unit = {}) {
    val density = LocalDensity.current
    val rowHeight = with(density) { 64.dp.toPx() }
    var dragY by remember(track.id, indexInLayer) { mutableFloatStateOf(0f) }
    var swipe by remember(track.id, indexInLayer) { mutableFloatStateOf(0f) }
    val swipeShown by androidx.compose.animation.core.animateFloatAsState(swipe, label = "queueSwipe")
    val haptics = LocalHapticFeedback.current
    Box(Modifier.fillMaxWidth().graphicsLayer {
        translationY = dragY
        translationX = swipeShown
        alpha = 1f - (abs(swipeShown) / size.width).coerceIn(0f, 0.8f)
    }.then(if (layer != null) Modifier.pointerInput(track.id, indexInLayer) {
        detectHorizontalDragGestures(onDragEnd = {
            if (abs(swipe) > size.width * 0.35f) { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd); onRemove() }
            swipe = 0f
        }, onDragCancel = { swipe = 0f }) { change, amount -> change.consume(); swipe += amount }
    } else Modifier)) {
        // The reorder handle stands on the side away from the thumb (left for the right hand),
        // so scrolling the queue does not catch it by accident.
        val left = LocalLeftHanded.current
        val handle: @Composable () -> Unit = {
            if (layer != null && !current) {
                    val dragLabel = stringResource(R.string.music_drag)
                    Box(Modifier.size(44.dp).semantics { contentDescription = dragLabel }.pointerInput(track.id, indexInLayer, layerSize) {
                        detectVerticalDragGestures(onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }, onDragEnd = {
                            // Reversed list: moving up goes later in the queue.
                            val steps = (-dragY / rowHeight).let { if (it > 0) (it + 0.5f).toInt() else (it - 0.5f).toInt() }
                            val target = (indexInLayer + steps).coerceIn(0, layerSize - 1)
                            dragY = 0f
                            if (target != indexInLayer) onMove(indexInLayer, target)
                        }, onDragCancel = { dragY = 0f }) { change, amount -> change.consume(); dragY += amount }
                    }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        TrackRow(track, origin, onClick = onClick, onMore = {}, current = current, dimmed = dimmed, showMore = false,
            leading = if (!left && layer != null) handle else null,
            trailing = {
                if (current) Icon(if (playing) Icons.Rounded.GraphicEq else Icons.Rounded.Pause, null, tint = MaterialTheme.colorScheme.primary)
                else if (left) handle()
            })
    }
}
