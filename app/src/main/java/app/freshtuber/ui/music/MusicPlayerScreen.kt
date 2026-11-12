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

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .navigationBarsPadding()) {
        // Top: what plays and where from. Swiping down here collapses into the dock line.
        Column(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
            var drag = 0f
            detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                if (drag > 80.dp.toPx()) { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd); onCollapse() }
            }) { change, amount -> drag += amount; if (drag > 0 && face != Face.Queue) change.consume() }
        }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val collapse: @Composable () -> Unit = { PadIcon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.music_collapse), onClick = onCollapse) }
                if (!left) collapse()
                Text(contextLine(state, online), Modifier.weight(1f).padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (left) collapse()
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                if (face == Face.Queue) QueueFace(state, viewModel, left, Modifier.fillMaxSize())
                else {
                    val side = minOf(maxWidth, maxHeight)
                    CoverArea(track, face == Face.Lyrics, direction, long, state, viewModel, Modifier.size(side),
                        onTap = { face = if (face == Face.Lyrics) Face.Cover else Face.Lyrics }, onNext = next, onPrevious = previous,
                        onSeek = { seconds -> music.seekBy(seconds * 1000L); haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) })
                }
            }
            TitleRow(track)
        }
        MusicPad(state, music.player, left, hearted = hearted, lyricsOn = face == Face.Lyrics, queueOn = face == Face.Queue,
            onHeart = { viewModel.toggleHeart(track); haptics.performHapticFeedback(HapticFeedbackType.Confirm) },
            onLyrics = { face = if (face == Face.Lyrics) Face.Cover else Face.Lyrics },
            onQueue = { face = if (face == Face.Queue) Face.Cover else Face.Queue },
            onMore = { menuOpen = true },
            onPrevious = previous, onNext = next, onToggle = music::toggle,
            onScan = { on -> music.scan(on); if (on) haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            onSeekTo = music::seekTo)
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
private fun TitleRow(track: MusicTrack) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 6.dp)) {
        Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The music pad: the second liquid drop, built like the video pad and pressed to the thumb. */
@Composable
private fun MusicPad(state: MusicState, player: androidx.media3.common.Player, left: Boolean, hearted: Boolean, lyricsOn: Boolean, queueOn: Boolean,
    onHeart: () -> Unit, onLyrics: () -> Unit, onQueue: () -> Unit, onMore: () -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onToggle: () -> Unit,
    onScan: (Boolean) -> Unit, onSeekTo: (Long) -> Unit) {
    val light = app.freshtuber.ui.liquid.LocalLiquidLight.current
    val radius = with(LocalDensity.current) { 30.dp.toPx() }
    DisposableEffect(light) { onDispose { light?.setDrop(1, null, 0f) } }
    val progress = rememberProgress(player)
    val duration = progress.durationMs.takeIf { it > 0 } ?: state.queue.track?.durationMs ?: 0L
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var fine by remember { mutableStateOf(false) }
    val shownMs = scrubMs ?: progress.positionMs.takeIf { progress.durationMs > 0 } ?: state.queue.positionMs
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
        .onGloballyPositioned { light?.setDrop(1, it.boundsInRoot(), radius) }
        .pointerInput(Unit) {
            var drag = 0f
            detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                if (drag < -36.dp.toPx()) { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd); onQueue() }
            }) { change, amount -> drag += amount; change.consume() }
        }
        .padding(horizontal = 6.dp, vertical = 4.dp)) {
        // The remote: seven buttons in one row, play and the neighbours in the middle of the reach.
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            val nextLabel = stringResource(R.string.music_next)
            val buttons: List<@Composable RowScope.() -> Unit> = listOf(
                { Slot { PadIcon(Icons.Rounded.SkipPrevious, stringResource(R.string.music_previous), onClick = onPrevious) } },
                { Slot { FilledIconButton(onClick = onToggle, modifier = Modifier.size(56.dp), shape = CircleShape) {
                    Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (state.playing) R.string.player_pause else R.string.player_play), Modifier.size(32.dp))
                } } },
                // Hold Next to scan forward.
                { Slot { Box(Modifier.size(44.dp).clip(CircleShape).semantics { contentDescription = nextLabel }.pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        var scanning = false
                        val hold = scope.launch { delay(380); scanning = true; onScan(true) }
                        val released = tryAwaitRelease()
                        hold.cancel()
                        if (scanning) onScan(false) else if (released) onNext()
                    })
                }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SkipNext, null, Modifier.size(28.dp)) } } },
                { Slot { PadIcon(if (hearted) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    stringResource(if (hearted) R.string.music_unfavorite else R.string.music_favorite), selected = hearted, onClick = onHeart) } },
                { Slot { PadIcon(Icons.Rounded.Lyrics, stringResource(R.string.music_lyrics), selected = lyricsOn, onClick = onLyrics) } },
                { Slot { PadIcon(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.music_queue), selected = queueOn, onClick = onQueue) } },
                { Slot { PadIcon(Icons.Rounded.MoreHoriz, stringResource(R.string.music_more), onClick = onMore) } },
            )
            (if (left) buttons.reversed() else buttons).forEach { it() }
        }
        // Time and what comes next; a tap on the text shows the queue.
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            val time: @Composable () -> Unit = {
                Text(formatDuration(shownMs / 1000) + if (duration > 0) " / ${formatDuration(duration / 1000)}" else "",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = app.freshtuber.ui.theme.TabularNumbers),
                    modifier = Modifier.padding(horizontal = 8.dp))
            }
            val upNext = state.queue.next
            val chapter = Chapter.at(state.chapters, shownMs / 1000)
            val nextText = chapter?.title ?: upNext?.let { stringResource(R.string.music_up_next, it.title) } ?: stringResource(R.string.music_up_next_none)
            if (left) Spacer(Modifier.width(0.dp)) else time()
            Text(nextText, Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onQueue).padding(horizontal = 6.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (left) TextAlign.End else TextAlign.Start)
            if (left) time()
        }
        // The track: drag along it to seek; lift the finger above it to seek finely.
        Box(Modifier.fillMaxWidth().height(34.dp)) {
            ScrubBar(if (duration > 0) shownMs.toFloat() / duration else 0f, state.chapters, duration, scrubbing = scrubMs != null,
                modifier = Modifier.fillMaxSize().pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val liftPx = app.freshtuber.ui.player.Scrub.FINE_LIFT_DP * density
                        var position = (down.position.x / width).coerceIn(0f, 1f) * duration
                        var lastX = down.position.x
                        var isFine = false
                        scrubMs = position.toLong()
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val nowFine = down.position.y - change.position.y > liftPx
                            if (nowFine != isFine) { isFine = nowFine; fine = nowFine; haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                            position = if (isFine) (position + (change.position.x - lastX) / width * duration * app.freshtuber.ui.player.Scrub.FINE_FACTOR).coerceIn(0f, duration.toFloat())
                                else (change.position.x / width).coerceIn(0f, 1f) * duration
                            lastX = change.position.x
                            scrubMs = position.toLong()
                        }
                        onSeekTo(position.toLong())
                        scrubMs = null
                        fine = false
                    }
                })
            scrubMs?.let { Bubble(formatDuration(it / 1000) + if (fine) " · ×5" else "", Modifier.align(Alignment.TopCenter).offset(y = (-30).dp)) }
        }
    }
}

