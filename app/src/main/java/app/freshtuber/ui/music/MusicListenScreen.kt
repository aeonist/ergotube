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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.rounded.AllInclusive
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
 * The one «Музыка» page, read from the top like Home: jams from the Music filter, jams from the
 * Home page, the «Моё» row (Любимое, playlists, + Плейлист), and at the thumb the two things that
 * play at once: «Микс дня» and «Мой джем». Without a network the jams pale and cannot start.
 */
@Composable
fun MusicListenScreen(viewModel: MusicViewModel = hiltViewModel()) {
    MusicNotes(viewModel)
    MusicFolderAccess(viewModel)
    LaunchedEffect(Unit) { viewModel.loadHome() }
    val home by viewModel.home.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val libraryIds by viewModel.libraryIds.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val navigation = LocalMusicNavigation.current
    val left = app.freshtuber.ui.components.LocalLeftHanded.current
    val dailyTitle = stringResource(R.string.music_daily_mix)
    val jamTitle = stringResource(R.string.music_my_jam)
    var newPlaylist by remember { mutableStateOf(false) }
    if (newPlaylist) PlaylistPicker(null, viewModel) { newPlaylist = false }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "header") { ScreenHeader(stringResource(R.string.nav_music), dayPart()) }
        // Two labelled shelves of jams; both stay, each says where YouTube showed it.
        listOf(
            Triple("jamsMusic", home.jams.music, R.string.music_jams_music),
            Triple("jamsHome", home.jams.home, if (home.signedIn) R.string.music_jams else R.string.music_jams_anon),
        ).forEach { (key, jams, label) ->
            if (jams.isNotEmpty()) item(key = key) {
                Label(stringResource(label))
                LazyRow(Modifier.alpha(if (online) 1f else 0.38f), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(jams, key = { it.id }) { jam ->
                        Column(Modifier.width(150.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = online) { viewModel.playJam(jam); navigation?.openPlayer() }.padding(4.dp)) {
                            Cover(jam.thumbnailUrl, Modifier.size(142.dp).lightSource(jam.thumbnailUrl), corner = 14.dp)
                            Text(jam.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            (jam.channelName ?: jam.videoCountText)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        item(key = "mine") {
            Label(stringResource(R.string.music_mine))
            MineRow(libraryIds.size, playlists, left, onOpen = { navigation?.openCollection(it) }, onNewPlaylist = { newPlaylist = true })
        }
        item(key = "playNow") {
            Label(stringResource(R.string.music_play_now))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val tiles: List<@Composable RowScope.() -> Unit> = listOf(
                    { DailyTile(home, online, Modifier.weight(1f), onPlay = { viewModel.playDaily(dailyTitle); navigation?.openPlayer() },
                        onShuffle = { viewModel.playDaily(dailyTitle, shuffle = true); navigation?.openPlayer() }) },
                    { JamTile(home.jams.mine, jamTitle, online, Modifier.weight(1f)) { mine -> viewModel.playJam(mine.copy(title = jamTitle)); navigation?.openPlayer() } },
                )
                // The tile of the working hand sits nearest the thumb.
                (if (left) tiles else tiles.reversed()).forEach { it() }
            }
        }
        if (home.daily == null && !home.dailyLoading && home.jams.isEmpty && !online) item(key = "empty") {
            Box(Modifier.height(160.dp)) { MessageState(stringResource(R.string.music_listen_empty)) }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp))
}

/** «Моё»: «Любимое», the playlists, then «+ Плейлист». */
@Composable
private fun MineRow(favorites: Int, playlists: List<app.freshtuber.data.music.PlaylistCount>, left: Boolean,
    onOpen: (String) -> Unit, onNewPlaylist: () -> Unit) {
    data class Tile(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val count: String, val onClick: () -> Unit)
    val tiles = listOf(Tile(Icons.Rounded.Favorite, stringResource(R.string.music_favorites), favorites.toString()) { onOpen("fav") }) +
        playlists.map { p -> Tile(Icons.AutoMirrored.Rounded.QueueMusic, p.name, p.count.toString()) { onOpen("playlist:${p.id}") } } +
        Tile(Icons.Rounded.Add, stringResource(R.string.music_playlist_add), " ") { onNewPlaylist() }
    LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), reverseLayout = !left) {
        items(tiles, key = { it.label + it.count }) { tile ->
            Surface(onClick = tile.onClick, modifier = Modifier.width(84.dp), shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)) {
                Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(tile.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(tile.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(tile.count, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
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

/** «Микс дня»: play at once, with a shuffle button. */
@Composable
private fun DailyTile(home: HomeMusic, online: Boolean, modifier: Modifier, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val mix = home.daily
    val enabled = mix != null && online
    Surface(modifier.alpha(if (online) 1f else 0.38f), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)) {
        Column(Modifier.clickable(enabled = enabled, onClick = onPlay).padding(12.dp)) {
            val covers = mix?.tracks.orEmpty().mapNotNull { it.artwork }.distinct().take(4)
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                if (covers.isEmpty()) {
                    if (home.dailyLoading && online) CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else Column(Modifier.fillMaxSize()) {
                    covers.chunked(2).forEach { row ->
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            row.forEach { url -> Cover(url, Modifier.weight(1f).fillMaxHeight().lightSource(url), corner = 0.dp) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.music_daily_mix), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (mix != null) Text(songsAndMinutes(mix.tracks.size, mix.tracks.sumOf { it.durationMs }, known = mix.tracks.all { it.durationMs > 0 }),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onShuffle, enabled = enabled) { Icon(Icons.Rounded.Shuffle, stringResource(R.string.music_shuffle)) }
            }
        }
    }
}

/** «Мой джем»: endless, starts at once. */
@Composable
private fun JamTile(mine: app.freshtuber.domain.model.PlaylistSummary?, title: String, online: Boolean, modifier: Modifier, onPlay: (app.freshtuber.domain.model.PlaylistSummary) -> Unit) {
    val enabled = mine != null && online
    Surface(modifier.alpha(if (online) 1f else 0.38f), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)) {
        Column(Modifier.clickable(enabled = enabled) { mine?.let(onPlay) }.padding(12.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                if (mine?.thumbnailUrl != null) Cover(mine.thumbnailUrl, Modifier.fillMaxSize().lightSource(mine.thumbnailUrl), corner = 0.dp)
                else Icon(Icons.Rounded.AllInclusive, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.music_my_jam_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, stringResource(R.string.player_play), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
