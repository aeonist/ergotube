package app.freshtuber.ui.screens.library

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.ui.graphics.vector.ImageVector
import app.freshtuber.R
import app.freshtuber.data.feed.FeedRepository

/** The account's own YouTube lists, opened from «Моё». All need a signed-in account. */
enum class LibrarySection(
    val browseId: String,
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    History(FeedRepository.HISTORY, R.string.library_history, Icons.Rounded.History),
    Playlists(FeedRepository.PLAYLISTS, R.string.library_playlists, Icons.AutoMirrored.Rounded.PlaylistPlay),
    WatchLater(FeedRepository.WATCH_LATER, R.string.library_watch_later, Icons.Rounded.WatchLater),
    Liked(FeedRepository.LIKED, R.string.library_liked, Icons.Rounded.ThumbUp),
}
