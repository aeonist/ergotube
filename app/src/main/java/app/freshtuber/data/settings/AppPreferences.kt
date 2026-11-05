package app.freshtuber.data.settings

/** Stable preference keys shared by settings and feature consumers. */
enum class AppFeature(val key: String, val default: Boolean) {
    SwapHomeMine("swap_home_mine", false),
    DeArrow("dearrow", true), Dislikes("return_dislikes", true), SponsorBlock("sponsor_block", true),
    Gestures("gesture_controls", true), Autoplay("autoplay_next", true), PictureInPicture("pip_on_leave", true),
    BackgroundPlayback("background_playback", true), MiniPlayerTapOpens("mini_player_tap_opens", true), ShortsTab("shorts_tab", true), ShortsInFeeds("shorts_in_feeds", false),
    Notifications("new_video_notifications", true), DownloadsWifi("downloads_wifi", true), DynamicColor("dynamic_color", false),
    /** Source Serif 4 everywhere; off switches to the system sans. */
    SerifFont("font_serif", true),
    /** Liquid drops bend the light behind them. */
    LiquidRefraction("liquid_refraction", true),
    /** Fine static grain over the background light. */
    LiquidGrain("liquid_grain", true),
    /** With the system battery saver on, the background switches to one flat color. */
    LiquidPowerSaver("liquid_power_saver", true),
    /** Energy saving: one flat color averaged from the video instead of the liquid light. */
    LiquidFlat("liquid_flat", false),
}

/** How the background light moves. */
enum class BackgroundMotion { Always, OnTransition, Off }

/** [Classic] is YouTube's own dark palette (#0F0F0F, grey chips). */
enum class ThemeMode { System, Light, Dark, Amoled, Classic }

/** Button placement only: [Ergonomic] keeps controls under the thumb; [Classic] repeats YouTube's layout. */
enum class UiStyle { Ergonomic, Classic }

data class AppPreferences(val features: Map<AppFeature, Boolean> = AppFeature.entries.associateWith { it.default },
    val theme: ThemeMode = ThemeMode.System, val quality: Int = 1080, val downloadQuality: Int = 720,
    val motion: BackgroundMotion = BackgroundMotion.Always, val style: UiStyle = UiStyle.Ergonomic,
    val wifiQuality: Int = quality, val mobileQuality: Int = quality, val dockWidth: Int = 100) {
    /** Zero means unrestricted adaptive quality; a resolution caps adaptive playback. */
    fun playbackQuality(wifi: Boolean): Int = if (wifi) wifiQuality else mobileQuality
    fun enabled(feature: AppFeature) = features[feature] ?: feature.default
    /** Only where the buttons are; colours, light and effects follow their own settings. */
    val classicLayout: Boolean get() = style == UiStyle.Classic
    /** YouTube's own dark colours; the light and its effects still follow the energy settings. */
    val youTubeColors: Boolean get() = theme == ThemeMode.Classic
}
