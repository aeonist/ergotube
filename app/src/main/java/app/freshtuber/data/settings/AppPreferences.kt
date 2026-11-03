package app.freshtuber.data.settings

/** Stable preference keys shared by settings and feature consumers. */
enum class AppFeature(val key: String, val default: Boolean) {
    DeArrow("dearrow", true), Dislikes("return_dislikes", true), SponsorBlock("sponsor_block", true),
    Gestures("gesture_controls", true), Autoplay("autoplay_next", true), PictureInPicture("pip_on_leave", true),
    BackgroundPlayback("background_playback", true), ShortsTab("shorts_tab", true), ShortsInFeeds("shorts_in_feeds", false),
    Notifications("new_video_notifications", true), DownloadsWifi("downloads_wifi", true), DynamicColor("dynamic_color", false),
}

enum class ThemeMode { System, Light, Dark, Amoled }

data class AppPreferences(val features: Map<AppFeature, Boolean> = AppFeature.entries.associateWith { it.default },
    val theme: ThemeMode = ThemeMode.System, val quality: Int = 1080, val downloadQuality: Int = 720) {
    fun enabled(feature: AppFeature) = features[feature] ?: feature.default
}
