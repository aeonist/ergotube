package app.freshtuber.ui.screens.library

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.freshtuber.R
import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.account.AccountState
import app.freshtuber.data.feed.FeedRepository
import app.freshtuber.ui.screens.SignInPrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Cloud lists use a browse id; downloads are stored on the device. */
enum class LibrarySection(
    val browseId: String?,
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    History(FeedRepository.HISTORY, R.string.library_history, Icons.Rounded.History),
    Playlists(FeedRepository.PLAYLISTS, R.string.library_playlists, Icons.AutoMirrored.Rounded.PlaylistPlay),
    WatchLater(FeedRepository.WATCH_LATER, R.string.library_watch_later, Icons.Rounded.WatchLater),
    Liked(FeedRepository.LIKED, R.string.library_liked, Icons.Rounded.ThumbUp),
    Downloads(null, R.string.library_downloads, Icons.Rounded.Download),
}

@HiltViewModel
class LibraryViewModel @Inject constructor(accounts: AccountRepository) : ViewModel() {
    val signedIn = accounts.state
        .map { if (it is AccountState.Loading) null else it is AccountState.SignedIn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun LibraryScreen(
    onOpenSection: (LibrarySection) -> Unit,
    onSignIn: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Text(
            stringResource(R.string.nav_library),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LibrarySection.entries.forEach { section ->
            val available = section == LibrarySection.Downloads || signedIn == true
            ListItem(
                headlineContent = { Text(stringResource(section.titleRes)) },
                supportingContent = if (available) null else {
                    { Text(stringResource(R.string.action_needs_sign_in)) }
                },
                leadingContent = { Icon(section.icon, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable { if (available) onOpenSection(section) else onSignIn() },
            )
        }
    }
}
