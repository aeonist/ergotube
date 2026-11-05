package app.freshtuber.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.freshtuber.ui.util.formatDuration

@Composable
fun PlaybackTime(progress: Progress, seekMs: Long?, modifier: Modifier = Modifier) {
    if (progress.durationMs <= 0) return
    Text("${formatDuration((seekMs ?: progress.positionMs) / 1000)} / ${formatDuration(progress.durationMs / 1000)}",
        color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = app.freshtuber.ui.theme.TabularNumbers),
        modifier = modifier.testTag("video:time").background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
}
