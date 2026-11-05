package app.freshtuber.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.freshtuber.R
import app.freshtuber.player.PlayerController
import app.freshtuber.player.PlayerState

val LocalQualityAnchor = staticCompositionLocalOf<(Rect) -> Unit> { {} }

fun Modifier.qualityAnchor(): Modifier = composed {
    val report = LocalQualityAnchor.current
    onGloballyPositioned { report(it.boundsInRoot()) }
}

/** A thumb-sized palette opens beside HQ in either orientation, never a settings list. */
@Composable
fun QualityChooser(state: PlayerState, controller: PlayerController, anchor: Rect?, fullscreen: Boolean, onDismiss: () -> Unit) {
    val now = state.nowPlaying ?: return
    val density = LocalDensity.current
    val margin = with(density) { 16.dp.roundToPx() }
    val position = remember(anchor, margin) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            val point = anchor ?: Rect(windowSize.width * 0.5f, windowSize.height * 0.8f, windowSize.width * 0.5f, windowSize.height * 0.8f)
            val x = (point.center.x - popupContentSize.width / 2).toInt().coerceIn(margin, maxOf(margin, windowSize.width - popupContentSize.width - margin))
            val above = point.top.toInt() - popupContentSize.height - margin
            val y = (if (above >= margin) above else point.bottom.toInt() + margin)
                .coerceIn(margin, maxOf(margin, windowSize.height - popupContentSize.height - margin))
            return IntOffset(x, y)
        }
    } }
    val phase = remember { Animatable(0f) }
    LaunchedEffect(Unit) { phase.animateTo(1f, tween(220, easing = app.freshtuber.ui.motion.Motion.Decelerate)) }
    val auto = stringResource(R.string.player_quality_auto)
    val options = buildList {
        if (!now.isShort && !now.isOffline && !now.isLive) add(Triple(auto, now.qualityAuto, -1))
        now.qualities.forEachIndexed { i, track -> add(Triple(track.label, !now.qualityAuto && i == now.qualityIndex, i)) }
    }
    val scheme = MaterialTheme.colorScheme
    val foreground = if (fullscreen) Color.White else scheme.onSurface
    // Same glass as the remote: translucent, hairline edge, chosen pill in the remote's accent.
    val panel = if (fullscreen) Color(0xE0141414) else scheme.surfaceContainerHigh.copy(alpha = 0.97f)
    val edge = foreground.copy(alpha = if (fullscreen) 0.16f else 0.10f)
    val chosen = scheme.primary
    val onChosen = scheme.onPrimary
    val autoOption = options.firstOrNull { it.third < 0 }
    val fixed = options.filter { it.third >= 0 }
    val playing = now.qualities.getOrNull(now.qualityIndex)?.label.orEmpty()
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(Modifier.width(minOf(340.dp, LocalConfiguration.current.screenWidthDp.dp - 32.dp)).testTag("quality:palette")
            .graphicsLayer {
                alpha = phase.value
                scaleX = 0.92f + phase.value * 0.08f; scaleY = scaleX
                translationY = (1f - phase.value) * 12.dp.toPx()
                val blur = (1f - phase.value) * 8.dp.toPx()
                renderEffect = if (android.os.Build.VERSION.SDK_INT >= 31 && blur > 0.5f) androidx.compose.ui.graphics.BlurEffect(blur, blur) else null
            }, shape = RoundedCornerShape(30.dp), color = panel, contentColor = foreground, shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, edge)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.padding(start = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.HighQuality, null, Modifier.size(20.dp), tint = foreground.copy(alpha = 0.8f))
                    Text(stringResource(R.string.player_quality), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (playing.isNotEmpty()) Text(playing, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = app.freshtuber.ui.theme.TabularNumbers),
                        color = foreground.copy(alpha = 0.75f),
                        modifier = Modifier.background(foreground.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                @Composable
                fun Pill(label: String, active: Boolean, index: Int, modifier: Modifier) {
                    Surface(onClick = { if (index < 0) controller.selectAutoQuality() else controller.selectQuality(index); onDismiss() },
                        modifier = modifier.height(46.dp).semantics { selected = active }, shape = RoundedCornerShape(23.dp),
                        color = if (active) chosen else foreground.copy(alpha = 0.07f), contentColor = if (active) onChosen else foreground,
                        border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, edge)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                            if (active) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = app.freshtuber.ui.theme.TabularNumbers), maxLines = 1)
                        }
                    }
                }
                autoOption?.let { (label, active, index) -> Pill(label, active, index, Modifier.fillMaxWidth()) }
                BoxWithConstraints {
                    val width = (maxWidth - 17.dp) / 3
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                        fixed.forEach { (label, active, index) -> Pill(label, active, index, Modifier.width(width)) }
                    }
                }
            }
        }
    }
}
