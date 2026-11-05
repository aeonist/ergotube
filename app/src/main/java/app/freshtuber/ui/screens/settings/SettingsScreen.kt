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
import app.freshtuber.ui.components.*
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import app.freshtuber.data.settings.AppFeature
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
    fun setHand(left: Boolean) { viewModelScope.launch { settings.setLeftHanded(left) } }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: AppSettings,
    val local: app.freshtuber.data.subscriptions.LocalSubscriptions,
    accounts: app.freshtuber.data.account.AccountRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context) : ViewModel() {
    val preferences = settings.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, app.freshtuber.data.settings.AppPreferences())
    val channels = local.channels.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val account = accounts.state
    private val _messages = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()
    private fun update(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { _messages.emit(R.string.settings_save_failed) }
    } }
    fun feature(feature: app.freshtuber.data.settings.AppFeature, enabled: Boolean) = update { settings.setFeature(feature, enabled) }
    fun theme(mode: app.freshtuber.data.settings.ThemeMode) = update { settings.setTheme(mode) }
    fun style(style: app.freshtuber.data.settings.UiStyle) = update { settings.setStyle(style) }
    fun motion(motion: app.freshtuber.data.settings.BackgroundMotion) = update { settings.setMotion(motion) }
    fun quality(wifi: Boolean, height: Int) = update { settings.setPlaybackQuality(wifi, height) }
    fun dockWidth(percent: Int) = update { settings.setDockWidth(percent) }
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

/** The six settings sections, most used first: item 0 lands under the thumb. */
private enum class SettingsGroup(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: Int) {
    Look(androidx.compose.material.icons.Icons.Rounded.BackHand, R.string.settings_group_look),
    Energy(androidx.compose.material.icons.Icons.Rounded.BatterySaver, R.string.settings_group_energy),
    Player(androidx.compose.material.icons.Icons.Rounded.PlayCircle, R.string.settings_group_player),
    Feeds(androidx.compose.material.icons.Icons.Rounded.AutoAwesome, R.string.settings_group_feeds),
    Notifications(androidx.compose.material.icons.Icons.Rounded.Notifications, R.string.settings_group_notifications),
    Storage(androidx.compose.material.icons.Icons.Rounded.SdStorage, R.string.settings_group_storage),
}

/**
 * Settings, built from the thumb up: the title, the account and six section tiles sit low on
 * the screen; a tile opens its switches in a sheet at the bottom. Choices are chips in place,
 * never a dialog in the middle of the screen.
 */
