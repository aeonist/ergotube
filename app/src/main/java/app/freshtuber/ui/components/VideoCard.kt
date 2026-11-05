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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
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
    /** Recommendations can be hidden with Not interested; saved lists and search cannot. */
    recommendation: Boolean = enableMenu,
) {
    val effectiveBranding = if (app.freshtuber.ui.theme.LocalAppPreferences.current.enabled(app.freshtuber.data.settings.AppFeature.DeArrow)) branding else null
    var hidden by remember(video.id) { mutableStateOf(false) }
    var menuOpen by remember(video.id) { mutableStateOf(false) }
    val feedActions: app.freshtuber.ui.feed.FeedVideoActions? = if (enableMenu) androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel() else null
    val hiddenIds = if (recommendation) feedActions?.hiddenVideos?.collectAsStateWithLifecycle()?.value.orEmpty() else emptySet()
    val act = app.freshtuber.ui.feed.rememberCardActions(video, feedActions) { hidden = it }
    if (hidden || video.id in hiddenIds) return
    if (menuOpen) app.freshtuber.ui.feed.FeedVideoMenu(video, recommendation, act, onChannelClick) { menuOpen = false }
    val fan = app.freshtuber.ui.feed.LocalFanHost.current
    var coordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    // Where the thumbnail is on screen, so a tap sends it flying into the player.
    val thumbRect = remember { arrayOf(androidx.compose.ui.geometry.Rect.Zero) }
    val handoff = app.freshtuber.ui.player.LocalVideoHandoff.current
    val thumbRadius = with(androidx.compose.ui.platform.LocalDensity.current) { 20.dp.toPx() }
    val thumbnailForFlight = effectiveBranding?.thumbnailUrl ?: video.thumbnailUrl
    val loaded = remember { arrayOfNulls<coil3.Image>(1) }
    val open = {
        if (handoff != null && thumbRect[0].width > 1f && !video.isShort)
            handoff.launch(video.id, app.freshtuber.ui.player.flightPicture(loaded[0], thumbnailForFlight), thumbRect[0], thumbRadius,
                app.freshtuber.ui.player.FlightTarget.Page, thumbnailForFlight)
        onClick()
    }
    val available = remember(recommendation) {
        app.freshtuber.ui.feed.FanAction.entries.filter { recommendation || it != app.freshtuber.ui.feed.FanAction.NotInterested }.toSet()
    }
    Column(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates = it }
            .then(if (enableMenu && fan != null) Modifier.pointerInput(video.id, available) {
                // Hold, then flick toward an action; hold and release still for the menu.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val long = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    val coords = coordinates ?: return@awaitEachGesture
                    fan.start(coords.localToRoot(long.position), available)
                    long.consume()
                    var released = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == long.id } ?: break
                        change.consume()
                        if (!change.pressed) { released = true; break }
                        fan.move(coords.localToRoot(change.position))
                    }
                    when (val result = fan.finish(cancelled = !released)) {
                        is app.freshtuber.ui.feed.FanResult.Picked -> act(result.action)
                        app.freshtuber.ui.feed.FanResult.Still -> menuOpen = true
                        app.freshtuber.ui.feed.FanResult.None -> Unit
                    }
                }
            } else Modifier)
            .clickable(onClick = open)
            .padding(top = 4.dp, bottom = 20.dp),
    ) {
        val light = app.freshtuber.ui.liquid.LocalLiquidLight.current
        val lightKey = remember { Any() }
        val thumbnail = effectiveBranding?.thumbnailUrl ?: video.thumbnailUrl
        DisposableEffect(light, lightKey) { onDispose { light?.removeCard(lightKey) } }
        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                // The card's thumbnail lights the background while it is in focus.
                .onGloballyPositioned { c ->
                    val p = c.positionInRoot()
                    val rect = androidx.compose.ui.geometry.Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height)
                    thumbRect[0] = rect
                    light?.reportCard(lightKey, thumbnail, rect)
                }
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { loaded[0] = it.result.image },
            )
            ThumbnailBadge(video, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            video.watchedPercent?.takeIf { it > 0 }?.let { WatchedBar(it, Modifier.align(Alignment.BottomStart)) }
        }
        val left = LocalLeftHanded.current
        val menuButton: @Composable () -> Unit = {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.MoreVert, stringResource(R.string.video_more))
            }
        }
        Row(Modifier.padding(start = if (enableMenu && left) 0.dp else 12.dp, end = 16.dp, top = 12.dp)) {
            // The menu button sits on the working-hand side.
            if (enableMenu && left) menuButton()
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
                    style = app.freshtuber.ui.theme.AppType.cardTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaLine(video, showChannel, Modifier.padding(top = 4.dp))
            }
            if (enableMenu && !left) menuButton()
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
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = app.freshtuber.ui.theme.TabularNumbers),
        color = BrandColors.BadgeText,
        modifier = modifier
            .background(background, RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
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
        style = app.freshtuber.ui.theme.AppType.meta,
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
