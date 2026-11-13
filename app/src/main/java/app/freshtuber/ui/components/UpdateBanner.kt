package app.freshtuber.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.freshtuber.R
import app.freshtuber.data.update.AppUpdate
import app.freshtuber.ui.motion.Motion
import app.freshtuber.ui.motion.blurFade
import kotlin.math.roundToInt

/**
 * «Доступно обновление» above the dock. «Позже» blurs the banner away while a half-transparent
 * chip comes into focus at the hand-side edge: still one tap away, never in the way. A tap on
 * the chip blurs the banner back in.
 */
@Composable
fun UpdateBanner(update: AppUpdate, collapsed: Boolean, left: Boolean, onUpdate: () -> Unit, onCollapse: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val fold by animateFloatAsState(if (collapsed) 1f else 0f, tween(if (collapsed) 420 else 380, easing = Motion.Emphasized), label = "updateFold")
    val label = if (update.build > 0) stringResource(R.string.update_available, update.build) else stringResource(R.string.update_available_plain)
    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // The chip sits a little above the banner's place, at the edge under the hand.
        if (fold > 0.01f) Box(Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd).offset { IntOffset(0, (-100).dp.roundToPx()) }
            .size(48.dp).blurFade({ fold }, max = 12.dp, fromScale = 0.7f)
            .graphicsLayer { alpha = 0.55f; shape = RoundedCornerShape(24.dp); clip = true }
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (collapsed) Modifier.clickable { onCollapse(false) }.semantics { contentDescription = label } else Modifier)) {
            Icon(Icons.Rounded.SystemUpdate, null, Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.onSurface)
        }
        if (fold < 0.99f) Row(Modifier.fillMaxWidth().height(64.dp).blurFade({ 1f - fold }, max = 18.dp, fromScale = 0.96f)
            .graphicsLayer { shadowElevation = 6.dp.toPx(); shape = RoundedCornerShape(20.dp); clip = true }
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (update.name.isNotBlank()) Text(update.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                }
                TextButton(onClick = { onCollapse(true) }, enabled = !collapsed, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.update_later)) }
                FilledTonalButton(onClick = onUpdate, enabled = !collapsed, modifier = Modifier.heightIn(min = 40.dp)) { Text(stringResource(R.string.update_install)) }
        }
    }
}
