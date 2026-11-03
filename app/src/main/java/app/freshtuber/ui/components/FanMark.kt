package app.freshtuber.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import app.freshtuber.ui.theme.BrandColors

/** The ErgoTube mark: a quarter-disc fan opening from the thumb's corner, with a play triangle. */
@Composable
fun FanMark(modifier: Modifier = Modifier, color: Color = BrandColors.Red) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Quarter of a circle centered on the bottom-right corner.
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = true,
            topLeft = Offset.Zero,
            size = Size(w * 2, h * 2),
        )
        val play = Path().apply {
            moveTo(w * 0.48f, h * 0.41f)
            lineTo(w * 0.48f, h * 0.82f)
            lineTo(w * 0.82f, h * 0.61f)
            close()
        }
        drawPath(play, Color.White)
    }
}
