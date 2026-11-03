package app.freshtuber.ui.player

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.freshtuber.R
import app.freshtuber.ui.radial.RadialItem
import app.freshtuber.ui.radial.ThumbArc
import app.freshtuber.player.PlayerState
import app.freshtuber.ui.feed.LoadError
import app.freshtuber.ui.feed.messageRes
import app.freshtuber.ui.theme.BrandColors
import app.freshtuber.ui.util.formatDuration
import kotlinx.coroutines.delay

/**
 * Everything drawn over the video: controls, seek-by-double-tap, brightness and volume swipes,
 * loading and error states, with auto-hiding thumb-arc controls.
 */
@Composable
fun PlayerOverlay(
    player: Player,
    state: PlayerState,
    fullscreen: Boolean,
    gesturesEnabled: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: (Offset?) -> Unit,
    onNext: (() -> Unit)?,
    onRetry: () -> Unit,
    onOpenActions: () -> Unit,
    actionsExpanded: Boolean = false,
    modifier: Modifier = Modifier,
    leftHanded: Boolean = false,
    thumbArc: ThumbArc = ThumbArc(),
) {
    val progress = rememberProgress(player)
    val autoHide = rememberAutoHide()
    autoHide.Run(keepVisible = !progress.isPlaying)
    var seekHint by remember { mutableStateOf<Int?>(null) }
    var levelHint by remember { mutableStateOf<Pair<ImageVector, Float>?>(null) }
    val context = LocalContext.current

    LaunchedEffect(seekHint) {
        if (seekHint != null) {
            delay(700)
            seekHint = null
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(player) {
                detectTapGestures(
                    onTap = { autoHide.toggle() },
                    onDoubleTap = { offset ->
                        val forward = offset.x > size.width / 2
                        val step = if (forward) SEEK_MS else -SEEK_MS
                        player.seekTo((player.currentPosition + step).coerceAtLeast(0))
                        seekHint = (seekHint ?: 0) + if (forward) 10 else -10
                    },
                )
            }
            .then(
                if (gesturesEnabled) {
                    Modifier.pointerInput(Unit) {
                        var leftSide = true
                        var volume = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                leftSide = it.x < size.width / 2
                                volume = currentVolumeSteps(context)
                            },
                            onDragEnd = { levelHint = null },
                            onDragCancel = { levelHint = null },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val delta = -dragAmount / size.height
                                levelHint = if (leftSide) {
                                    Icons.Rounded.BrightnessMedium to adjustBrightness(context, delta)
                                } else {
                                    volume = setVolumeSteps(context, volume + delta * maxVolumeSteps(context))
                                    Icons.AutoMirrored.Rounded.VolumeUp to volume / maxVolumeSteps(context)
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val loading = state.loadingId != null || progress.buffering
        when {
            state.error != null -> ErrorOverlay(state.error, onRetry)
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }

        seekHint?.let { Hint(if (it > 0) "+$it" else "$it", Modifier.align(if (it > 0) Alignment.CenterEnd else Alignment.CenterStart)) }
        levelHint?.let { (icon, level) -> LevelHint(icon, level, Modifier.align(Alignment.Center)) }

        AnimatedVisibility(
            visible = autoHide.visible || state.error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Controls(
                player = player,
                state = state,
                progress = progress,
                loading = loading || state.error != null,
                fullscreen = fullscreen,
                onBack = onBack,
                onToggleFullscreen = onToggleFullscreen,
                onOpenSettings = onOpenSettings,
                onNext = onNext,
                onInteract = autoHide::poke,
                leftHanded = leftHanded,
                thumbArc = thumbArc,
                onOpenActions = onOpenActions,
                actionsExpanded = actionsExpanded,
            )
        }
    }
}

@Composable
private fun Controls(
    player: Player,
    state: PlayerState,
    progress: Progress,
    loading: Boolean,
    fullscreen: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: (Offset?) -> Unit,
    onNext: (() -> Unit)?,
    onInteract: () -> Unit,
    leftHanded: Boolean,
    thumbArc: ThumbArc,
    onOpenActions: () -> Unit,
    actionsExpanded: Boolean,
) {
    var settingsPosition by remember { mutableStateOf<Offset?>(null) }
    val live = state.nowPlaying?.isLive == true
    Box(Modifier.fillMaxSize().background(Color(0x66000000))
        .windowInsetsPadding(if (fullscreen) WindowInsets.safeDrawing.union(WindowInsets.safeGestures).only(WindowInsetsSides.Horizontal)
            else WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .windowInsetsPadding(if (fullscreen) WindowInsets.safeDrawing.only(WindowInsetsSides.Top) else WindowInsets(0, 0, 0, 0))
                .heightIn(min = 40.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!fullscreen) WhiteIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), onBack)
            Text(
                state.nowPlaying?.title.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

        }

        if ((!loading || fullscreen) && !actionsExpanded) {
            val (playIcon, playLabel) = when {
                progress.ended -> Icons.Rounded.Replay to R.string.player_replay
                progress.isPlaying -> Icons.Rounded.Pause to R.string.player_pause
                else -> Icons.Rounded.PlayArrow to R.string.player_play
            }
            val toggle = {
                onInteract()
                when {
                    progress.ended -> { player.seekTo(0); player.play() }
                    player.isPlaying -> player.pause()
                    else -> player.play()
                }
            }
            if (fullscreen) {
                val items = buildList {
                    if (!live) add(RadialItem("back10", stringResource(R.string.player_back_10), Icons.Rounded.Replay10, enabled = !loading) { onInteract(); player.seekBack() })
                    add(RadialItem("play", stringResource(playLabel), playIcon, enabled = !loading, action = toggle))
                    if (!live) add(RadialItem("forward10", stringResource(R.string.player_forward_10), Icons.Rounded.Forward10, enabled = !loading) { onInteract(); player.seekForward() })
                    onNext?.let { next -> add(RadialItem("next", stringResource(R.string.player_next), Icons.Rounded.SkipNext) { onInteract(); next() }) }
                    add(RadialItem("back", stringResource(R.string.action_back), Icons.AutoMirrored.Rounded.ArrowBack, action = onBack))
                    add(RadialItem("actions", stringResource(R.string.player_actions), Icons.Rounded.MoreHoriz) { onInteract(); onOpenActions() })
                    add(RadialItem("settings", stringResource(R.string.player_settings), Icons.Rounded.Settings) { onInteract(); onOpenSettings(settingsPosition) })
                    add(RadialItem("exit", stringResource(R.string.player_exit_fullscreen), Icons.Rounded.FullscreenExit, action = onToggleFullscreen))
                }
                PlaybackArc(items, leftHanded, thumbArc, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(top = 48.dp, bottom = 64.dp), onSettingsPosition = { settingsPosition = it })
            }
        }

        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .windowInsetsPadding(if (fullscreen) WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) else WindowInsets(0, 0, 0, 0))
            .padding(horizontal = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    Text(
                        stringResource(R.string.search_live),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(BrandColors.Red, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                } else {
                    Text(
                        "${formatDuration(progress.positionMs / 1000)} / ${formatDuration(progress.durationMs / 1000)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(Modifier.weight(1f))

            }
            if (!live && progress.durationMs > 0) {
                SeekBar(player, progress, onInteract)
            }
        }
    }
}

/** Already unfolded when the video is tapped; no secondary menu trigger. */
@Composable
private fun PlaybackArc(items: List<RadialItem>, left: Boolean, arc: ThumbArc, modifier: Modifier, onSettingsPosition: (Offset) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val layout = app.freshtuber.ui.radial.RadialGeometry.layout(maxWidth.value, maxHeight.value, left, arc, false)
        if (layout.capacity() < items.size) {
            Column(Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.chunked(4).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (if (left) group.reversed() else group).forEach { item ->
                            IconButton(item.action, enabled = item.enabled, modifier = Modifier.size(48.dp).then(if (item.id == "settings") Modifier.onGloballyPositioned { onSettingsPosition(it.positionOnScreen() + Offset(it.size.width / 2f, it.size.height / 2f)) } else Modifier).background(Color(0xCC181818), CircleShape),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) { Icon(item.icon!!, item.title) }
                        }
                    }
                }
            }
            return@BoxWithConstraints
        }
        val points = layout.points(items.size)
        items.forEachIndexed { index, item ->
            val p = points[index]
            IconButton(onClick = item.action, enabled = item.enabled, modifier = Modifier.offset {
                androidx.compose.ui.unit.IntOffset(with(density) { (p.x - 24).dp.roundToPx() }, with(density) { (p.y - 24).dp.roundToPx() })
            }.size(48.dp).then(if (item.id == "settings") Modifier.onGloballyPositioned { onSettingsPosition(it.positionOnScreen() + Offset(it.size.width / 2f, it.size.height / 2f)) } else Modifier).background(Color(0xCC181818), CircleShape),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                Icon(item.icon!!, item.title)
            }
        }
    }
}

/** A thin YouTube-style bar: 3dp track, small round thumb, no stops or gaps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(player: Player, progress: Progress, onInteract: () -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = progress.durationMs.toFloat()
    val colors = SliderDefaults.colors(
        thumbColor = BrandColors.Red,
        activeTrackColor = BrandColors.Red,
        inactiveTrackColor = Color(0x66FFFFFF),
    )
    Slider(
        value = if (dragging) dragValue else progress.positionMs.toFloat().coerceIn(0f, duration),
        onValueChange = {
            onInteract()
            dragging = true
            dragValue = it
        },
        onValueChangeFinished = {
            player.seekTo(dragValue.toLong())
            dragging = false
        },
        valueRange = 0f..duration,
        colors = colors,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        thumb = {
            Canvas(Modifier.size(16.dp)) {
                drawCircle(BrandColors.Red, (if (dragging) 8.dp else 6.dp).toPx(), center)
            }
        },
        track = { sliderState ->
            // Equal 16dp layout heights give the line and thumb exactly the same center.
            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                val y = size.height / 2
                val fraction = (sliderState.value / duration).coerceIn(0f, 1f)
                drawLine(Color(0x66FFFFFF), Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
                drawLine(BrandColors.Red, Offset(0f, y), Offset(size.width * fraction, y), 3.dp.toPx(), StrokeCap.Round)
            }
        },
    )
}

@Composable
private fun ErrorOverlay(error: LoadError, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xCC000000)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(error.messageRes()), color = Color.White, style = MaterialTheme.typography.bodyMedium)
        FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun BoxScope.Hint(text: String, modifier: Modifier) {
    Text(
        stringResource(R.string.player_seconds, text),
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .padding(32.dp)
            .background(Color(0x99000000), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun LevelHint(icon: ImageVector, level: Float, modifier: Modifier) {
    Row(
        modifier
            .background(Color(0x99000000), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text("${(level * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun WhiteIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
        Icon(icon, contentDescription = description)
    }
}

/** Changes this window's brightness by [delta] (−1..1) and returns the new level 0..1. */
private fun adjustBrightness(context: Context, delta: Float): Float {
    val window = (context as? Activity)?.window ?: return 0f
    val attributes = window.attributes
    val current = attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f
    attributes.screenBrightness = (current + delta).coerceIn(0.01f, 1f)
    window.attributes = attributes
    return attributes.screenBrightness
}

private fun maxVolumeSteps(context: Context): Float =
    context.getSystemService(AudioManager::class.java)?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 1f

private fun currentVolumeSteps(context: Context): Float =
    context.getSystemService(AudioManager::class.java)?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 0f

/**
 * Sets media volume to [steps] (fractional, so small drags still add up) and returns the
 * clamped value.
 */
private fun setVolumeSteps(context: Context, steps: Float): Float {
    val audio = context.getSystemService(AudioManager::class.java) ?: return steps
    val clamped = steps.coerceIn(0f, maxVolumeSteps(context))
    if (clamped.toInt() != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, clamped.toInt(), 0)
    }
    return clamped
}

private const val SEEK_MS = 10_000L
