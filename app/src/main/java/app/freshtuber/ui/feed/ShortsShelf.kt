package app.freshtuber.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.domain.model.FeedItem
import app.freshtuber.domain.model.VideoSummary
import app.freshtuber.ui.player.FlightTarget
import app.freshtuber.ui.player.LocalVideoHandoff
import app.freshtuber.ui.theme.BrandColors
import coil3.compose.AsyncImage

/** A list row: one item as it is, or a run of Shorts gathered into a sideways shelf. */
sealed interface FeedRow {
    val key: String

    data class Item(val item: FeedItem) : FeedRow {
        override val key: String get() = item.key
    }

    data class Shelf(val shorts: List<VideoSummary>) : FeedRow {
        override val key: String get() = "shelf:${shorts.first().id}"
    }
}

object FeedRows {
    /**
     * Gathers every run of at least [minRun] Shorts in a row into one shelf, as YouTube does;
     * [minRun] 0 keeps every item as it is.
     */
    fun of(items: List<FeedItem>, minRun: Int): List<FeedRow> {
        if (minRun <= 0) return items.map(FeedRow::Item)
        val rows = ArrayList<FeedRow>(items.size)
        val run = ArrayList<FeedItem.Video>()
        fun flush() {
            if (run.size >= minRun) rows += FeedRow.Shelf(run.map { it.video })
            else run.forEach { rows += FeedRow.Item(it) }
            run.clear()
        }
        for (item in items) {
            if (item is FeedItem.Video && item.video.isShort) run += item
            else { flush(); rows += FeedRow.Item(item) }
        }
        flush()
        return rows.distinctBy { it.key }
    }
}

/** A sideways shelf of Shorts: tall cards with the title on the picture; a tap opens them as Shorts. */
@Composable
fun ShortsShelf(shorts: List<VideoSummary>, navigation: FeedNavigation, modifier: Modifier = Modifier) {
    val handoff = LocalVideoHandoff.current
    val radius = with(LocalDensity.current) { 16.dp.toPx() }
    val light = app.freshtuber.ui.liquid.LocalLiquidLight.current
    Column(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Slideshow, null, Modifier.size(22.dp), tint = BrandColors.Red)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.nav_shorts), style = MaterialTheme.typography.titleMedium)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(shorts, key = { _, short -> short.id }) { index, short ->
                val rect = remember { arrayOf(Rect.Zero) }
                val loaded = remember { arrayOfNulls<coil3.Image>(1) }
                val lightKey = remember { Any() }
                DisposableEffect(light, lightKey) { onDispose { light?.removeCard(lightKey) } }
                Box(
                    Modifier
                        .width(150.dp)
                        .aspectRatio(9f / 16f)
                        .onGloballyPositioned { c ->
                            val p = c.positionInRoot()
                            rect[0] = Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height)
                            light?.reportCard(lightKey, short.thumbnailUrl, rect[0])
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .semantics { contentDescription = short.title }
                        .clickable {
                            if (rect[0].width > 1f) handoff?.launch(short.id, app.freshtuber.ui.player.flightPicture(loaded[0], short.thumbnailUrl), rect[0], radius, FlightTarget.Short, short.thumbnailUrl)
                            navigation.openShorts(shorts.map { it.id }, shorts.map { it.thumbnailUrl.orEmpty() }, index)
                        },
                ) {
                    AsyncImage(short.thumbnailUrl, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        onSuccess = { loaded[0] = it.result.image })
                    Column(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                            .padding(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 10.dp),
                    ) {
                        Text(short.title, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        short.detailText?.let {
                            Text(it, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    short.watchedPercent?.takeIf { it > 0 }?.let { percent ->
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(percent.coerceIn(0, 100) / 100f).height(3.dp).background(BrandColors.Red))
                    }
                }
            }
        }
    }
}
