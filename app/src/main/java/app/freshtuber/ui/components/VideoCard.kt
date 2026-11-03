package app.freshtuber.ui.components

import android.icu.text.CompactDecimalFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.data.extras.Branding
import app.freshtuber.domain.model.VideoSummary
import app.freshtuber.ui.theme.BrandColors
import app.freshtuber.ui.util.formatDuration
import app.freshtuber.ui.util.relativeTimeText
import coil3.compose.AsyncImage

/** Full-width feed card: 16:9 thumbnail, then channel avatar, title and one meta line. */
@Composable
fun VideoCard(
    video: VideoSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    branding: Branding? = null,
    onChannelClick: ((String) -> Unit)? = null,
    showChannel: Boolean = true,
    enableMenu: Boolean = false,
) {
    val effectiveBranding = if (app.freshtuber.ui.theme.LocalAppPreferences.current.enabled(app.freshtuber.data.settings.AppFeature.DeArrow)) branding else null
    var hidden by remember(video.id) { mutableStateOf(false) }
    var menuAnchor by remember(video.id) { mutableStateOf<Offset?>(null) }
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }
    val feedActions: app.freshtuber.ui.feed.FeedVideoActions? = if (enableMenu) androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel() else null
    val hiddenIds = feedActions?.hiddenVideos?.collectAsStateWithLifecycle()?.value.orEmpty()
    if (hidden || video.id in hiddenIds) return
    menuAnchor?.let { app.freshtuber.ui.feed.FeedVideoMenu(video, it, { hidden = true; feedActions?.hideVideo(video.id) }, { menuAnchor = null }) }
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 4.dp, bottom = 20.dp),
    ) {
        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = effectiveBranding?.thumbnailUrl ?: video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            ThumbnailBadge(video, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            video.watchedPercent?.takeIf { it > 0 }?.let { WatchedBar(it, Modifier.align(Alignment.BottomStart)) }
        }
        Row(Modifier.padding(start = 12.dp, end = 16.dp, top = 12.dp)) {
            if (showChannel && video.channelAvatarUrl != null) {
                val channelId = video.channelId
                val openChannel = if (channelId != null && onChannelClick != null) {
                    Modifier.clickable { onChannelClick(channelId) }
                } else {
                    Modifier
                }
                AsyncImage(
                    model = video.channelAvatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .then(openChannel),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    effectiveBranding?.title ?: video.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaLine(video, showChannel, Modifier.padding(top = 4.dp))
            }
            if (enableMenu) IconButton(onClick = { menuAnchor = buttonCenter }, modifier = Modifier.size(48.dp).onGloballyPositioned { buttonCenter = it.positionOnScreen() + Offset(it.size.width / 2f, it.size.height / 2f) }) {
                Icon(Icons.Rounded.MoreVert, stringResource(R.string.video_more))
            }
        }
    }
}

@Composable
private fun WatchedBar(percent: Int, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color(0x99FFFFFF)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .background(BrandColors.Red),
        )
    }
}

@Composable
private fun ThumbnailBadge(video: VideoSummary, modifier: Modifier) {
    val (text, background) = when {
        video.isLive -> stringResource(R.string.search_live) to BrandColors.Red
        video.durationText != null -> video.durationText to BrandColors.BadgeBackground
        video.durationSeconds != null -> formatDuration(video.durationSeconds) to BrandColors.BadgeBackground
        else -> return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = BrandColors.BadgeText,
        modifier = modifier
            .background(background, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun MetaLine(video: VideoSummary, showChannel: Boolean, modifier: Modifier) {
    val parts = buildList {
        if (showChannel) video.channelName?.let(::add)
        if (video.detailText != null) {
            add(video.detailText)
        } else {
            video.viewCount?.let { add(viewCountText(it, live = video.isLive)) }
            video.uploadedAtMillis?.let { add(relativeTimeText(it)) }
        }
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** "312 тыс. просмотров" / "312K views" (or viewers for live), with the right plural form. */
@Composable
fun viewCountText(count: Long, live: Boolean = false): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatted = remember(count, locale) { compactNumber(count, locale) }
    // Compact numbers ("312K", "1,1 млн") always take the plural form used for large counts.
    val quantity = if (count < 1000) count.toInt() else 1000
    val plural = if (live) R.plurals.watching_count else R.plurals.views_count
    return pluralStringResource(plural, quantity, formatted)
}

fun compactNumber(count: Long, locale: java.util.Locale): String =
    CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(count)
