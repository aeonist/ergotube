package app.freshtuber.ui.player

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import app.freshtuber.R
import app.freshtuber.player.PlayerController
import app.freshtuber.player.PlayerState
import app.freshtuber.player.SleepTimer
import app.freshtuber.ui.radial.RadialItem
import app.freshtuber.ui.radial.RadialMenu
import app.freshtuber.ui.radial.ThumbArc
import java.util.Locale

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** One visible radial level, including the available stream qualities and playback speeds. */
@Composable
fun PlayerSettingsSheet(
    state: PlayerState,
    controller: PlayerController,
    leftHanded: Boolean,
    arc: ThumbArc,
    centerOnScreen: androidx.compose.ui.geometry.Offset? = null,
    onDismiss: () -> Unit,
) {
    var sleepOpen by remember { mutableStateOf(false) }
    val now = state.nowPlaying
    var lastSubtitle by remember(now?.videoId) { mutableIntStateOf(-1) }
    val enabled = stringResource(R.string.player_on)
    val disabled = stringResource(R.string.player_off)
    val quality = now?.qualities?.getOrNull(now.qualityIndex)?.label ?: stringResource(R.string.player_quality_auto)
    val items = buildList {
        if (now != null && now.qualities.isNotEmpty()) add(RadialItem(
            "quality", "${stringResource(R.string.player_quality)}\n$quality", Icons.Rounded.HighQuality,
            children = now.qualities.mapIndexed { index, track ->
                RadialItem("quality-$index", track.label, Icons.Rounded.HighQuality, selected = index == now.qualityIndex) {
                    controller.selectQuality(index); onDismiss()
                }
            },
        ) {})
        else add(RadialItem("quality-unavailable", stringResource(R.string.player_quality), Icons.Rounded.HighQuality, enabled = false) {})
        add(RadialItem("speed", "${stringResource(R.string.player_speed)}\n${speedLabel(state.speed)}", Icons.Rounded.Speed,
            children = SPEEDS.map { speed ->
                RadialItem("speed-$speed", speedLabel(speed), Icons.Rounded.Speed, selected = speed == state.speed) {
                    controller.setSpeed(speed); onDismiss()
                }
            }) {})
        if (now != null && now.subtitles.isNotEmpty()) add(RadialItem(
            "subtitles", "${stringResource(R.string.player_subtitles)}\n${if (now.subtitleIndex >= 0) enabled else disabled}",
            Icons.Rounded.ClosedCaption, selected = now.subtitleIndex >= 0,
        ) {
            if (now.subtitleIndex >= 0) {
                lastSubtitle = now.subtitleIndex
                controller.selectSubtitles(-1)
            } else {
                val preferred = now.subtitles.indexOfFirst { it.language.substringBefore('-') == Locale.getDefault().language }
                controller.selectSubtitles(lastSubtitle.takeIf { it in now.subtitles.indices } ?: preferred.takeIf { it >= 0 } ?: 0)
            }
        })
        else add(RadialItem("subtitles-unavailable", stringResource(R.string.player_subtitles), Icons.Rounded.ClosedCaption, enabled = false) {})
        add(RadialItem("repeat", "${stringResource(R.string.player_repeat)}\n${if (state.repeat) enabled else disabled}",
            Icons.Rounded.Repeat, selected = state.repeat) { controller.toggleRepeat() })
        val timer = when (val sleep = state.sleepTimer) {
            SleepTimer.Off -> disabled
            SleepTimer.EndOfVideo -> stringResource(R.string.player_sleep_end_short)
            is SleepTimer.Minutes -> stringResource(R.string.player_sleep_minutes, sleep.value)
        }
        add(RadialItem("sleep", "${stringResource(R.string.player_sleep)}\n$timer", Icons.Rounded.Bedtime,
            selected = state.sleepTimer != SleepTimer.Off) { sleepOpen = true })
    }
    if (sleepOpen) {
        SleepTimerDial(state.sleepTimer, arc, leftHanded,
            onBack = { sleepOpen = false },
            onSet = { controller.setSleepTimer(it); onDismiss() })
    } else if (centerOnScreen != null) app.freshtuber.ui.radial.TouchRadialMenu(items, centerOnScreen, onDismiss)
    else RadialMenu(items, leftHanded, arc, initiallyExpanded = true, closeOnAction = false,
        onExpandedChange = { if (!it && !sleepOpen) onDismiss() },
        heading = stringResource(R.string.player_settings), instruction = stringResource(R.string.radial_choose_action))
}
