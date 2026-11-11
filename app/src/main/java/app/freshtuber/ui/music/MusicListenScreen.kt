package app.freshtuber.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.freshtuber.R
import app.freshtuber.data.music.MusicTrack
import app.freshtuber.ui.components.MessageState
import app.freshtuber.ui.components.ScreenHeader
import app.freshtuber.ui.util.formatDuration
import java.time.LocalDateTime
import java.time.format.TextStyle

/**
 * «Слушать», the first music screen. Read from the top like Home: the daily mix, the song
 * the listener stopped at, jams from their YouTube Home and recent songs.
 */
@Composable
fun MusicListenScreen(viewModel: MusicViewModel = hiltViewModel()) {
    MusicNotes(viewModel)
    LaunchedEffect(Unit) { viewModel.loadHome() }
    val home by viewModel.home.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    val navigation = LocalMusicNavigation.current
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.music_share)
    val dailyTitle = stringResource(R.string.music_daily_mix)
    var menu by remember { mutableStateOf<MusicTrack?>(null) }
    menu?.let { TrackMenu(it, viewModel, onDismiss = { menu = null }) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "header") { ScreenHeader(stringResource(R.string.music_listen_title), dayPart()) }
        item(key = "daily") {
            DailyMix(home, downloaded.keys, onPlay = { viewModel.playDaily(dailyTitle); navigation?.openPlayer() },
                onShuffle = { viewModel.playDaily(dailyTitle, shuffle = true); navigation?.openPlayer() })
        }
        val current = state.queue.track
        if (current != null && !state.playing) item(key = "continue") {
            Label(stringResource(R.string.music_continue))
            val position = state.queue.positionMs
            TrackRow(current, originOf(current, downloaded), onClick = { viewModel.music.play(); navigation?.openPlayer() }, onMore = { menu = current },
                detail = if (current.durationMs > 0) stringResource(R.string.music_position, formatDuration(position / 1000), formatDuration(current.durationMs / 1000)) else null,
                trailing = { IconButton(onClick = { viewModel.music.play() }) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.player_play)) } })
        }
        if (home.jams.isNotEmpty()) item(key = "jams") {
            Label(stringResource(if (home.signedIn) R.string.music_jams else R.string.music_jams_anon))
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(home.jams, key = { it.id }) { jam ->
                    Column(Modifier.width(150.dp).clip(RoundedCornerShape(16.dp)).clickable { viewModel.playJam(jam); navigation?.openPlayer() }.padding(4.dp)) {
                        Cover(jam.thumbnailUrl, Modifier.size(142.dp).lightSource(jam.thumbnailUrl), corner = 14.dp)
                        Text(jam.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        (jam.channelName ?: jam.videoCountText)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "recentLabel") { Label(stringResource(R.string.music_recent)) }
            items(recent.size, key = { "r" + recent[it].id }) { index ->
                val track = recent[index]
                TrackRow(track, originOf(track, downloaded), current = track.id == current?.id,
                    onClick = { viewModel.music.playCollection(recent, index, null); navigation?.openPlayer() },
                    onMore = { menu = track }, onFlick = { viewModel.flick(it, track, context, shareTitle) },
                    modifier = Modifier.lightSource(track.artwork))
            }
        }
        if (recent.isEmpty() && home.daily == null && !home.dailyLoading && current == null) item(key = "empty") {
            Box(Modifier.height(240.dp)) { MessageState(stringResource(R.string.music_listen_empty)) }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp))
}

/** «Вторник, вечер». */
@Composable
private fun dayPart(): String {
    val now = remember { LocalDateTime.now() }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val day = now.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
    val part = stringResource(when (now.hour) {
        in 5..11 -> R.string.music_part_morning
        in 12..16 -> R.string.music_part_day
        in 17..22 -> R.string.music_part_evening
        else -> R.string.music_part_night
    })
    return stringResource(R.string.music_day_part, day, part)
}

/** The daily mix: a 2×2 collage of its covers, what it is, and a big Play under the thumb. */
@Composable
private fun DailyMix(home: HomeMusic, downloaded: Set<String>, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val mix = home.daily
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val covers = mix?.tracks.orEmpty().mapNotNull { it.artwork }.distinct().take(4)
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(enabled = mix != null, onClick = onPlay)) {
            if (covers.isEmpty()) {
                if (home.dailyLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else Column(Modifier.fillMaxSize()) {
                covers.chunked(2).forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        row.forEach { url -> Cover(url, Modifier.weight(1f).fillMaxHeight().lightSource(url), corner = 0.dp) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.music_daily_mix), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                if (mix != null) {
                    val all = mix.tracks.isNotEmpty() && mix.tracks.all { it.videoId in downloaded }
                    val meta = songsAndMinutes(mix.tracks.size, mix.tracks.sumOf { it.durationMs }, known = mix.tracks.all { it.durationMs > 0 }) +
                        if (all) " · " + stringResource(R.string.music_downloaded_mark) else ""
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(if (mix.personalized) R.string.music_daily_personal else R.string.music_daily_popular),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onShuffle, enabled = mix != null) { Icon(Icons.Rounded.Shuffle, stringResource(R.string.music_shuffle)) }
            Spacer(Modifier.width(4.dp))
            FilledIconButton(onClick = onPlay, enabled = mix != null, modifier = Modifier.size(56.dp), shape = CircleShape) {
                Icon(Icons.Rounded.PlayArrow, stringResource(R.string.music_play_all), Modifier.size(32.dp))
            }
        }
    }
}
