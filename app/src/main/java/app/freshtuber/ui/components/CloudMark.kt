package app.freshtuber.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import app.freshtuber.R

/** Approved cloud-sofa silhouette, shared with the launcher and notification icons. */
@Composable
fun CloudMark(modifier: Modifier = Modifier) {
    val tint = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0xFFF3C39E)
    } else {
        Color(0xFF572A35)
    }
    Icon(painterResource(R.drawable.ic_brand), contentDescription = null, modifier = modifier, tint = tint)
}
