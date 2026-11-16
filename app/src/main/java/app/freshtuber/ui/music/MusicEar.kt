package app.freshtuber.ui.music

import app.freshtuber.ui.motion.blurFade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import app.freshtuber.R

/** Height reserved for the «Музыка» ear above the dock. */
val MusicEarHeight = 40.dp

/**
 * The «Музыка» ear above the dock on Home. It blurs away while the feed is read and comes
 * back into focus when the feed scrolls up. It sits on the working-hand side.
 */
@Composable
fun MusicEar(visible: Boolean, left: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val appear by animateFloatAsState(if (visible) 1f else 0f, tween(220), label = "musicEar")
    Box(modifier.fillMaxWidth().height(MusicEarHeight).padding(horizontal = 14.dp), contentAlignment = if (left) Alignment.BottomStart else Alignment.BottomEnd) {
        if (appear > 0.01f) Surface(onClick = onClick, enabled = visible, shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.blurFade({ appear }, max = 14.dp, fromScale = 0.9f)) {
            Row(Modifier.height(34.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Headphones, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nav_music), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * The «Музыка» pill at the hand end of Home's filter row: chip height, so the bottom bar stays
 * one row tall.
 */
@Composable
fun MusicChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.testTag("home:music")) {
        Row(Modifier.height(40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Headphones, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.nav_music), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
