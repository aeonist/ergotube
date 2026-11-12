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
import kotlin.math.roundToInt

/**
 * «Доступно обновление» above the dock. «Позже» shrinks it, along a short arc, into a
 * half-transparent chip at the hand-side edge: still one tap away, never in the way. A tap on
 * the chip unfolds the banner again.
 */
@Composable
fun UpdateBanner(update: AppUpdate, collapsed: Boolean, left: Boolean, onUpdate: () -> Unit, onCollapse: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val fold by animateFloatAsState(if (collapsed) 1f else 0f, if (collapsed) tween(520, easing = Motion.Emphasized) else Motion.settle(), label = "updateFold")
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val chip = 48.dp
        val width = lerp(maxWidth, chip, fold)
        val height = lerp(64.dp, chip, fold)
        val density = LocalDensity.current
        // The chip rises a little above the banner's place while it travels to the edge.
        val shiftX = with(density) { (if (left) 0.dp else maxWidth - width).toPx() * fold }
        val lift = with(density) { (-28).dp.toPx() * fold - 18.dp.toPx() * kotlin.math.sin(fold * Math.PI).toFloat() }
        val corner = lerp(20.dp, chip / 2, fold)
        val label = stringResource(R.string.update_available, update.build)
        Box(Modifier.offset { IntOffset(shiftX.roundToInt(), lift.roundToInt()) }.size(width, height)
            .graphicsLayer { alpha = 1f - 0.45f * fold; shadowElevation = 6.dp.toPx() * (1f - fold); shape = RoundedCornerShape(corner); clip = true }
            .background(lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHighest, fold))
            .then(if (collapsed) Modifier.clickable { onCollapse(false) }.semantics { contentDescription = label } else Modifier)) {
            if (fold > 0.5f) Icon(Icons.Rounded.SystemUpdate, null, Modifier.align(Alignment.Center).graphicsLayer { alpha = (fold - 0.5f) * 2f },
                tint = MaterialTheme.colorScheme.onSurface)
            if (fold < 0.5f) Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 6.dp).graphicsLayer { alpha = 1f - fold * 2f },
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (update.name.isNotBlank()) Text(update.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                }
                TextButton(onClick = { onCollapse(true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.update_later)) }
                FilledTonalButton(onClick = onUpdate, modifier = Modifier.heightIn(min = 40.dp)) { Text(stringResource(R.string.update_install)) }
            }
        }
    }
}
