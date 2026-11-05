package app.freshtuber.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object ShortsRoute
@Serializable data object SubscriptionsRoute
@Serializable data object SearchRoute
@Serializable data object DownloadsRoute
@Serializable data object AccountRoute
@Serializable data object SettingsRoute
@Serializable data class PlayerRoute(val videoId: String)
@Serializable data object WelcomeRoute
@Serializable data object LoginRoute
@Serializable data class LibraryListRoute(val section: String)
@Serializable data class PlaylistRoute(val playlistId: String, val title: String, val seedVideoId: String? = null)
@Serializable data class ChannelRoute(val channelId: String)
/** Shorts opened from a shelf: these Shorts in order, starting at [start]. */
@Serializable data class ShortsViewerRoute(val videoIds: List<String>, val thumbnails: List<String>, val start: Int)
