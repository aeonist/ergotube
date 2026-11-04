package app.freshtuber.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.player.PlayerController
import app.freshtuber.player.PlayerState
import app.freshtuber.player.SleepTimer
import app.freshtuber.ui.components.ChoiceRows
import app.freshtuber.ui.components.LocalLeftHanded
import app.freshtuber.ui.components.TileGrid
import app.freshtuber.ui.components.TileItem
import app.freshtuber.ui.feed.shareVideo
import app.freshtuber.ui.screens.player.VideoUiState
import app.freshtuber.ui.screens.player.VideoViewModel
import java.util.Locale

val AllSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val SleepChoices = listOf(SleepTimer.Off, SleepTimer.Minutes(15), SleepTimer.Minutes(30), SleepTimer.Minutes(45),
    SleepTimer.Minutes(60), SleepTimer.Minutes(90), SleepTimer.EndOfVideo)

private enum class MorePage { Tiles, Quality, Speed, Subtitles, Sleep }

/** Turns captions on (last or preferred language) or off. Returns whether they are on now. */
fun toggleCaptions(controller: PlayerController, state: PlayerState, last: Int): Boolean {
    val now = state.nowPlaying ?: return false
    if (now.subtitles.isEmpty()) return false
    if (now.subtitleIndex >= 0) { controller.selectSubtitles(-1); return false }
    val preferred = now.subtitles.indexOfFirst { it.language.substringBefore('-') == Locale.getDefault().language }
    controller.selectSubtitles(last.takeIf { it in now.subtitles.indices } ?: preferred.takeIf { it >= 0 } ?: 0)
    return true
}

@Composable
fun sleepLabel(timer: SleepTimer): String = when (timer) {
    SleepTimer.Off -> stringResource(R.string.player_sleep_off)
    SleepTimer.EndOfVideo -> stringResource(R.string.player_sleep_end_short)
    is SleepTimer.Minutes -> stringResource(R.string.player_sleep_minutes, timer.value)
}

/** «Ещё» in the player: tiles first, each opening a plain list of choices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMoreSheet(state: PlayerState, video: VideoUiState, controller: PlayerController, viewModel: VideoViewModel,
    openChannel: (String) -> Unit, onDismiss: () -> Unit) {
    var page by remember { mutableStateOf(MorePage.Tiles) }
    val now = state.nowPlaying
    val left = LocalLeftHanded.current
    val context = LocalContext.current
    val downloadNotifications = app.freshtuber.ui.components.rememberDownloadNotifications()
    val enableNotifications = app.freshtuber.ui.components.rememberNotificationPermission { viewModel.setLocalNotifications(true) }
    val shareTitle = stringResource(R.string.video_share)
    val on = stringResource(R.string.player_on)
    val off = stringResource(R.string.player_off)
    val auto = stringResource(R.string.player_quality_auto)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        when (page) {
            MorePage.Tiles -> {
                val channel = video.details?.channel
                val tiles = buildList {
                    add(TileItem(Icons.Rounded.HighQuality, stringResource(R.string.player_quality),
                        now?.qualities?.getOrNull(now.qualityIndex)?.label ?: auto, enabled = !now?.qualities.isNullOrEmpty()) { page = MorePage.Quality })
                    add(TileItem(Icons.Rounded.Speed, stringResource(R.string.player_speed), speedLabel(state.speed)) { page = MorePage.Speed })
                    add(TileItem(Icons.Rounded.ClosedCaption, stringResource(R.string.player_subtitles),
                        now?.subtitles?.getOrNull(now.subtitleIndex)?.label ?: off, enabled = !now?.subtitles.isNullOrEmpty()) { page = MorePage.Subtitles })
                    add(TileItem(Icons.Rounded.Bedtime, stringResource(R.string.player_sleep), sleepLabel(state.sleepTimer),
                        selected = state.sleepTimer != SleepTimer.Off) { page = MorePage.Sleep })
                    add(TileItem(Icons.Rounded.Repeat, stringResource(R.string.player_repeat), if (state.repeat) on else off, selected = state.repeat) { controller.toggleRepeat() })
                    add(TileItem(Icons.Rounded.Download, stringResource(R.string.feed_download)) { downloadNotifications(); viewModel.download(); onDismiss() })
                    add(TileItem(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.video_save)) { onDismiss(); viewModel.openPlaylistPicker() })
                    add(TileItem(Icons.Rounded.Share, shareTitle) { onDismiss(); shareVideo(context, viewModel.videoId, shareTitle) })
                    if (channel != null) {
                        add(TileItem(Icons.Rounded.AccountCircle, stringResource(R.string.channel_group)) { onDismiss(); openChannel(channel.id) })
                        add(TileItem(Icons.Rounded.Notifications, stringResource(R.string.player_notify), selected = video.localNotifications) {
                            if (video.localNotifications) viewModel.setLocalNotifications(false) else enableNotifications()
                        })
                    }
                }
                TileGrid(tiles, 4, left, Modifier.padding(bottom = 16.dp))
            }
            MorePage.Quality -> PickerTitle(stringResource(R.string.player_quality)) {
                val qualities = now?.qualities.orEmpty()
                ChoiceRows(qualities.map { it.label }, now?.qualityIndex ?: -1) { controller.selectQuality(it); onDismiss() }
            }
            MorePage.Speed -> PickerTitle(stringResource(R.string.player_speed)) {
                ChoiceRows(AllSpeeds.map(::speedLabel), AllSpeeds.indexOf(state.speed)) { controller.setSpeed(AllSpeeds[it]); onDismiss() }
            }
            MorePage.Subtitles -> PickerTitle(stringResource(R.string.player_subtitles)) {
                val subtitles = now?.subtitles.orEmpty()
                ChoiceRows(listOf(off) + subtitles.map { it.label }, (now?.subtitleIndex ?: -1) + 1) { controller.selectSubtitles(it - 1); onDismiss() }
            }
            MorePage.Sleep -> PickerTitle(stringResource(R.string.player_sleep)) {
                ChoiceRows(SleepChoices.map { sleepLabel(it) }, SleepChoices.indexOf(state.sleepTimer)) { controller.setSleepTimer(SleepChoices[it]); onDismiss() }
            }
        }
    }
}

@Composable
private fun PickerTitle(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        content()
    }
}
