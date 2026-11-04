package app.freshtuber.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.freshtuber.R
import app.freshtuber.data.account.AccountProfile
import app.freshtuber.data.account.AccountState
import app.freshtuber.data.account.ChannelIdentity
import coil3.compose.AsyncImage
import app.freshtuber.ui.components.*
import androidx.compose.material.icons.automirrored.rounded.Logout

@Composable
fun AccountScreen(
    onSignIn: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val left = LocalLeftHanded.current
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (val current = state) {
                AccountState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AccountState.SignedOut -> SignedOutContent()
                is AccountState.SignedIn -> SignedInContent(
                    state = current,
                    onSwitchProfile = viewModel::switchProfile,
                    onSwitchChannel = viewModel::switchChannel,
                )
            }
        }
        val signedIn = state as? AccountState.SignedIn
        ContextBar(buildList {
            add(ContextAction(Icons.Rounded.PersonAdd, stringResource(if (signedIn == null) R.string.welcome_sign_in else R.string.account_add),
                primary = signedIn == null, onClick = onSignIn))
            if (signedIn != null) add(ContextAction(Icons.AutoMirrored.Rounded.Logout, stringResource(R.string.account_sign_out)) { confirmSignOut = true })
        }, left)
    }
    val active = (state as? AccountState.SignedIn)?.active
    if (confirmSignOut && active != null) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.account_sign_out_title, active.displayName())) },
            text = { Text(stringResource(R.string.account_sign_out_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut(active.id)
                }) {
                    Text(stringResource(R.string.account_sign_out), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SignedOutContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.account_signed_out_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.account_signed_out_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.welcome_risk),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SignedInContent(
    state: AccountState.SignedIn,
    onSwitchProfile: (String) -> Unit,
    onSwitchChannel: (ChannelIdentity) -> Unit,
) {
    val active = state.active

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Avatar(active.avatarUrl, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        active.displayName(),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    listOfNotNull(active.handle, active.email).firstOrNull()?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (active.channels.size > 1) {
            item { SectionTitle(stringResource(R.string.account_channels)) }
            items(active.channels, key = { "channel:${it.pageId}:${it.name}" }) { channel ->
                IdentityRow(
                    title = channel.name,
                    subtitle = channel.handle,
                    avatarUrl = channel.avatarUrl,
                    selected = channel.pageId == active.activePageId,
                    onClick = { onSwitchChannel(channel) },
                )
            }
        }

        // With a single account the header already says who it is; list accounts only to switch.
        if (state.all.size > 1) {
            item { SectionTitle(stringResource(R.string.account_google_accounts)) }
            items(state.all, key = { "profile:${it.id}" }) { profile ->
                IdentityRow(
                    title = profile.displayName(),
                    subtitle = profile.email,
                    avatarUrl = profile.avatarUrl,
                    selected = profile.id == active.id,
                    onClick = { onSwitchProfile(profile.id) },
                )
            }
        }
        item {
            Text(
                stringResource(R.string.account_storage_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun AccountProfile.displayName(): String =
    name ?: email ?: stringResource(R.string.account_unnamed)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun IdentityRow(
    title: String,
    subtitle: String?,
    avatarUrl: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { Avatar(avatarUrl, 40.dp) },
        trailingContent = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.account_selected)) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(enabled = !selected, onClick = onClick),
    )
}

@Composable
private fun Avatar(url: String?, size: Dp) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}
