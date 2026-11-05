package app.freshtuber.ui.screens.search

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.freshtuber.R
import app.freshtuber.data.settings.SearchHistory
import app.freshtuber.ui.components.*
import app.freshtuber.ui.feed.FeedList
import app.freshtuber.ui.feed.FeedNavigation

/** Results read from the top; the field, its suggestions and filters sit at the bottom. */
@Composable
fun SearchScreen(
    navigation: FeedNavigation,
    onSignIn: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val left = LocalLeftHanded.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasQuery by viewModel.hasQuery.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val run: (String) -> Unit = { text ->
        query = text
        keyboard?.hide()
        submitted = text.trim()
        viewModel.search(text)
    }
    val context = LocalContext.current
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }
    val voiceAvailable = remember { voiceIntent.resolveActivity(context.packageManager) != null }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(run)
    }

    // Suggestions grow upward from the field, but never above the reach line.
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    var fieldTop by remember { mutableIntStateOf(0) }
    val rowPx = with(density) { 48.dp.toPx() }
    val room = if (fieldTop > 0 && windowHeight > 0) ((fieldTop - Reach.LINE * windowHeight) / rowPx).toInt() else 3
    val suggestions = SearchHistory.suggestions(history, query).take(room.coerceIn(1, 5))
    val showSuggestions = (focused || !hasQuery) && suggestions.isNotEmpty() && (focused || query.isEmpty())

    val classic = app.freshtuber.ui.components.LocalClassic.current
    val results: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            if (!hasQuery) {
                MessageState(stringResource(R.string.search_idle))
            } else {
                FeedList(
                    state = state,
                    navigation = navigation,
                    onRetry = viewModel::retry,
                    onLoadMore = viewModel::loadMore,
                    emptyMessage = stringResource(R.string.search_empty, submitted),
                    onSignInAgain = onSignIn,
                    videoActions = true,
                    header = if (classic) null else ({ item(key = "header") { ScreenHeader("«$submitted»") } }),
                )
            }
        }
    }
    // Classic: the field is on top as in YouTube and suggestions read downward from it.
    val shownSuggestions = if (classic) SearchHistory.suggestions(history, query).take(8) else suggestions
    val suggestionRows: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            // Ergonomic: the first suggestion sits right above the field.
            (if (classic) shownSuggestions else shownSuggestions.reversed()).forEach { text ->
                Row(Modifier.fillMaxWidth().height(48.dp).clickable { run(text) }.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (query.isEmpty()) Icons.Rounded.History else Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    val chips: @Composable () -> Unit = {
        HandChips(state.filters.map { ChipItem(it.title, it.selected) { viewModel.selectFilter(it) } }, left = classic || left)
    }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    if (classic) LaunchedEffect(Unit) { if (!hasQuery) runCatching { focus.requestFocus() } }
    val field: @Composable () -> Unit = {
        val leading: @Composable () -> Unit = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        val trailing: @Composable () -> Unit = {
            Row {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_clear))
                }
                if (voiceAvailable) IconButton(onClick = { runCatching { voice.launch(voiceIntent) } }) {
                    Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.search_voice))
                }
            }
        }
        val handLeft = left && !classic
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (classic) 6.dp else 8.dp)
                .then(if (classic) Modifier.height(52.dp) else Modifier)
                .focusRequester(focus)
                .onGloballyPositioned { fieldTop = it.positionInWindow().y.toInt() }
                .onFocusChanged { focused = it.isFocused },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            // The voice button sits on the working-hand side.
            leadingIcon = if (handLeft) trailing else leading,
            trailingIcon = if (handLeft) leading else trailing,
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { run(query) }),
        )
    }
    val suggestionsVisible = (focused || !hasQuery) && shownSuggestions.isNotEmpty() && (focused || query.isEmpty())
    if (classic) Column(Modifier.fillMaxSize()) {
        field()
        if (suggestionsVisible) Box(Modifier.weight(1f)) { suggestionRows() }
        else {
            if (hasQuery && state.filters.size > 1) chips()
            results(Modifier.weight(1f))
        }
    } else Column(Modifier.fillMaxSize()) {
        results(Modifier.weight(1f))
        BottomBar {
            if (showSuggestions) suggestionRows()
            else if (hasQuery && state.filters.size > 1) chips()
            field()
        }
    }
}
