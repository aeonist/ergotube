package app.freshtuber.ui.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.freshtuber.R
import app.freshtuber.data.music.MusicSource
import app.freshtuber.data.music.MusicTrack
import app.freshtuber.data.settings.AppFeature
import app.freshtuber.ui.components.BottomBar
import app.freshtuber.ui.components.LocalLeftHanded
import app.freshtuber.ui.components.MessageState
import app.freshtuber.ui.components.ScreenHeader

/**
 * Song search inside the common Search: the field at the bottom like the video search.
 * «У тебя» (phone and library) first, then «В YouTube»; offline only what is on the phone.
 */
@Composable
fun MusicSearchScreen(viewModel: MusicViewModel = hiltViewModel()) {
    MusicNotes(viewModel)
    val left = LocalLeftHanded.current
    val navigation = LocalMusicNavigation.current
    val context = LocalContext.current
    val results by viewModel.search.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val fromBottom = !app.freshtuber.ui.theme.LocalAppPreferences.current.enabled(AppFeature.TraditionalScroll)
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val shareTitle = stringResource(R.string.music_share)
    var menu by remember { mutableStateOf<MusicTrack?>(null) }
    menu?.let { TrackMenu(it, viewModel, onDismiss = { menu = null }) }
    LaunchedEffect(query) { kotlinx.coroutines.delay(350); viewModel.search(query) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (results.query.isEmpty()) MessageState(stringResource(R.string.music_search_idle))
            else LazyColumn(Modifier.fillMaxSize(), reverseLayout = fromBottom, contentPadding = PaddingValues(vertical = 8.dp)) {
                // Bottom-up: phone results sit nearest the field. The title always stands on top.
                if (!fromBottom) item(key = "header") { ScreenHeader("«${results.query}»") }
                if (results.phone.isNotEmpty()) {
                    if (!fromBottom) item(key = "phoneLabel") { SectionLabel(stringResource(R.string.music_search_phone)) }
                    items(results.phone.size, key = { "p" + results.phone[it].id }) { index ->
                        val track = results.phone[index]
                        TrackRow(track, originOf(track, downloaded), current = track.id == state.queue.track?.id,
                            onClick = {
                                if (track.source == MusicSource.Phone) viewModel.music.playCollection(results.phone, index, null)
                                else viewModel.music.playRadio(track)
                                navigation?.openPlayer()
                            },
                            onMore = { menu = track }, onFlick = { viewModel.flick(it, track, context, shareTitle) })
                    }
                    if (fromBottom) item(key = "phoneLabel") { SectionLabel(stringResource(R.string.music_search_phone)) }
                }
                if (results.youtube.isNotEmpty()) {
                    if (!fromBottom) item(key = "ytLabel") { SectionLabel(stringResource(R.string.music_search_youtube)) }
                    items(results.youtube.size, key = { "y" + results.youtube[it].id }) { index ->
                        val track = results.youtube[index]
                        TrackRow(track, originOf(track, downloaded), current = track.id == state.queue.track?.id,
                            onClick = { viewModel.music.playRadio(track); navigation?.openPlayer() },
                            onMore = { menu = track }, onFlick = { viewModel.flick(it, track, context, shareTitle) },
                            modifier = Modifier.lightSource(track.artwork))
                    }
                    if (fromBottom) item(key = "ytLabel") { SectionLabel(stringResource(R.string.music_search_youtube)) }
                }
                if (results.loading) item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                }
                if (!results.loading && results.phone.isEmpty() && results.youtube.isEmpty()) item(key = "empty") {
                    Box(Modifier.height(200.dp)) { MessageState(stringResource(if (!online) R.string.music_offline_library else R.string.music_search_empty)) }
                }
                if (fromBottom) item(key = "header") { ScreenHeader("«${results.query}»") }
            }
        }
        BottomBar {
            val leading: @Composable () -> Unit = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            val trailing: @Composable () -> Unit = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.action_clear)) }
            }
            TextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.music_search_hint)) },
                leadingIcon = if (left) trailing else leading, trailingIcon = if (left) leading else trailing,
                singleLine = true, shape = RoundedCornerShape(26.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus(); viewModel.search(query) }))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
}
