package app.freshtuber.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.freshtuber.R
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.ui.radial.ThumbArc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MenuSettingsViewModel @Inject constructor(private val settings: AppSettings) : ViewModel() {
    val leftHanded = settings.leftHanded.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val arc = settings.thumbArc.stateIn(viewModelScope, SharingStarted.Eagerly, ThumbArc())
    val pinned = settings.videoPinned.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setPinned(value: Boolean) { viewModelScope.launch { settings.setVideoPinned(value) } }
    fun setHand(left: Boolean) { viewModelScope.launch { settings.setLeftHanded(left) } }
    fun saveArc(value: ThumbArc, onSaved: () -> Unit = {}) { viewModelScope.launch { settings.setThumbArc(value); onSaved() } }
}

@Composable
fun SettingsScreen(onCalibrate: () -> Unit, viewModel: MenuSettingsViewModel = hiltViewModel()) {
    val left by viewModel.leftHanded.collectAsStateWithLifecycle()
    val arc by viewModel.arc.collectAsStateWithLifecycle()
    var vertical by remember(arc) { mutableFloatStateOf(arc.verticalRadius) }
    var radius by remember(arc) { mutableFloatStateOf(arc.radius) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Text(stringResource(R.string.radial_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.radial_hand), style = MaterialTheme.typography.bodyMedium)
        listOf(false, true).forEach { hand ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { viewModel.setHand(hand) },
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = left == hand, onClick = { viewModel.setHand(hand) })
                Text(stringResource(if (hand) R.string.radial_left else R.string.radial_right))
            }
        }
        Button(onClick = onCalibrate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.radial_calibrate))
        }
        Text(stringResource(R.string.radial_reach), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.radial_reach_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = radius, onValueChange = { radius = it }, valueRange = 80f..300f,
            onValueChangeFinished = { viewModel.saveArc(arc.copy(radius = radius)) })
        Text(stringResource(R.string.radial_vertical_reach), style = MaterialTheme.typography.titleSmall)
        Slider(value = vertical, onValueChange = { vertical = it }, valueRange = 80f..420f,
            onValueChangeFinished = { viewModel.saveArc(arc.copy(verticalRadius = vertical)) })
        TextButton(onClick = { viewModel.saveArc(ThumbArc()) }) { Text(stringResource(R.string.radial_reset)) }
    }
}
