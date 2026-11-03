package app.freshtuber.ui.screens.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.ui.components.FanMark

/**
 * First launch. The choice sits at the bottom, under the thumb; the mark and name take the top.
 */
@Composable
fun WelcomeScreen(onSignIn: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FanMark(Modifier.size(96.dp))
            Spacer(Modifier.size(24.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Button(
            onClick = onSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.welcome_sign_in), style = MaterialTheme.typography.titleMedium)
        }
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.welcome_skip), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            stringResource(R.string.welcome_risk),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
        )
    }
}
