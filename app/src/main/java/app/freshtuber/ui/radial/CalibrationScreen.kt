package app.freshtuber.ui.radial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.freshtuber.R
import app.freshtuber.ui.screens.settings.MenuSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(onBack: () -> Unit, viewModel: MenuSettingsViewModel = hiltViewModel()) {
    val left by viewModel.leftHanded.collectAsStateWithLifecycle()
    val saved by viewModel.arc.collectAsStateWithLifecycle()
    var result by remember { mutableStateOf<ThumbArc?>(null) }
    var failed by remember { mutableStateOf(false) }
    var samples by remember { mutableStateOf<List<ArcPoint>>(emptyList()) }
    val density = LocalDensity.current
    val foreground = MaterialTheme.colorScheme.onSurface
    val guide = MaterialTheme.colorScheme.outline
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Same full-window coordinate space and gesture insets as the actual menu anchor.
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.safeGestures))) {
            val layout = RadialGeometry.singleLevelLayout(maxWidth.value, maxHeight.value, left, result ?: saved, 72f * density.fontScale)
            val anchor = layout.anchor
            Canvas(Modifier.fillMaxSize().pointerInput(left, anchor) {
                detectDragGestures(
                    onDragStart = { p ->
                        result = null; failed = false
                        samples = listOf(ArcPoint(with(density) { p.x.toDp().value }, with(density) { p.y.toDp().value }))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        samples = samples + ArcPoint(with(density) { change.position.x.toDp().value }, with(density) { change.position.y.toDp().value })
                    },
                    onDragEnd = { result = RadialGeometry.calibrate(anchor, samples, left); failed = result == null },
                    onDragCancel = { samples = emptyList() },
                )
            }) {
                val center = Offset(anchor.x.dp.toPx(), anchor.y.dp.toPx())
                val radius = layout.outerRadius.dp.toPx()
                val vertical = layout.outerVerticalRadius.dp.toPx()
                drawArc(guide, if (left) -(layout.startAngle + layout.sweep) else 180f + layout.startAngle, layout.sweep, false,
                    topLeft = center - Offset(radius, vertical), size = Size(radius * 2, vertical * 2), style = Stroke(2.dp.toPx()))
                drawCircle(guide, 28.dp.toPx(), center)
                layout.points(4, outer = true).forEach { p ->
                    drawCircle(guide, 6.dp.toPx(), Offset(p.x.dp.toPx(), p.y.dp.toPx()))
                }
                samples.zipWithNext().forEach { (a, b) ->
                    drawLine(foreground, Offset(a.x.dp.toPx(), a.y.dp.toPx()), Offset(b.x.dp.toPx(), b.y.dp.toPx()),
                        strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        Column {
            TopAppBar(title = { Text(stringResource(R.string.radial_calibrate)) }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
            })
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.radial_calibration_hint), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(when { result != null -> R.string.radial_calibration_ready; failed -> R.string.radial_calibration_retry; else -> R.string.radial_calibration_idle }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { result?.let { viewModel.saveArc(it, onBack) } }, enabled = result != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.radial_save)) }
            }
        }
    }
}
