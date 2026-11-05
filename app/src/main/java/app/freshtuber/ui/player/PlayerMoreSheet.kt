package app.freshtuber.ui.player

import androidx.compose.foundation.layout.*
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
import app.freshtuber.ui.components.ChipItem
import app.freshtuber.ui.components.HandChips
import app.freshtuber.ui.components.LocalLeftHanded
import app.freshtuber.ui.components.TileItem
import app.freshtuber.ui.components.handOrder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.freshtuber.ui.feed.shareVideo
import app.freshtuber.ui.screens.player.VideoUiState
import app.freshtuber.ui.screens.player.VideoViewModel
import java.util.Locale

val AllSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val SleepChoices = listOf(SleepTimer.Off, SleepTimer.Minutes(15), SleepTimer.Minutes(30), SleepTimer.Minutes(45),
    SleepTimer.Minutes(60), SleepTimer.Minutes(90), SleepTimer.EndOfVideo)

/** Which choice the sheet shows inline, right above its switches. */
private enum class MorePick { Quality, Speed, Subtitles, Sleep }

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

/**
 * «Ещё» in the player, built from the thumb up: the four most used actions lowest (Save,
 * Download, Share, Sleep timer), above them switches for quality, speed, subtitles and
 * repeat whose choices open inline right above, and the channel with its notifications on top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMoreSheet(state: PlayerState, video: VideoUiState, controller: PlayerController, viewModel: VideoViewModel,
    openChannel: (String) -> Unit, onDismiss: () -> Unit) {
    var pick by remember { mutableStateOf<MorePick?>(null) }
    val now = state.nowPlaying
    val left = LocalLeftHanded.current
    val context = LocalContext.current
    val downloadNotifications = app.freshtuber.ui.components.rememberDownloadNotifications()
    val enableNotifications = app.freshtuber.ui.components.rememberNotificationPermission { viewModel.setLocalNotifications(true) }
    val shareTitle = stringResource(R.string.video_share)
    val off = stringResource(R.string.player_off)
    val auto = stringResource(R.string.player_quality_auto)
    val toggle: (MorePick) -> Unit = { pick = if (pick == it) null else it }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top, small: the channel and its notifications.
            video.details?.channel?.let { channel ->
                val bell = @Composable {
                    SmallChip(stringResource(R.string.player_notify), selected = video.localNotifications,
                        leading = { Icon(if (video.localNotifications) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications, null, Modifier.size(18.dp)) }) {
                        if (video.localNotifications) viewModel.setLocalNotifications(false) else enableNotifications()
                    }
                }
                val channelChip = @Composable {
                    SmallChip(channel.name, leading = {
                        coil3.compose.AsyncImage(channel.avatarUrl, null, Modifier.size(22.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    }) { onDismiss(); openChannel(channel.id) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, if (left) Alignment.Start else Alignment.End)) {
                    handOrder(listOf(channelChip, bell), left).forEach { it() }
                }
            }
            // The open choice, right above the switch that opened it.
            AnimatedVisibility(pick != null, enter = expandVertically(), exit = shrinkVertically()) {
                val current = pick
                Column(Modifier.fillMaxWidth()) {
                    if (current != null) {
                        val (title, options, selected, apply) = when (current) {
                            // Tracks come best first; the strip reads low to high like the speeds.
                            MorePick.Quality -> now?.qualities.orEmpty().let { q ->
                                // «Авто» (the player picks from the connection speed, as YouTube does)
                                // sits at the hand end of the strip, where it is seen and reached first.
                                val canAuto = now != null && !now.isShort && !now.isOffline && !now.isLive
                                val labels = q.map { it.label }.reversed()
                                val autoAt = if (!canAuto) -1 else if (left) 0 else labels.size
                                val shift = if (autoAt == 0) 1 else 0
                                val options = if (!canAuto) labels else if (left) listOf(auto) + labels else labels + auto
                                Choice(stringResource(R.string.player_quality), options,
                                    when {
                                        canAuto && now.qualityAuto -> autoAt
                                        else -> (now?.qualityIndex ?: -1).let { if (it >= 0) q.size - 1 - it + shift else -1 }
                                    }) { i: Int -> if (i == autoAt) controller.selectAutoQuality() else controller.selectQuality(q.size - 1 - (i - shift)) }
                            }
                            MorePick.Speed -> Choice(stringResource(R.string.player_speed), AllSpeeds.map(::speedLabel),
                                AllSpeeds.indexOf(state.speed)) { i: Int -> controller.setSpeed(AllSpeeds[i]) }
                            MorePick.Subtitles -> Choice(stringResource(R.string.player_subtitles), listOf(off) + now?.subtitles.orEmpty().map { it.label },
                                (now?.subtitleIndex ?: -1) + 1) { i: Int -> controller.selectSubtitles(i - 1) }
                            MorePick.Sleep -> Choice(stringResource(R.string.player_sleep), SleepChoices.map { sleepLabel(it) },
                                SleepChoices.indexOf(state.sleepTimer)) { i: Int -> controller.setSleepTimer(SleepChoices[i]) }
                        }
                        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            textAlign = if (left) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End)
                        HandChips(options.mapIndexed { i, label -> ChipItem(label, i == selected) { apply(i); pick = null } }, left, natural = true)
                    }
                }
            }
            // Switches: the current value on each, the choice opens above.
            val switches = listOf(
                SwitchItem(Icons.Rounded.HighQuality, stringResource(R.string.player_quality),
                    now?.qualities?.getOrNull(now.qualityIndex)?.label?.let { if (now.qualityAuto) "$auto · $it" else it } ?: auto, open = pick == MorePick.Quality,
                    enabled = !now?.qualities.isNullOrEmpty()) { toggle(MorePick.Quality) },
                SwitchItem(Icons.Rounded.Speed, stringResource(R.string.player_speed), speedLabel(state.speed),
                    open = pick == MorePick.Speed, active = state.speed != 1f) { toggle(MorePick.Speed) },
                SwitchItem(Icons.Rounded.ClosedCaption, stringResource(R.string.player_subtitles),
                    now?.subtitles?.getOrNull(now.subtitleIndex)?.label ?: off, open = pick == MorePick.Subtitles,
                    active = (now?.subtitleIndex ?: -1) >= 0, enabled = !now?.subtitles.isNullOrEmpty()) { toggle(MorePick.Subtitles) },
                SwitchItem(Icons.Rounded.Repeat, stringResource(R.string.player_repeat),
                    stringResource(if (state.repeat) R.string.player_on else R.string.player_off), active = state.repeat) { controller.toggleRepeat() },
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                handOrder(switches, left).forEach { SwitchPill(it, Modifier.weight(1f)) }
            }
            // Lowest, under the thumb: the four most used actions; the first sits on the hand side.
            val actions = listOf(
                TileItem(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.video_save)) { onDismiss(); viewModel.openPlaylistPicker() },
                TileItem(Icons.Rounded.Download, stringResource(R.string.feed_download)) { downloadNotifications(); viewModel.download(); onDismiss() },
                TileItem(Icons.Rounded.Share, shareTitle) { onDismiss(); shareVideo(context, viewModel.videoId, shareTitle) },
                TileItem(Icons.Rounded.Bedtime, if (state.sleepTimer == SleepTimer.Off) stringResource(R.string.player_sleep) else sleepLabel(state.sleepTimer),
                    selected = state.sleepTimer != SleepTimer.Off || pick == MorePick.Sleep) { toggle(MorePick.Sleep) },
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                handOrder(actions, left).forEach { BigAction(it, Modifier.weight(1f)) }
            }
        }
    }
}

private data class Choice(val title: String, val options: List<String>, val selected: Int, val apply: (Int) -> Unit)

private data class SwitchItem(val icon: ImageVector, val label: String, val value: String, val open: Boolean = false,
    val active: Boolean = false, val enabled: Boolean = true, val onClick: () -> Unit)

/** A switch: icon and current value; filled while its choice is open or it is on. */
@Composable
private fun SwitchPill(item: SwitchItem, modifier: Modifier) {
    val strong = item.open || item.active
    Surface(onClick = item.onClick, enabled = item.enabled, modifier = modifier.heightIn(min = 58.dp)
        .semantics { contentDescription = "${item.label}: ${item.value}"; selected = strong },
        shape = RoundedCornerShape(18.dp),
        color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = (if (strong) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface).copy(alpha = if (item.enabled) 1f else 0.38f)) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(item.icon, null, Modifier.size(20.dp))
            Text(item.value, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Text(item.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = LocalContentColor.current.copy(alpha = 0.7f))
        }
    }
}

/** One of the four big actions in the lowest row. */
@Composable
private fun BigAction(tile: TileItem, modifier: Modifier) {
    val bg = if (tile.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (tile.selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(onClick = tile.onClick, enabled = tile.enabled, modifier = modifier.heightIn(min = 76.dp).semantics { selected = tile.selected },
        shape = RoundedCornerShape(20.dp), color = bg, contentColor = fg) {
        Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(tile.icon, null, Modifier.size(26.dp))
            Text(tile.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun SmallChip(label: String, selected: Boolean = false, leading: @Composable () -> Unit, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.heightIn(min = 40.dp).widthIn(max = 220.dp).semantics { this.selected = selected },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(Modifier.padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
