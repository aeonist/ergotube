package app.freshtuber.ui.screens

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.ui.components.MessageState

/** Shown where a screen only makes sense with an account. */
@Composable
fun SignInPrompt(message: String, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    MessageState(
        message = message,
        modifier = modifier,
        action = {
            Button(onClick = onSignIn, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.welcome_sign_in))
            }
        },
    )
}