@Composable
fun SettingsScreen(onAccount: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val menu: MenuSettingsViewModel = hiltViewModel()
    val left by menu.leftHanded.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val resources = androidx.compose.ui.platform.LocalResources.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val appMessages = LocalAppMessages.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var systemNotifications by remember { mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) systemNotifications = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val enableNotifications = app.freshtuber.ui.components.rememberNotificationPermission {
        systemNotifications = true
        viewModel.feature(AppFeature.Notifications, true)
    }
    LaunchedEffect(viewModel, resources) { viewModel.messages.collect { appMessages?.show(resources.getString(it)) } }
    var open by rememberSaveable { mutableStateOf<SettingsGroup?>(null) }
    var clearHidden by remember { mutableStateOf(false) }
    var clearImages by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<app.freshtuber.data.subscriptions.LocalChannel?>(null) }
    var about by remember { mutableStateOf(false) }
    val qualityOptions = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)
    val notificationsOn = preferences.enabled(AppFeature.Notifications) && systemNotifications
    val themeNames = listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark, R.string.theme_amoled, R.string.theme_classic).map { stringResource(it) }
    val on = stringResource(R.string.player_on)
    val off = stringResource(R.string.player_off)

    fun summary(group: SettingsGroup): String = when (group) {
        SettingsGroup.Look -> resources.getString(R.string.settings_hand_value, resources.getString(if (left) R.string.radial_left else R.string.radial_right)) +
            " · " + themeNames[preferences.theme.ordinal]
        SettingsGroup.Energy -> resources.getString(if (preferences.enabled(AppFeature.LiquidFlat)) R.string.settings_flat else R.string.settings_live_light)
        SettingsGroup.Player -> resources.getString(R.string.settings_quality_networks)
        SettingsGroup.Feeds -> resources.getString(R.string.settings_group_feeds_hint)
        SettingsGroup.Notifications -> if (notificationsOn) on else off
        SettingsGroup.Storage -> resources.getString(R.string.quality_resolution, preferences.downloadQuality)
    }

    // Pressed to the bottom; on a short screen (landscape) it scrolls instead of clipping.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(bottom = 12.dp),
        verticalArrangement = Arrangement.Bottom) {
        ScreenHeader(stringResource(R.string.nav_settings), stringResource(R.string.settings_subtitle))
        val signed = account as? app.freshtuber.data.account.AccountState.SignedIn
        Surface(onClick = onAccount, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(min = 56.dp),
            shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Rounded.AccountCircle, null)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.nav_account), style = MaterialTheme.typography.titleSmall)
                    Text(signed?.active?.let { it.name ?: it.email } ?: stringResource(R.string.settings_account_signed_out),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SectionTiles(SettingsGroup.entries, left, Modifier.padding(horizontal = 12.dp)) { group, tileModifier ->
            SectionTile(group.icon, stringResource(group.title), summary(group), tileModifier) { open = group }
        }
    }
    }

    open?.let { group ->
        SettingsSheet(stringResource(group.title), onDismiss = { open = null }) {
            // Inside a sheet the most used rows come last, nearest the thumb.
            when (group) {
                SettingsGroup.Look -> {
                    SettingsToggle(stringResource(R.string.settings_swap_home_mine), stringResource(R.string.settings_swap_home_mine_hint),
                        preferences.enabled(AppFeature.SwapHomeMine)) { viewModel.feature(AppFeature.SwapHomeMine, it) }
                    Text(stringResource(R.string.settings_dock_width), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
                    var width by remember(preferences.dockWidth) { mutableFloatStateOf(preferences.dockWidth.toFloat()) }
                    Slider(value = width, onValueChange = { width = it }, valueRange = 0f..100f,
                        onValueChangeFinished = { viewModel.dockWidth(width.toInt()) }, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.settings_dock_compact), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.settings_dock_full), style = MaterialTheme.typography.labelSmall)
                    }
                    SettingsToggle(stringResource(R.string.settings_dynamic_color), stringResource(R.string.settings_dynamic_color_hint), preferences.enabled(AppFeature.DynamicColor)) { viewModel.feature(AppFeature.DynamicColor, it) }
                    SettingsChoice(stringResource(R.string.settings_font), listOf(stringResource(R.string.settings_font_serif), stringResource(R.string.settings_font_sans)),
                        if (preferences.enabled(AppFeature.SerifFont)) 0 else 1, left) { viewModel.feature(AppFeature.SerifFont, it == 0) }
                    SettingsChoice(stringResource(R.string.settings_style), listOf(stringResource(R.string.settings_style_ergonomic), stringResource(R.string.settings_style_classic)),
                        preferences.style.ordinal, left) { viewModel.style(app.freshtuber.data.settings.UiStyle.entries[it]) }
                    SettingsChoice(stringResource(R.string.settings_theme), themeNames, preferences.theme.ordinal, left) { viewModel.theme(app.freshtuber.data.settings.ThemeMode.entries[it]) }
                    SettingsChoice(stringResource(R.string.settings_hand), listOf(stringResource(R.string.radial_left), stringResource(R.string.radial_right)),
                        if (left) 0 else 1, left) { menu.setHand(it == 0) }
                }
                SettingsGroup.Energy -> {
                    SettingsChoice(stringResource(R.string.settings_motion), listOf(R.string.settings_motion_always, R.string.settings_motion_transition, R.string.settings_motion_off).map { stringResource(it) },
                        preferences.motion.ordinal, left) { viewModel.motion(app.freshtuber.data.settings.BackgroundMotion.entries[it]) }
                    listOf(
                        Triple(AppFeature.LiquidRefraction, R.string.settings_refraction, R.string.settings_refraction_hint),
                        Triple(AppFeature.LiquidGrain, R.string.settings_grain, R.string.settings_grain_hint),
                        Triple(AppFeature.LiquidPowerSaver, R.string.settings_power_saver, R.string.settings_power_saver_hint),
                        Triple(AppFeature.LiquidFlat, R.string.settings_flat, R.string.settings_flat_hint),
                    ).forEach { (feature, label, hint) -> SettingsToggle(stringResource(label), stringResource(hint), preferences.enabled(feature)) { viewModel.feature(feature, it) } }
                }
                SettingsGroup.Player -> {
                    listOf(
                        Triple(AppFeature.SponsorBlock, R.string.settings_sponsorblock, R.string.settings_sponsorblock_hint),
                        Triple(AppFeature.Gestures, R.string.settings_gestures, R.string.settings_gestures_hint),
                        Triple(AppFeature.Autoplay, R.string.settings_autoplay, R.string.settings_autoplay_hint),
                        Triple(AppFeature.PictureInPicture, R.string.settings_pip, R.string.settings_pip_hint),
                        Triple(AppFeature.MiniPlayerTapOpens, R.string.settings_mini_tap, R.string.settings_mini_tap_hint),
                        Triple(AppFeature.BackgroundPlayback, R.string.settings_background, R.string.settings_background_hint),
                    ).forEach { (feature, label, hint) -> SettingsToggle(stringResource(label), stringResource(hint), preferences.enabled(feature)) { viewModel.feature(feature, it) } }
                    val streamingOptions = listOf(0) + qualityOptions
                    val labels = streamingOptions.map { if (it == 0) stringResource(R.string.player_quality_auto) else stringResource(R.string.quality_resolution, it) }
                    Text(stringResource(R.string.settings_quality_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsChoice(stringResource(R.string.settings_quality_wifi), labels,
                        streamingOptions.indexOf(preferences.wifiQuality).coerceAtLeast(0), left) { viewModel.quality(true, streamingOptions[it]) }
                    SettingsChoice(stringResource(R.string.settings_quality_mobile), labels,
                        streamingOptions.indexOf(preferences.mobileQuality).coerceAtLeast(0), left) { viewModel.quality(false, streamingOptions[it]) }
                }
                SettingsGroup.Feeds -> listOf(
                    Triple(AppFeature.ShortsInFeeds, R.string.settings_feed_shorts, R.string.settings_feed_shorts_hint),
                    Triple(AppFeature.ShortsTab, R.string.nav_shorts, R.string.settings_shorts_hint),
                    Triple(AppFeature.Dislikes, R.string.settings_dislikes, R.string.settings_dislikes_hint),
                    Triple(AppFeature.DeArrow, R.string.settings_dearrow, R.string.settings_dearrow_hint),
                ).forEach { (feature, label, hint) -> SettingsToggle(stringResource(label), stringResource(hint), preferences.enabled(feature)) { viewModel.feature(feature, it) } }
                SettingsGroup.Notifications -> {
                    if (channels.isEmpty()) Text(stringResource(R.string.settings_no_local_channels), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    channels.forEach { channel ->
                        key(channel.id) {
                            val grant = app.freshtuber.ui.components.rememberNotificationPermission { viewModel.channelNotifications(channel, true) }
                            ListItem(colors = ClearListItem, headlineContent = { Text(channel.name, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                                supportingContent = { Text(stringResource(R.string.local_on_device)) },
                                trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(checked = channel.notifications, onCheckedChange = { if (it) grant() else viewModel.channelNotifications(channel, false) })
                                    IconButton(onClick = { remove = channel }) { Icon(androidx.compose.material.icons.Icons.Rounded.DeleteOutline, stringResource(R.string.local_remove_channel)) }
                                } })
                        }
                    }
                    SettingsToggle(stringResource(R.string.settings_notifications), stringResource(if (systemNotifications) R.string.settings_notifications_hint else R.string.notification_permission_hint), notificationsOn) {
                        if (it) enableNotifications() else viewModel.feature(AppFeature.Notifications, false)
                    }
                }
                SettingsGroup.Storage -> {
                    ListItem(colors = ClearListItem, headlineContent = { Text(stringResource(R.string.settings_about)) }, supportingContent = { Text(stringResource(R.string.settings_version, app.freshtuber.BuildConfig.VERSION_NAME)) }, modifier = Modifier.clickable { about = true })
                    ListItem(colors = ClearListItem, headlineContent = { Text(stringResource(R.string.settings_reset_hidden)) }, modifier = Modifier.clickable { clearHidden = true })
                    ListItem(colors = ClearListItem, headlineContent = { Text(stringResource(R.string.settings_clear_images)) }, supportingContent = { Text(stringResource(R.string.settings_clear_images_hint)) }, modifier = Modifier.clickable { clearImages = true })
                    SettingsToggle(stringResource(R.string.settings_download_wifi), stringResource(R.string.settings_download_wifi_hint), preferences.enabled(AppFeature.DownloadsWifi)) { viewModel.feature(AppFeature.DownloadsWifi, it) }
                    SettingsChoice(stringResource(R.string.settings_download_quality), qualityOptions.map { stringResource(R.string.quality_resolution, it) },
                        qualityOptions.indexOf(preferences.downloadQuality).coerceAtLeast(0), left) { viewModel.downloadQuality(qualityOptions[it]) }
                }
            }
        }
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

/** A section's switches in a sheet at the bottom; long sections scroll, starting at the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val scroll = rememberScrollState()
        // Start scrolled to the end, so the most used rows are on screen under the thumb.
        LaunchedEffect(scroll.maxValue) { if (scroll.value == 0) scroll.scrollTo(scroll.maxValue) }
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(scroll).padding(bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            content()
        }
    }
}

/** List rows let the background light through. */
private val ClearListItem @Composable get() = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)

@Composable
private fun SettingsToggle(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(colors = ClearListItem, headlineContent = { Text(label) }, supportingContent = { Text(hint) }, trailingContent = { Switch(checked, onChange) },
        modifier = Modifier.heightIn(min = 64.dp).clickable { onChange(!checked) })
}

/** A choice with its options as chips right there, starting at the working-hand edge. */
@Composable
private fun SettingsChoice(label: String, options: List<String>, selected: Int, left: Boolean, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
        HandChips(options.mapIndexed { index, title -> ChipItem(title, index == selected) { onChange(index) } }, left, natural = true)
    }
}
