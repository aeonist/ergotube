package app.freshtuber.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import app.freshtuber.R
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object ShortsRoute
@Serializable data object SubscriptionsRoute
@Serializable data object SearchRoute
@Serializable data object DownloadsRoute
@Serializable data object LibraryRoute
@Serializable data object AccountRoute
@Serializable data object CalibrationRoute
@Serializable data object SettingsRoute
@Serializable data class PlayerRoute(val videoId: String)
@Serializable data object WelcomeRoute
@Serializable data object LoginRoute
@Serializable data class LibraryListRoute(val section: String)
@Serializable data class PlaylistRoute(val playlistId: String, val title: String, val seedVideoId: String? = null)
@Serializable data class ChannelRoute(val channelId: String)

/**
 * Top-level sections displayed on the radial menu.
 */
enum class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    Home(HomeRoute, Icons.Rounded.Home, R.string.nav_home),
    Shorts(ShortsRoute, Icons.Rounded.Slideshow, R.string.nav_shorts),
    Subscriptions(SubscriptionsRoute, Icons.Rounded.Subscriptions, R.string.nav_subscriptions),
    Search(SearchRoute, Icons.Rounded.Search, R.string.nav_search),
    Library(LibraryRoute, Icons.Rounded.VideoLibrary, R.string.nav_library),
    Account(AccountRoute, Icons.Rounded.AccountCircle, R.string.nav_account),
    Settings(SettingsRoute, Icons.Rounded.Settings, R.string.nav_settings),
}
