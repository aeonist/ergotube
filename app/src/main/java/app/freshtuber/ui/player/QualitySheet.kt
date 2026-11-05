package app.freshtuber.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.player.PlayerController
import app.freshtuber.player.PlayerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(state: PlayerState, controller: PlayerController, onDismiss: () -> Unit) {
    val now = state.nowPlaying ?: return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.player_quality), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            if (!now.isShort && !now.isOffline && !now.isLive) QualityRow(stringResource(R.string.player_quality_auto), now.qualityAuto) {
                controller.selectAutoQuality(); onDismiss()
            }
            now.qualities.forEachIndexed { index, track ->
                QualityRow(track.label, !now.qualityAuto && now.qualityIndex == index) { controller.selectQuality(index); onDismiss() }
            }
        }
    }
}

@Composable
private fun QualityRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Surface(onClick = onSelect, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected, onClick = null)
            Text(label, modifier = Modifier.padding(start = 16.dp))
        }
    }
}
