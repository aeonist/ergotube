package app.freshtuber.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.freshtuber.R

@Composable
fun ChannelSubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = Modifier.padding(start = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (subscribed) colors.surfaceContainerHigh else colors.onSurface,
            contentColor = if (subscribed) colors.onSurface else colors.surface,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(stringResource(if (subscribed) R.string.channel_subscribed else R.string.channel_subscribe))
    }
}
