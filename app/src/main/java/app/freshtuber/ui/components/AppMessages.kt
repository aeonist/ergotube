package app.freshtuber.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * App-wide snackbar shown above the bottom bars. Lives at the root, so a message started from a
 * list card survives the card scrolling away.
 */
class AppMessages(val host: SnackbarHostState, private val scope: CoroutineScope) {
    /** [onResult] gets true when the action (for example Undo) was pressed. */
    fun show(text: String, action: String? = null, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val timeout = launch { delay(if (action != null) UNDO_MS else SHORT_MS); host.currentSnackbarData?.dismiss() }
            val result = host.showSnackbar(text, action, duration = SnackbarDuration.Indefinite)
            timeout.cancel()
            onResult(result == SnackbarResult.ActionPerformed)
        }
    }

    private companion object {
        const val UNDO_MS = 5_000L
        const val SHORT_MS = 2_500L
    }
}

val LocalAppMessages = staticCompositionLocalOf<AppMessages?> { null }
