package app.freshtuber.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
 * «Слушать» (variant B, 24 Nov 2026): pressed to the bottom like Home. Far from the thumb only the
 * title and the jam shelf, which is swiped anywhere; right above the dock the big «Микс дня» with
 * its play and shuffle at the hand edge, and «Мой джем» as one wide button below it.
 * Favourites, phone files and playlists live in the music dock's «Библиотека».
 */
@Composable
fun MusicListenScreen(viewModel: MusicViewModel = hiltViewModel()) {
    MusicNotes(viewModel)
    MusicFolderAccess(viewModel)
    LaunchedEffect(Unit) { viewModel.loadHome() }
    val home by viewModel.home.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val navigation = LocalMusicNavigation.current
    val left = app.freshtuber.ui.components.LocalLeftHanded.current
    val dailyTitle = stringResource(R.string.music_daily_mix)
    val jamTitle = stringResource(R.string.music_my_jam)
    // Which jams the shelf shows: from Home's «Музыка» filter or from the whole Home page.
    var fromMusic by rememberSaveable { mutableStateOf(true) }
    val jams = when {
        home.jams.music.isEmpty() -> home.jams.home
        home.jams.home.isEmpty() -> home.jams.music
        fromMusic -> home.jams.music
        else -> home.jams.home
    }
    val both = home.jams.music.isNotEmpty() && home.jams.home.isNotEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewport).padding(bottom = 12.dp)) {
            ScreenHeader(stringResource(R.string.music_listen_title))
            Spacer(Modifier.weight(1f))
            if (jams.isNotEmpty()) {
                JamSourceRow(both, fromMusic, left, onPick = { fromMusic = it })
                LazyRow(Modifier.alpha(if (online) 1f else 0.38f), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(jams, key = { it.id }) { jam ->
                        Column(Modifier.width(120.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = online) { viewModel.playJam(jam); navigation?.openPlayer() }.padding(4.dp)) {
                            Cover(jam.thumbnailUrl, Modifier.size(112.dp).lightSource(jam.thumbnailUrl), corner = 14.dp)
                            Text(jam.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            (jam.channelName ?: jam.videoCountText)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (home.daily == null && !home.dailyLoading && home.jams.isEmpty && !online) {
                Box(Modifier.fillMaxWidth().height(160.dp)) { MessageState(stringResource(R.string.music_listen_empty)) }
            }
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DailyHero(home, online, left,
                    onPlay = { viewModel.playDaily(dailyTitle); navigation?.openPlayer() },
                    onShuffle = { viewModel.playDaily(dailyTitle, shuffle = true); navigation?.openPlayer() })
                MyJamButton(home.jams.mine, jamTitle, online, left) { mine -> viewModel.playJam(mine.copy(title = jamTitle)); navigation?.openPlayer() }
            }
        }
    }
}

/** «Джемы» with a two-way switch at the working-hand edge: «Музыка» / «Вся Главная». */
@Composable
private fun JamSourceRow(both: Boolean, fromMusic: Boolean, left: Boolean, onPick: (Boolean) -> Unit) {
    val title: @Composable () -> Unit = {
        Text(stringResource(R.string.music_jams_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp))
    }
    val toggle: @Composable () -> Unit = {
        if (both) Row(Modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)).padding(3.dp)) {
            listOf(true to R.string.music_jams_source_music, false to R.string.music_jams_source_home).forEach { (music, label) ->
                val on = music == fromMusic
                Box(Modifier.heightIn(min = 42.dp).clip(RoundedCornerShape(21.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .semantics { selected = on }
                    .clickable(role = Role.Tab) { onPick(music) }
                    .padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        if (left) { toggle(); title() } else { title(); toggle() }
    }
}

/** «Микс дня»: a 2x2 collage, the title, and play + shuffle at the bottom corner by the thumb. */
@Composable
private fun DailyHero(home: HomeMusic, online: Boolean, left: Boolean, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val mix = home.daily
    val enabled = mix != null && online
    Surface(onClick = onPlay, enabled = enabled, modifier = Modifier.fillMaxWidth().alpha(if (online) 1f else 0.38f), shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)) {
        val collage: @Composable () -> Unit = {
            val covers = mix?.tracks.orEmpty().mapNotNull { it.artwork }.distinct().take(4)
            Box(Modifier.size(156.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
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
        }
        val text: @Composable RowScope.() -> Unit = {
            Column(Modifier.weight(1f).height(156.dp), horizontalAlignment = if (left) Alignment.End else Alignment.Start) {
                Text(stringResource(R.string.music_play_now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.music_daily_mix), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                if (mix != null) Text(songsAndMinutes(mix.tracks.size, mix.tracks.sumOf { it.durationMs }, known = mix.tracks.all { it.durationMs > 0 }),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val shuffle: @Composable () -> Unit = {
                        FilledTonalIconButton(onClick = onShuffle, enabled = enabled, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Shuffle, stringResource(R.string.music_shuffle))
                        }
                    }
                    val play: @Composable () -> Unit = {
                        FilledIconButton(onClick = onPlay, enabled = enabled, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Rounded.PlayArrow, stringResource(R.string.player_play), Modifier.size(32.dp))
                        }
                    }
                    // Play sits in the very corner by the thumb.
                    if (left) { play(); shuffle() } else { shuffle(); play() }
                }
            }
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (left) { text(); collage() } else { collage(); text() }
        }
    }
}

/** «Мой джем»: endless, starts at once; its play mark is at the working-hand edge. */
@Composable
private fun MyJamButton(mine: app.freshtuber.domain.model.PlaylistSummary?, title: String, online: Boolean, left: Boolean, onPlay: (app.freshtuber.domain.model.PlaylistSummary) -> Unit) {
    val enabled = mine != null && online
    Surface(onClick = { mine?.let(onPlay) }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp).alpha(if (enabled) 1f else 0.38f),
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        val playMark: @Composable () -> Unit = {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        val text = "$title · ${stringResource(R.string.music_my_jam_endless)}"
        Row(Modifier.padding(start = if (left) 6.dp else 18.dp, end = if (left) 18.dp else 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (left) playMark() else Icon(Icons.Rounded.AllInclusive, null)
            Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f), textAlign = if (left) TextAlign.End else TextAlign.Start)
            if (left) Icon(Icons.Rounded.AllInclusive, null) else playMark()
        }
    }
}

/**
 * Playlists, bottom-up like the rest of the library, «Новый плейлист» under the thumb.
 * Inside the «Библиотека» tab the shelf of sections sits right above that button.
 */
@Composable
fun MusicPlaylists(viewModel: MusicViewModel, title: String? = null, subtitle: String? = null, shelf: (@Composable ColumnScope.() -> Unit)? = null) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val navigation = LocalMusicNavigation.current
    val left = app.freshtuber.ui.components.LocalLeftHanded.current
    val preferences = app.freshtuber.ui.theme.LocalAppPreferences.current
    val fromBottom = !preferences.enabled(app.freshtuber.data.settings.AppFeature.TraditionalScroll)
    var creating by remember { mutableStateOf(false) }
    if (creating) PlaylistPicker(null, viewModel) { creating = false }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title ?: stringResource(R.string.music_playlists), subtitle)
        LazyColumn(Modifier.weight(1f), reverseLayout = fromBottom) {
            items(playlists, key = { it.id }) { playlist ->
                ListItem(headlineContent = { Text(playlist.name) }, trailingContent = { Text(playlist.count.toString()) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.heightIn(min = 64.dp).clickable { navigation?.openCollection("playlist:${playlist.id}") })
            }
            if (playlists.isEmpty()) item(key = "empty") { Box(Modifier.height(220.dp)) { MessageState(stringResource(R.string.music_library_empty)) } }
        }
        app.freshtuber.ui.components.ContextBar(listOf(app.freshtuber.ui.components.ContextAction(Icons.Rounded.Add, stringResource(R.string.music_playlist_new), primary = true) { creating = true }),
            left, above = shelf)
    }
}
