package app.freshtuber.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.freshtuber.R
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.ui.radial.ThumbArc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.asSharedFlow
import androidx.compose.material.icons.rounded.DeleteOutline
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MenuSettingsViewModel @Inject constructor(private val settings: AppSettings) : ViewModel() {
    val leftHanded = settings.leftHanded.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val arc = settings.thumbArc.stateIn(viewModelScope, SharingStarted.Eagerly, ThumbArc())
    val pinned = settings.videoPinned.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setPinned(value: Boolean) { viewModelScope.launch { settings.setVideoPinned(value) } }
    fun setHand(left: Boolean) { viewModelScope.launch { settings.setLeftHanded(left) } }
    fun saveArc(value: ThumbArc, onSaved: () -> Unit = {}) { viewModelScope.launch { settings.setThumbArc(value); onSaved() } }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: AppSettings,
    val local: app.freshtuber.data.subscriptions.LocalSubscriptions,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context) : ViewModel() {
    val preferences = settings.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, app.freshtuber.data.settings.AppPreferences())
    val channels = local.channels.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _messages = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()
    private fun update(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { _messages.emit(R.string.settings_save_failed) }
    } }
    fun feature(feature: app.freshtuber.data.settings.AppFeature, enabled: Boolean) = update { settings.setFeature(feature, enabled) }
    fun theme(mode: app.freshtuber.data.settings.ThemeMode) = update { settings.setTheme(mode) }
    fun quality(height: Int) = update { settings.setPreferredQuality(height) }
    fun downloadQuality(height: Int) = update { settings.setDownloadQuality(height) }
    fun channelNotifications(channel: app.freshtuber.data.subscriptions.LocalChannel, enabled: Boolean) = update {
        local.notify(app.freshtuber.domain.model.ChannelSummary(channel.id, channel.name, channel.avatarUrl), enabled)
    }
    fun removeChannel(id: String) = update { local.remove(id) }
    fun resetHidden() = update { settings.resetHiddenVideos(); _messages.emit(R.string.settings_cleared) }
    fun clearImages() = update {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            coil3.SingletonImageLoader.get(context).diskCache?.clear()
            coil3.SingletonImageLoader.get(context).memoryCache?.clear()
        }
        _messages.emit(R.string.settings_cleared)
    }
}

