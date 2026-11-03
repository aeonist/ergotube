package app.freshtuber.ui.radial

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.freshtuber.R
import kotlin.math.*

/** Equal travel from the pressed control; pick an inward fan without moving its center. */
internal fun touchMenuPoints(width: Float, height: Float, center: ArcPoint, count: Int, size: Float): List<ArcPoint>? {
    if (count == 0) return emptyList()
    val direction = atan2(height / 2 - center.y, width / 2 - center.x)
    val directions = (0 until 360 step 5).map { Math.toRadians(it.toDouble()).toFloat() }
        .sortedBy { abs(atan2(sin(it - direction), cos(it - direction))) }
    for (radius in 112..280 step 4) for (span in listOf(180, 150, 120, 90)) {
        val radians = Math.toRadians(span.toDouble()).toFloat()
        for (middle in directions) {
            val points = (0 until count).map { i ->
                val angle = middle + if (count == 1) 0f else radians * (i.toFloat() / (count - 1) - 0.5f)
                ArcPoint(center.x + radius * cos(angle), center.y + radius * sin(angle))
            }
            if (points.all { it.x - size / 2 >= 8 && it.x + size / 2 <= width - 8 && it.y - size / 2 >= 8 && it.y + size / 2 <= height - 8 } &&
                points.zipWithNext().all { (a, b) -> max(abs(a.x - b.x), abs(a.y - b.y)) >= size + 8 }) return points
        }
    }
    return null
}

@Composable
fun TouchRadialMenu(items: List<RadialItem>, centerOnScreen: Offset, onDismiss: () -> Unit) {
    var path by remember { mutableStateOf<List<RadialItem>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    val back = stringResource(R.string.action_back)
    val previous = stringResource(R.string.radial_previous_short)
    val next = stringResource(R.string.radial_next_short)
    val close = stringResource(R.string.radial_close)
    val all = if (path.isEmpty()) items else listOf(RadialItem("up", back, Icons.AutoMirrored.Rounded.NavigateBefore) { path = path.dropLast(1); page = 0 }) + path.last().children
    val chunks = if (all.size <= 5) listOf(all) else all.chunked(3)
    val current = page.coerceIn(chunks.indices)
    val visible = buildList {
        if (current > 0) add(RadialItem("prev", previous, Icons.AutoMirrored.Rounded.NavigateBefore) { page-- })
        addAll(chunks[current])
        if (current < chunks.lastIndex) add(RadialItem("next", next, Icons.AutoMirrored.Rounded.NavigateNext) { page++ })
    }
    Popup(onDismissRequest = onDismiss, properties = PopupProperties(focusable = true, clippingEnabled = false)) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        var screenOrigin by remember { mutableStateOf<Offset?>(null) }
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { screenOrigin = it.positionOnScreen() }
            .background(Color.Black.copy(alpha = 0.72f)).clickable(onClick = onDismiss)) {
            val relative = centerOnScreen - (screenOrigin ?: centerOnScreen)
            val center = ArcPoint(relative.x / density.density, relative.y / density.density)
            val geometry = if (screenOrigin == null) null else remember(maxWidth, maxHeight, center, visible.map { it.id }) {
                (72 downTo 48).firstNotNullOfOrNull { size -> touchMenuPoints(maxWidth.value, maxHeight.value, center, visible.size, size.toFloat())?.let { size.toFloat() to it } }
            }
            fun Modifier.at(p: ArcPoint, size: Float) = offset { IntOffset(with(density) { (p.x - size / 2).dp.roundToPx() }, with(density) { (p.y - size / 2).dp.roundToPx() }) }.size(size.dp)
            if (screenOrigin != null && geometry != null) {
                val (size, points) = geometry
                visible.forEachIndexed { index, item ->
                    Surface(onClick = {
                        if (item.children.isNotEmpty()) { path = path + item; page = 0 } else item.action()
                    }, enabled = item.enabled, modifier = Modifier.at(points[index], size).semantics { contentDescription = item.title }, shape = RoundedCornerShape(20.dp),
                        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (item.enabled) 1f else 0.38f),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            item.icon?.let { Icon(it, null, Modifier.size(20.dp)) }
                            val style = MaterialTheme.typography.labelSmall
                            val longest = item.shortTitle.split(Regex("\\s+")).maxOfOrNull { measurer.measure(AnnotatedString(it), style, softWrap = false).size.width } ?: 1
                            val fit = (with(density) { (size - 8).dp.toPx() } * 0.94f / longest.coerceAtLeast(1)).coerceAtMost(1f)
                            Text(item.shortTitle, style = style.copy(fontSize = style.fontSize * fit), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                FilledIconButton(onDismiss, Modifier.at(center, 48f)) { Icon(Icons.Rounded.Close, close) }
            }
        }
    }
}
