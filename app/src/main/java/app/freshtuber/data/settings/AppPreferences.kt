package app.freshtuber.data.settings

/** Stable preference keys shared by settings and feature consumers. */
enum class AppFeature(val key: String, val default: Boolean) {
    SwapHomeMine("swap_home_mine", false),
    /**
     * Feeds, lists and the music queue read top to bottom, as in YouTube (on by default since
     * build 32, under a new key so everyone gets it once). Off: lists grow up from the thumb.
     */
    TraditionalScroll("scroll_top_down", true),
    DeArrow("dearrow", true), Dislikes("return_dislikes", true), SponsorBlock("sponsor_block", true),
    Gestures("gesture_controls", true), Autoplay("autoplay_next", true), PictureInPicture("pip_on_leave", true),
    BackgroundPlayback("background_playback", true), MiniPlayerTapOpens("mini_player_tap_opens", true),
    /** A tap on the video in the portrait player shows YouTube's own controls over it. */
    YouTubeTapControls("youtube_tap_controls", false),
    ShortsTab("shorts_tab", true), ShortsInFeeds("shorts_in_feeds", false),
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
    /** The music player's background light pulses with the sound (read from the player itself, no microphone). */
    LiquidPulse("liquid_pulse", true),
    /** «Музыка» in the main dock instead of Shorts (Shorts move to «Моё»). */
    MusicTab("music_tab", false),
    /** Song lyrics from LRCLIB; only artist, title, album and length are sent. */
    MusicLyrics("music_lyrics", true),
    /** Download «Микс дня» on Wi-Fi, within the music storage limit. */
    MusicAutoDaily("music_auto_daily", false),
    /** Download «Любимое» on Wi-Fi; these are never removed to make room. */
    MusicAutoFavorites("music_auto_favorites", false),
    /** Continue music when headphones connect again after they paused it. */
    MusicResumeOnConnect("music_resume_connect", false),
    /** Downloaded songs are saved as tagged files in the phone's Music folder, for other players too. */
    MusicToFolder("music_to_folder", true),
    /**
     * Songs from the phone's Music folder show under «С телефона» on their own. On by default:
     * the first visit to Music asks for audio access, and a refusal turns it off.
     */
    MusicShowFolder("music_show_folder", true),
    /** The app opens on «Слушать» instead of the video Home. */
    StartWithMusic("start_with_music", false),
    /** Off removes music from the app: no tab above the dock, no song line, no music search. */
    MusicInApp("music_in_app", true),
    /** The «Музыка» ear above Home's chips; off hides only the ear, music stays in the app. */
    MusicEar("music_ear", true),
    SeparateFavorites("separate_favorites", false),
}

/** Audio quality for music: the best stream, or a small one (about 50 kbps) that saves data. */
enum class MusicQuality { Best, Economy }

/** How the background light moves. */
enum class BackgroundMotion { Always, OnTransition, Off }

/** [Classic] is YouTube's own dark palette (#0F0F0F, grey chips). */
enum class ThemeMode { System, Light, Dark, Amoled, Classic }

/** Button placement only: [Ergonomic] keeps controls under the thumb; [Classic] repeats YouTube's layout. */
enum class UiStyle { Ergonomic, Classic }

data class AppPreferences(val features: Map<AppFeature, Boolean> = AppFeature.entries.associateWith { it.default },
    val theme: ThemeMode = ThemeMode.System, val quality: Int = 1080, val downloadQuality: Int = 720,
    val motion: BackgroundMotion = BackgroundMotion.Always, val style: UiStyle = UiStyle.Ergonomic,
    val wifiQuality: Int = quality, val mobileQuality: Int = quality, val dockWidth: Int = 100,
    val musicWifi: MusicQuality = MusicQuality.Best, val musicMobile: MusicQuality = MusicQuality.Economy,
    val musicDownloads: MusicQuality = MusicQuality.Best, val musicLimitGb: Int = 2) {
    fun musicQuality(wifi: Boolean): MusicQuality = if (wifi) musicWifi else musicMobile
    /** Zero means unrestricted adaptive quality; a resolution caps adaptive playback. */
    fun playbackQuality(wifi: Boolean): Int = if (wifi) wifiQuality else mobileQuality
    fun enabled(feature: AppFeature) = features[feature] ?: feature.default
    /** Only where the buttons are; colours, light and effects follow their own settings. */
    val classicLayout: Boolean get() = style == UiStyle.Classic
    /** YouTube's own dark colours; the light and its effects still follow the energy settings. */
    val youTubeColors: Boolean get() = theme == ThemeMode.Classic
}