@Composable
fun SettingsScreen(onCalibrate: () -> Unit, onAccount: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val menu: MenuSettingsViewModel = hiltViewModel()
    val left by menu.leftHanded.collectAsStateWithLifecycle()
    val arc by menu.arc.collectAsStateWithLifecycle()
    var vertical by remember(arc) { mutableFloatStateOf(arc.verticalRadius) }
    var radius by remember(arc) { mutableFloatStateOf(arc.radius) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var systemNotifications by remember { mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) systemNotifications = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val enableNotifications = app.freshtuber.ui.components.rememberNotificationPermission {
        systemNotifications = true
        viewModel.feature(app.freshtuber.data.settings.AppFeature.Notifications, true)
    }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) } }
    var clearHidden by remember { mutableStateOf(false) }
    var clearImages by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<app.freshtuber.data.subscriptions.LocalChannel?>(null) }
    var about by remember { mutableStateOf(false) }
    val qualityOptions = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = app.freshtuber.ui.feed.LocalFeedBottomClearance.current)) {
            item { Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp)) }
            item { SettingsSection(R.string.settings_playback) }
            item { SettingsChoice(stringResource(R.string.settings_playback_quality), qualityOptions.map { stringResource(R.string.quality_resolution, it) }, qualityOptions.indexOf(preferences.quality).coerceAtLeast(0)) { viewModel.quality(qualityOptions[it]) } }
            listOf(
                Triple(app.freshtuber.data.settings.AppFeature.BackgroundPlayback, R.string.settings_background, R.string.settings_background_hint),
                Triple(app.freshtuber.data.settings.AppFeature.PictureInPicture, R.string.settings_pip, R.string.settings_pip_hint),
                Triple(app.freshtuber.data.settings.AppFeature.Autoplay, R.string.settings_autoplay, R.string.settings_autoplay_hint),
                Triple(app.freshtuber.data.settings.AppFeature.Gestures, R.string.settings_gestures, R.string.settings_gestures_hint),
                Triple(app.freshtuber.data.settings.AppFeature.SponsorBlock, R.string.settings_sponsorblock, R.string.settings_sponsorblock_hint),
            ).forEach { (feature, label, hint) -> item { SettingsToggle(stringResource(label), stringResource(hint), preferences.enabled(feature)) { viewModel.feature(feature, it) } } }
            item { SettingsSection(R.string.settings_recommendations) }
            listOf(
                Triple(app.freshtuber.data.settings.AppFeature.DeArrow, R.string.settings_dearrow, R.string.settings_dearrow_hint),
                Triple(app.freshtuber.data.settings.AppFeature.Dislikes, R.string.settings_dislikes, R.string.settings_dislikes_hint),
                Triple(app.freshtuber.data.settings.AppFeature.ShortsTab, R.string.nav_shorts, R.string.settings_shorts_hint),
                Triple(app.freshtuber.data.settings.AppFeature.ShortsInFeeds, R.string.settings_feed_shorts, R.string.settings_feed_shorts_hint),
            ).forEach { (feature, label, hint) -> item { SettingsToggle(stringResource(label), stringResource(hint), preferences.enabled(feature)) { viewModel.feature(feature, it) } } }
            item { SettingsSection(R.string.settings_appearance) }
            item { SettingsChoice(stringResource(R.string.settings_theme), listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark, R.string.theme_amoled).map { stringResource(it) }, preferences.theme.ordinal) { viewModel.theme(app.freshtuber.data.settings.ThemeMode.entries[it]) } }
            item { SettingsToggle(stringResource(R.string.settings_dynamic_color), stringResource(R.string.settings_dynamic_color_hint), preferences.enabled(app.freshtuber.data.settings.AppFeature.DynamicColor)) { viewModel.feature(app.freshtuber.data.settings.AppFeature.DynamicColor, it) } }
            item { SettingsSection(R.string.radial_title) }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.radial_hand), style = MaterialTheme.typography.bodyMedium)
                    listOf(false, true).forEach { hand ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { menu.setHand(hand) }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = left == hand, onClick = { menu.setHand(hand) })
                            Text(stringResource(if (hand) R.string.radial_left else R.string.radial_right))
                        }
                    }
                    OutlinedButton(onClick = onCalibrate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.radial_calibrate)) }
                    Text(stringResource(R.string.radial_reach), modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.radial_reach_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = radius, onValueChange = { radius = it }, valueRange = 80f..300f, onValueChangeFinished = { menu.saveArc(arc.copy(radius = radius)) })
                    Text(stringResource(R.string.radial_vertical_reach), style = MaterialTheme.typography.titleSmall)
                    Slider(value = vertical, onValueChange = { vertical = it }, valueRange = 80f..420f, onValueChangeFinished = { menu.saveArc(arc.copy(verticalRadius = vertical)) })
                    TextButton(onClick = { menu.saveArc(ThumbArc()) }) { Text(stringResource(R.string.radial_reset)) }
                }
            }
            item { SettingsSection(R.string.notification_new_videos) }
            item { SettingsToggle(stringResource(R.string.settings_notifications), stringResource(if (systemNotifications) R.string.settings_notifications_hint else R.string.notification_permission_hint), preferences.enabled(app.freshtuber.data.settings.AppFeature.Notifications) && systemNotifications) {
                if (it) enableNotifications() else viewModel.feature(app.freshtuber.data.settings.AppFeature.Notifications, false)
            } }
            if (channels.isEmpty()) item { Text(stringResource(R.string.settings_no_local_channels), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(channels.size, key = { channels[it].id }) { index ->
                val channel = channels[index]
                val grant = app.freshtuber.ui.components.rememberNotificationPermission { viewModel.channelNotifications(channel, true) }
                ListItem(headlineContent = { Text(channel.name, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    supportingContent = { Text(stringResource(R.string.local_on_device)) },
                    trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = channel.notifications, onCheckedChange = { if (it) grant() else viewModel.channelNotifications(channel, false) })
                        IconButton(onClick = { remove = channel }) { Icon(androidx.compose.material.icons.Icons.Rounded.DeleteOutline, stringResource(R.string.local_remove_channel)) }
                    } })
            }
            item { SettingsSection(R.string.library_downloads) }
            item { SettingsChoice(stringResource(R.string.settings_download_quality), qualityOptions.map { stringResource(R.string.quality_resolution, it) }, qualityOptions.indexOf(preferences.downloadQuality).coerceAtLeast(0)) { viewModel.downloadQuality(qualityOptions[it]) } }
            item { SettingsToggle(stringResource(R.string.settings_download_wifi), stringResource(R.string.settings_download_wifi_hint), preferences.enabled(app.freshtuber.data.settings.AppFeature.DownloadsWifi)) { viewModel.feature(app.freshtuber.data.settings.AppFeature.DownloadsWifi, it) } }
            item { SettingsSection(R.string.settings_storage) }
            item { ListItem(headlineContent = { Text(stringResource(R.string.settings_clear_images)) }, supportingContent = { Text(stringResource(R.string.settings_clear_images_hint)) }, modifier = Modifier.clickable { clearImages = true }) }
            item { ListItem(headlineContent = { Text(stringResource(R.string.settings_reset_hidden)) }, modifier = Modifier.clickable { clearHidden = true }) }
            item { SettingsSection(R.string.nav_account) }
            item { ListItem(headlineContent = { Text(stringResource(R.string.settings_manage_accounts)) }, modifier = Modifier.clickable(onClick = onAccount)) }
            item { ListItem(headlineContent = { Text(stringResource(R.string.settings_about)) }, supportingContent = { Text(stringResource(R.string.settings_version, app.freshtuber.BuildConfig.VERSION_NAME)) }, modifier = Modifier.clickable { about = true }) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = app.freshtuber.ui.feed.LocalFeedBottomClearance.current))
    }
    if (clearHidden || clearImages) AlertDialog(onDismissRequest = { clearHidden = false; clearImages = false },
        title = { Text(stringResource(if (clearHidden) R.string.settings_reset_hidden else R.string.settings_clear_images)) },
        text = { Text(stringResource(if (clearHidden) R.string.settings_reset_hidden_hint else R.string.settings_clear_images_hint)) },
        confirmButton = { TextButton(onClick = { if (clearHidden) viewModel.resetHidden() else viewModel.clearImages(); clearHidden = false; clearImages = false }) { Text(stringResource(R.string.action_clear)) } },
        dismissButton = { TextButton(onClick = { clearHidden = false; clearImages = false }) { Text(stringResource(R.string.action_cancel)) } })
    remove?.let { channel -> AlertDialog(onDismissRequest = { remove = null }, title = { Text(stringResource(R.string.local_remove_channel)) }, text = { Text(channel.name) },
        confirmButton = { TextButton(onClick = { viewModel.removeChannel(channel.id); remove = null }) { Text(stringResource(R.string.local_remove_channel)) } },
        dismissButton = { TextButton(onClick = { remove = null }) { Text(stringResource(R.string.action_cancel)) } }) }
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text(stringResource(R.string.app_name)) },
        text = { Text(stringResource(R.string.settings_about_text)) }, confirmButton = { TextButton(onClick = { about = false }) { Text(stringResource(android.R.string.ok)) } })
}

@Composable
private fun SettingsSection(label: Int) {
    Text(stringResource(label), Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsToggle(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(label) }, supportingContent = { Text(hint) }, trailingContent = { Switch(checked, onChange) },
        modifier = Modifier.heightIn(min = 64.dp).clickable { onChange(!checked) })
}

@Composable
private fun SettingsChoice(label: String, options: List<String>, selected: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(headlineContent = { Text(label) }, supportingContent = { Text(options[selected]) }, modifier = Modifier.clickable { open = true })
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { options.forEachIndexed { index, title ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(index); open = false }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(index == selected, onClick = { onChange(index); open = false }); Text(title)
            }
        } }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } })
}