@Composable
private fun ScrubBar(fraction: Float, chapters: List<Chapter>, durationMs: Long, scrubbing: Boolean, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val rail = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val gap = MaterialTheme.colorScheme.surface
    val dot by androidx.compose.animation.core.animateFloatAsState(if (scrubbing) 9f else 6f, label = "musicDot")
    Canvas(modifier.padding(horizontal = 10.dp)) {
        val y = size.height / 2
        val stroke = 4.dp.toPx()
        drawLine(rail, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        val f = fraction.coerceIn(0f, 1f)
        drawLine(accent, Offset(0f, y), Offset(size.width * f, y), stroke, StrokeCap.Round)
        if (durationMs > 0) chapters.drop(1).forEach { c ->
            val x = size.width * (c.startSeconds * 1000f / durationMs)
            if (x in 0f..size.width) drawLine(gap, Offset(x, y - 4.dp.toPx()), Offset(x, y + 4.dp.toPx()), 2.dp.toPx())
        }
        drawCircle(accent, dot.dp.toPx(), Offset(size.width * f, y))
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

/** One button of the remote: an equal share of the row, centred. */
@Composable
private fun RowScope.Slot(content: @Composable () -> Unit) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
}

private val SleepChoices = listOf(MusicSleep.Off, MusicSleep.Minutes(15), MusicSleep.Minutes(30), MusicSleep.Minutes(45),
    MusicSleep.Minutes(60), MusicSleep.Minutes(90), MusicSleep.EndOfSong)

/**
 * «Режим»: shuffle, repeat, «Бесконечно» and the sleep timer (minutes or «До конца песни»; the
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
            ModeRow(Icons.Rounded.Shuffle, stringResource(R.string.music_shuffle), queue.shuffle) { music.toggleShuffle() }
            ModeRow(if (queue.repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                stringResource(when (queue.repeat) { RepeatMode.Off -> R.string.music_repeat; RepeatMode.All -> R.string.music_repeat_all; RepeatMode.One -> R.string.music_repeat_one }),
                queue.repeat != RepeatMode.Off) { music.cycleRepeat() }
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
 * One «Режим» button opens shuffle, repeat, the sleep timer and «Бесконечно».
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
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            val mode: @Composable () -> Unit = {
                FilledTonalButton(onClick = { modeOpen = true }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.music_mode))
                }
            }
            val heading: @Composable () -> Unit = {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.music_queue), style = MaterialTheme.typography.titleLarge)
                    Text(songsAndMinutes(queue.upcoming.size, queue.upcomingDurationMs), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (left) { mode(); Spacer(Modifier.width(8.dp)); heading() } else { heading(); mode() }
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
        TrackRow(track, origin, onClick = onClick, onMore = {}, current = current, dimmed = dimmed, showMore = false,
            trailing = {
                if (current) Icon(if (playing) Icons.Rounded.GraphicEq else Icons.Rounded.Pause, null, tint = MaterialTheme.colorScheme.primary)
                else if (layer != null) {
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
            })
    }
}
