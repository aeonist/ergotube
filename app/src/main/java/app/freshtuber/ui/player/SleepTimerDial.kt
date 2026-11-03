package app.freshtuber.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.player.SleepTimer
import app.freshtuber.ui.radial.ThumbArc
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A mechanical dial whose ellipse follows the saved thumb-calibration aspect ratio. */
@Composable
internal fun SleepTimerDial(
    current: SleepTimer,
    arc: ThumbArc,
    leftHanded: Boolean,
    onBack: () -> Unit,
    onSet: (SleepTimer) -> Unit,
) {
    BackHandler(onBack = onBack)
    var minutes by remember { mutableFloatStateOf((current as? SleepTimer.Minutes)?.value?.toFloat() ?: 30f) }
    val value = minutes.roundToInt()
    val label = stringResource(R.string.player_sleep_minutes, value)
    val title = stringResource(R.string.player_sleep)
    val safe = arc.normalized()
    val haptics = LocalHapticFeedback.current
    val dialColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val rim = MaterialTheme.colorScheme.outline
    val ink = MaterialTheme.colorScheme.onSurface
    val background = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()
    val tickStyle = MaterialTheme.typography.labelSmall.copy(color = ink)
    val change: (Float) -> Unit = { next ->
        if (next.roundToInt() != minutes.roundToInt()) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        minutes = next.coerceIn(1f, 180f)
    }
    val latestChange by rememberUpdatedState(change)
    val latestMinutes by rememberUpdatedState(minutes)
    val dial: @Composable (Modifier) -> Unit = { modifier ->
        BoxWithConstraints(modifier, contentAlignment = if (leftHanded) Alignment.BottomStart else Alignment.BottomEnd) {
            val ratio = safe.radius / safe.verticalRadius
            val height = minOf(maxHeight, maxWidth / ratio, safe.verticalRadius.dp)
            val width = height * ratio
            Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().semantics {
                    contentDescription = title
                    stateDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 1f..180f, 178)
                    setProgress { target -> latestChange(target); true }
                }.pointerInput(width, height) {
                    var previous = 0f
                    fun angle(p: Offset) = SleepDialMath.angle(p.x - size.width / 2f, p.y - size.height / 2f,
                        size.width / 2f, size.height / 2f)
                    detectDragGestures(
                        onDragStart = { previous = angle(it) },
                        onDrag = { event, _ ->
                            event.consume()
                            val next = angle(event.position)
                            latestChange(SleepDialMath.turn(latestMinutes, previous, next))
                            previous = next
                        },
                    )
                }) {
                    val inset = 12.dp.toPx()
                    val rx = size.width / 2 - inset
                    val ry = size.height / 2 - inset
                    val box = Size(rx * 2, ry * 2)
                    drawOval(dialColor, topLeft = center - Offset(rx, ry), size = box)
                    drawOval(rim, topLeft = center - Offset(rx, ry), size = box, style = Stroke(2.dp.toPx()))
                    repeat(60) { index ->
                        val angle = (index + minutes) * (2 * PI / 60) - PI / 2
                        val long = index % 5 == 0
                        val length = (if (long) 16.dp else 8.dp).toPx()
                        drawLine(if (long) ink else rim,
                            center + Offset((rx - 5.dp.toPx()) * cos(angle).toFloat(), (ry - 5.dp.toPx()) * sin(angle).toFloat()),
                            center + Offset((rx - length - 5.dp.toPx()) * cos(angle).toFloat(), (ry - length - 5.dp.toPx()) * sin(angle).toFloat()),
                            (if (long) 2.dp else 1.dp).toPx())
                    }
                    if (size.width >= 200.dp.toPx()) listOf(0, 15, 30, 45).forEach { minute ->
                        val angle = (minutes - minute) * (2 * PI / 60) - PI / 2
                        val text = textMeasurer.measure(minute.toString(), tickStyle)
                        val position = center + Offset((rx - 36.dp.toPx()) * cos(angle).toFloat(), (ry - 36.dp.toPx()) * sin(angle).toFloat())
                        drawText(text, topLeft = position - Offset(text.size.width / 2f, text.size.height / 2f))
                    }
                    // Fixed index, with the numbered dial turning beneath it.
                    drawLine(ink, Offset(center.x, center.y - ry - 7.dp.toPx()),
                        Offset(center.x, center.y - ry + 20.dp.toPx()), 4.dp.toPx())
                }
                Text(label, style = if (width < 180.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.background(dialColor).padding(4.dp).clearAndSetSemantics {})
            }
        }
    }
    val controls: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.player_sleep_dial_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(onClick = { change(minutes - 1) }, enabled = value > 1, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Remove, stringResource(R.string.player_sleep_less))
                }
                IconButton(onClick = { change(minutes + 1) }, enabled = value < 180, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.player_sleep_more))
                }
            }
            Button(onClick = { onSet(SleepTimer.Minutes(value)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.player_sleep_start))
            }
            TextButton(onClick = { onSet(SleepTimer.EndOfVideo) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.player_sleep_end))
            }
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_back))
            }
            if (current != SleepTimer.Off) TextButton(onClick = { onSet(SleepTimer.Off) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.player_sleep_stop))
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(background).pointerInput(Unit) { detectTapGestures {} }
        .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.safeGestures)).padding(12.dp)) {
        val wide = maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            if (wide) {
                val thumbControls: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dial(Modifier.weight(1f).fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            IconButton(onClick = { change(minutes - 1) }, enabled = value > 1, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.Remove, stringResource(R.string.player_sleep_less))
                            }
                            IconButton(onClick = { change(minutes + 1) }, enabled = value < 180, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.Add, stringResource(R.string.player_sleep_more))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                            }
                            Button(onClick = { onSet(SleepTimer.Minutes(value)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.player_sleep_start))
                            }
                        }
                    }
                }
                val secondary: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.player_sleep_dial_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { onSet(SleepTimer.EndOfVideo) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.player_sleep_end))
                        }
                        if (current != SleepTimer.Off) TextButton(onClick = { onSet(SleepTimer.Off) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.player_sleep_stop))
                        }
                    }
                }
                Row(Modifier.weight(1f).widthIn(max = (safe.radius + 180f).dp)
                    .align(if (leftHanded) Alignment.Start else Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                    if (leftHanded) thumbControls(Modifier.weight(1f)) else secondary(Modifier.weight(1f))
                    if (leftHanded) secondary(Modifier.weight(1f)) else thumbControls(Modifier.weight(1f))
                }
            } else {
                dial(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp))
                controls(Modifier.fillMaxWidth())
            }
        }
    }
}
