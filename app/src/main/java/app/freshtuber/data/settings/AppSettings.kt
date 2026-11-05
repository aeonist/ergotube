package app.freshtuber.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Non-sensitive app settings. Account data never goes here. */
@Singleton
class AppSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val preferences: Flow<AppPreferences> = context.settingsStore.data.map { data ->
        AppPreferences(features = AppFeature.entries.associateWith { data[booleanPreferencesKey(it.key)] ?: it.default },
            theme = runCatching { ThemeMode.valueOf(data[THEME] ?: "System") }.getOrDefault(ThemeMode.System),
            quality = data[QUALITY]?.coerceIn(144, 4320) ?: 1080,
            wifiQuality = data[WIFI_QUALITY]?.let(::validQuality) ?: data[QUALITY] ?: 1080,
            mobileQuality = data[MOBILE_QUALITY]?.let(::validQuality) ?: data[QUALITY] ?: 1080,
            dockWidth = (data[DOCK_WIDTH] ?: 100).coerceIn(0, 100),
            downloadQuality = data[DOWNLOAD_QUALITY]?.coerceIn(144, 4320) ?: 720,
            motion = runCatching { BackgroundMotion.valueOf(data[MOTION] ?: "Always") }.getOrDefault(BackgroundMotion.Always),
            style = runCatching { UiStyle.valueOf(data[STYLE] ?: "Ergonomic") }.getOrDefault(UiStyle.Ergonomic))
    }
    val backgroundPlayback: Flow<Boolean> = context.settingsStore.data.map { it[booleanPreferencesKey(AppFeature.BackgroundPlayback.key)] ?: true }
    val shortsEnabled: Flow<Boolean> = context.settingsStore.data.map { it[booleanPreferencesKey(AppFeature.ShortsTab.key)] ?: true }
    val downloadWifiOnly: Flow<Boolean> = context.settingsStore.data.map { it[booleanPreferencesKey(AppFeature.DownloadsWifi.key)] ?: true }
    val downloadQuality: Flow<Int> = context.settingsStore.data.map { it[DOWNLOAD_QUALITY]?.coerceIn(144, 4320) ?: 720 }
    suspend fun setFeature(feature: AppFeature, enabled: Boolean) { context.settingsStore.edit { it[booleanPreferencesKey(feature.key)] = enabled } }
    suspend fun setMotion(motion: BackgroundMotion) { context.settingsStore.edit { it[MOTION] = motion.name } }
    suspend fun setTheme(theme: ThemeMode) { context.settingsStore.edit { it[THEME] = theme.name } }
    suspend fun setStyle(style: UiStyle) { context.settingsStore.edit { it[STYLE] = style.name } }
    suspend fun setDownloadQuality(height: Int) { context.settingsStore.edit { it[DOWNLOAD_QUALITY] = height.coerceIn(144, 4320) } }
    suspend fun setDockWidth(percent: Int) { context.settingsStore.edit { it[DOCK_WIDTH] = percent.coerceIn(0, 100) } }
    suspend fun setPlaybackQuality(wifi: Boolean, height: Int) {
        context.settingsStore.edit { it[if (wifi) WIFI_QUALITY else MOBILE_QUALITY] = validQuality(height) }
    }
    suspend fun resetHiddenVideos() { context.settingsStore.edit { it.remove(HIDDEN_VIDEOS) } }

    val notificationsEnabled: Flow<Boolean> = context.settingsStore.data.map { it[NOTIFICATIONS] ?: true }
    suspend fun setNotifications(enabled: Boolean) { context.settingsStore.edit { it[NOTIFICATIONS] = enabled } }

    val hiddenVideos: Flow<Set<String>> = context.settingsStore.data.map { it[HIDDEN_VIDEOS] ?: emptySet() }
    suspend fun hideVideo(id: String) { context.settingsStore.edit { it[HIDDEN_VIDEOS] = (it[HIDDEN_VIDEOS].orEmpty() + id) } }

    /** True once the user signed in or chose to continue without an account. */
    val welcomeDone: Flow<Boolean> = context.settingsStore.data.map { it[WELCOME_DONE] == true }

    suspend fun markWelcomeDone() {
        context.settingsStore.edit { it[WELCOME_DONE] = true }
    }

    /** DeArrow community titles and thumbnails. Settings screen arrives in stage 7. */
    val deArrowEnabled: Flow<Boolean> = context.settingsStore.data.map { it[DEARROW] ?: true }

    /** Return YouTube Dislike counts on the video page. */
    val dislikesEnabled: Flow<Boolean> = context.settingsStore.data.map { it[DISLIKES] ?: true }

    /** Shorts inside Home and Subscriptions; off by default, they have their own tab. */
    val shortsInFeeds: Flow<Boolean> = context.settingsStore.data.map { it[SHORTS_IN_FEEDS] ?: false }

    /** SponsorBlock: skip sponsor segments, self-promotion and "subscribe" reminders. */
    val sponsorBlockEnabled: Flow<Boolean> = context.settingsStore.data.map { it[SPONSOR_BLOCK] ?: true }

    /** Brightness (left) and volume (right) by swiping up and down on the video. */
    val gestureControls: Flow<Boolean> = context.settingsStore.data.map { it[GESTURES] ?: true }

    /** Portrait watch page: the video stays on top (true) or scrolls away with the list. */
    val videoPinned: Flow<Boolean> = context.settingsStore.data.map { it[VIDEO_PINNED] ?: true }
    suspend fun setVideoPinned(pinned: Boolean) { context.settingsStore.edit { it[VIDEO_PINNED] = pinned } }

    /** Play the next suggested video when one ends. */
    val autoplayNext: Flow<Boolean> = context.settingsStore.data.map { it[AUTOPLAY] ?: true }

    /** Shrink the video into a floating window when leaving the app. */
    val pipOnLeave: Flow<Boolean> = context.settingsStore.data.map { it[PIP] ?: true }

    /** Highest quality picked automatically, in lines (1080 = 1080p). */
    val preferredQuality: Flow<Int> = context.settingsStore.data.map { it[QUALITY]?.coerceIn(144, 4320) ?: 1080 }

    suspend fun setPreferredQuality(height: Int) {
        context.settingsStore.edit { it[QUALITY] = height.coerceIn(144, 4320) }
    }

    val leftHanded: Flow<Boolean> = context.settingsStore.data.map { it[LEFT_HAND] ?: false }
    suspend fun setLeftHanded(enabled: Boolean) {
        context.settingsStore.edit { it[LEFT_HAND] = enabled }
    }

    /** Recent searches, newest first; stays on the device. */
    val searchHistory: Flow<List<String>> = context.settingsStore.data.map { SearchHistory.decode(it[SEARCH_HISTORY]) }
    suspend fun rememberSearch(query: String) {
        context.settingsStore.edit { it[SEARCH_HISTORY] = SearchHistory.encode(SearchHistory.add(SearchHistory.decode(it[SEARCH_HISTORY]), query)) }
    }
    suspend fun unhideVideo(id: String) { context.settingsStore.edit { it[HIDDEN_VIDEOS] = (it[HIDDEN_VIDEOS].orEmpty() - id) } }

    private fun validQuality(height: Int) = if (height == 0) 0 else height.coerceIn(144, 4320)

    private companion object {
        val WIFI_QUALITY = intPreferencesKey("wifi_quality")
        val MOBILE_QUALITY = intPreferencesKey("mobile_quality")
        val DOCK_WIDTH = intPreferencesKey("dock_width")
        val THEME = stringPreferencesKey("theme_mode")
        val STYLE = stringPreferencesKey("ui_style")
        val MOTION = stringPreferencesKey("liquid_motion")
        val DOWNLOAD_QUALITY = intPreferencesKey("download_quality")
        val NOTIFICATIONS = booleanPreferencesKey("new_video_notifications")
        val HIDDEN_VIDEOS = androidx.datastore.preferences.core.stringSetPreferencesKey("hidden_videos")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
        val LEFT_HAND = booleanPreferencesKey("left_hand")
        val SPONSOR_BLOCK = booleanPreferencesKey("sponsor_block")
        val GESTURES = booleanPreferencesKey("gesture_controls")
        val AUTOPLAY = booleanPreferencesKey("autoplay_next")
        val PIP = booleanPreferencesKey("pip_on_leave")
        val QUALITY = intPreferencesKey("preferred_quality")
        val WELCOME_DONE = booleanPreferencesKey("welcome_done")
        val DEARROW = booleanPreferencesKey("dearrow")
        val DISLIKES = booleanPreferencesKey("return_dislikes")
        val SHORTS_IN_FEEDS = booleanPreferencesKey("shorts_in_feeds")
        val VIDEO_PINNED = booleanPreferencesKey("video_pinned")
    }
}
