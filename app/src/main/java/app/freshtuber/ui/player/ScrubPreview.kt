package app.freshtuber.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.player.Chapter
import app.freshtuber.player.Storyboard
import app.freshtuber.ui.util.formatDuration
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap

/** Loaded storyboard sheets by url, kept while the player screen lives. */
class StoryboardPages {
    val pages = mutableStateMapOf<String, ImageBitmap>()
}

/**
 * The frame being scrubbed to, drawn above the controls where the finger does not cover it.
 * [fine] null hides the hint (dragging along the bar).
 */
@Composable
fun ScrubPreview(storyboard: Storyboard?, positionMs: Long, chapters: List<Chapter>, fine: Boolean?, fanHint: Boolean, pages: StoryboardPages, modifier: Modifier = Modifier) {
    val frame = storyboard?.frameAt(positionMs)
    val context = LocalContext.current
    LaunchedEffect(frame?.url) {
        val url = frame?.url ?: return@LaunchedEffect
        if (url in pages.pages) return@LaunchedEffect
        val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(url).allowHardware(false).build())
        (result as? SuccessResult)?.image?.toBitmap()?.let { pages.pages[url] = it.asImageBitmap() }
    }
    val bitmap = frame?.url?.let { pages.pages[it] }
    Column(modifier.width(152.dp).background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(14.dp)).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (frame != null) Box(Modifier.fillMaxWidth().aspectRatio(frame.width.toFloat() / frame.height).clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            if (bitmap != null) Canvas(Modifier.fillMaxSize()) {
                // Sheets can be smaller than the frame grid says; scale the cell to the real sheet.
                val sx = bitmap.width / (storyboard.perRow * frame.width).toFloat()
                val sy = bitmap.height / (storyboard.perColumn * frame.height).toFloat()
                drawImage(bitmap, srcOffset = IntOffset((frame.x * sx).toInt(), (frame.y * sy).toInt()),
                    srcSize = IntSize((frame.width * sx).toInt().coerceAtLeast(1), (frame.height * sy).toInt().coerceAtLeast(1)),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            }
        }
        val color = MaterialTheme.colorScheme.surface
        Text(formatDuration(positionMs / 1000), color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 5.dp))
        Chapter.at(chapters, positionMs / 1000)?.let {
            Text(it.title, color = color.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (fine != null) Text(stringResource(when { fine -> R.string.player_scrub_fine; fanHint -> R.string.player_scrub_hint_fan; else -> R.string.player_scrub_hint_pad }),
            color = if (fine) MaterialTheme.colorScheme.tertiary else color.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall,
            fontWeight = if (fine) FontWeight.Bold else null, textAlign = TextAlign.Center, maxLines = 2)
    }
}
