package app.freshtuber.ui.radial

import kotlin.math.*

/** All lengths in dp; independent of Compose so layout and calibration can be tested. */
data class ArcPoint(val x: Float, val y: Float) {
    fun distance(other: ArcPoint): Float = hypot(x - other.x, y - other.y)
}

/** Independent axes; the old radius remains the horizontal axis for saved-setting migration. */
data class ThumbArc(val radius: Float = 220f, val sweep: Float = 90f, val verticalRadius: Float = 300f) {
    fun normalized() = ThumbArc(
        radius = if (radius.isFinite()) radius.coerceIn(80f, 300f) else 220f,
        sweep = if (sweep.isFinite()) sweep.coerceIn(70f, 90f) else 90f,
        verticalRadius = if (verticalRadius.isFinite()) verticalRadius.coerceIn(80f, 420f) else 300f,
    )
}

data class ArcLayout(
    val anchor: ArcPoint,
    val radius: Float,
    val outerRadius: Float,
    val sweep: Float,
    val leftHanded: Boolean,
    val outerTarget: Float = 64f,
    val verticalRadius: Float = radius,
    val outerVerticalRadius: Float = outerRadius,
    val targetGap: Float = 16f,
    val rectangularTargets: Boolean = false,
    val startAngle: Float = 45f - sweep / 2,
) {
    fun points(count: Int, outer: Boolean = false): List<ArcPoint> {
        if (count <= 0) return emptyList()
        val rx = if (outer) outerRadius else radius
        val ry = if (outer) outerVerticalRadius else verticalRadius
        val start = Math.toRadians(startAngle.toDouble())
        val span = Math.toRadians(sweep.toDouble())
        fun point(t: Double) = ArcPoint(anchor.x + (if (leftHanded) 1 else -1) * rx * cos(t).toFloat(), anchor.y - ry * sin(t).toFloat())
        val curve = (0..128).map { point(start + span * it / 128) }
        if (rectangularTargets && count == 1) return listOf(curve.first())
        if (rectangularTargets && count > 1) {
            fun spaced(spacing: Float): List<ArcPoint>? {
                val result = mutableListOf(curve.first())
                var cursor = 0
                repeat(count - 2) {
                    val previous = result.last()
                    cursor = (cursor + 1..curve.lastIndex).firstOrNull { i ->
                        abs(curve[i].x - previous.x) >= spacing || abs(curve[i].y - previous.y) >= spacing
                    } ?: return null
                    result += curve[cursor]
                }
                val previous = result.last()
                if (abs(curve.last().x - previous.x) < spacing && abs(curve.last().y - previous.y) < spacing) return null
                val next = (cursor + 1..curve.lastIndex).firstOrNull { i ->
                    abs(curve[i].x - previous.x) >= spacing || abs(curve[i].y - previous.y) >= spacing
                } ?: return null
                return result + curve[next]
            }
            spaced(outerTarget + targetGap)?.let { return it }

        }
        val distances = FloatArray(curve.size)
        for (i in 1 until curve.size) distances[i] = distances[i - 1] + curve[i].distance(curve[i - 1])
        return (0 until count).map { index ->
            val distance = distances.last() * if (count == 1) 0.5f else index.toFloat() / (count - 1)
            val end = distances.indexOfFirst { it >= distance }.coerceAtLeast(1)
            val fraction = (distance - distances[end - 1]) / (distances[end] - distances[end - 1])
            point(start + span * (end - 1 + fraction) / 128)
        }
    }

    /** Check actual ellipse chords instead of assuming circular arc length. */
    fun capacity(outer: Boolean = false): Int {
        val spacing = if (outer) outerTarget + targetGap else 56f
        return (12 downTo 2).firstOrNull { count ->
            points(count, outer).zipWithNext().all { (a, b) ->
                if (rectangularTargets) abs(a.x - b.x) >= spacing || abs(a.y - b.y) >= spacing
                else a.distance(b) >= spacing
            }
        } ?: 1
    }
}

object RadialGeometry {
    fun layout(width: Float, height: Float, leftHanded: Boolean, arc: ThumbArc, hasChildren: Boolean, outerTarget: Float = 64f): ArcLayout {
        val safe = arc.normalized()
        val target = if (outerTarget.isFinite()) outerTarget.coerceIn(64f, 128f) else 64f
        val margin = if (hasChildren) max(40f, target / 2 + 8f) else 40f
        val anchor = ArcPoint(if (leftHanded) margin else width - margin, height - margin)
        val separation = 24f + target / 2 + 8f
        val edge = if (hasChildren) target / 2 + 8f else 40f
        val availableX = (width - margin - edge).coerceAtLeast(56f)
        val availableY = (height - margin - edge).coerceAtLeast(56f)
        val outerX = min(safe.radius + separation, availableX)
        val outerY = min(safe.verticalRadius + separation, availableY)
        val innerX = if (hasChildren) (outerX - separation).coerceAtLeast(56f) else min(safe.radius, availableX)
        val innerY = if (hasChildren) (outerY - separation).coerceAtLeast(56f) else min(safe.verticalRadius, availableY)
        return ArcLayout(anchor, innerX, outerX, safe.sweep, leftHanded, target, innerY, outerY)
    }

    /** Single-level menus place centers on the measured reach, with no outer-ring offset. */
    fun singleLevelLayout(width: Float, height: Float, left: Boolean, arc: ThumbArc, target: Float = 64f, origin: ArcPoint? = null, anchorMargin: Float = 40f): ArcLayout {
        val safe = arc.normalized()
        val size = target.coerceIn(48f, 128f)
        val margin = anchorMargin
        val anchor = origin ?: ArcPoint(if (left) margin else width - margin, height - margin)
        val x = min(safe.radius, ((if (left) width - anchor.x else anchor.x) - size / 2 - 8f).coerceAtLeast(0f))
        val y = min(safe.verticalRadius, (anchor.y - size / 2 - 8f).coerceAtLeast(0f))
        val edge = size / 2 + 8.01f // Preserve the 8dp floor after floating-point rounding.
        val inward = (edge - margin).coerceAtLeast(0f)
        val start = max(45f - safe.sweep / 2, Math.toDegrees(asin((inward / y.coerceAtLeast(1f)).coerceIn(0f, 1f)).toDouble()).toFloat())
        val end = min(45f + safe.sweep / 2, Math.toDegrees(acos((inward / x.coerceAtLeast(1f)).coerceIn(0f, 1f)).toDouble()).toFloat())
        return ArcLayout(anchor, x, x, (end - start).coerceAtLeast(0f), left, size, y, y,
            targetGap = 8f, rectangularTargets = true, startAngle = start)
    }

    /** Keep six slots on an arc. Preserve horizontal reach first when a tiny ellipse is too short. */
    fun menuLayout(width: Float, height: Float, left: Boolean, arc: ThumbArc,
        preferredTarget: Float = 64f, origin: ArcPoint? = null, anchorMargin: Float = 40f): ArcLayout {
        fun measured(size: Float) = singleLevelLayout(width, height, left, arc, size, origin, anchorMargin)
        for (size in preferredTarget.toInt().coerceIn(48, 128) downTo 48) {
            val layout = measured(size.toFloat())
            if (layout.capacity(true) >= 6) return layout
        }
        val compact = measured(48f)
        val availableX = ((if (left) width - compact.anchor.x else compact.anchor.x) - 32f).coerceAtLeast(compact.radius)
        val availableY = (compact.anchor.y - 32f).coerceAtLeast(compact.verticalRadius)
        fun stretched(x: Float, y: Float) = compact.copy(radius = x, outerRadius = x,
            verticalRadius = y, outerVerticalRadius = y)
        // Add only the missing length; the anchor and page membership never change.
        fun firstFit(low: Float, high: Float, candidate: (Float) -> ArcLayout): ArcLayout? {
            if (candidate(high).capacity(true) < 6) return null
            var lower = low
            var upper = high
            repeat(12) {
                val middle = (lower + upper) / 2
                if (candidate(middle).capacity(true) >= 6) upper = middle else lower = middle
            }
            return candidate(upper)
        }
        firstFit(compact.verticalRadius, availableY) { stretched(compact.radius, it) }?.let { return it }
        firstFit(compact.radius, availableX) { stretched(it, availableY) }?.let { return it }
        // A short landscape window may also need the full quarter ellipse.
        return stretched(availableX, availableY).copy(startAngle = 0f, sweep = 90f)
    }

    fun hit(point: ArcPoint, targets: List<ArcPoint>, targetRadius: Float = 28f): Int? =
        targets.indices.minByOrNull { targets[it].distance(point) }
            ?.takeIf { targets[it].distance(point) <= targetRadius }

    /** Fit x²/a² + y²/b² = 1, then reject stray samples and refit. */
    fun calibrate(anchor: ArcPoint, samples: List<ArcPoint>, leftHanded: Boolean): ThumbArc? {
        val points = samples.mapNotNull { p ->
            val x = (p.x - anchor.x) * if (leftHanded) 1 else -1
            val y = anchor.y - p.y
            if (x < 0 || y < 0 || hypot(x, y) !in 100f..460f) null else ArcPoint(x, y)
        }
        if (points.size < 8) return null
        val angles = points.map { Math.toDegrees(atan2(it.y, it.x).toDouble()).toFloat() }
        val sweep = angles.max() - angles.min()
        if (sweep < 45f) return null
        fun fit(values: List<ArcPoint>): Pair<Float, Float>? {
            var xx = 0.0; var xy = 0.0; var yy = 0.0; var x = 0.0; var y = 0.0
            for (p in values) {
                val u = p.x.toDouble().pow(2); val v = p.y.toDouble().pow(2)
                xx += u * u; xy += u * v; yy += v * v; x += u; y += v
            }
            val det = xx * yy - xy * xy
            if (det <= 0) return null
            val a = (x * yy - y * xy) / det; val b = (y * xx - x * xy) / det
            if (a <= 0 || b <= 0) return null
            return (1 / sqrt(a)).toFloat() to (1 / sqrt(b)).toFloat()
        }
        // A median per-angle reach gives a robust starting ellipse before the least-squares fit.
        val nearSide = points.filter { it.y <= it.x * 0.45f }.map { it.x }.sorted()
        val nearTop = points.filter { it.x <= it.y * 0.45f }.map { it.y }.sorted()
        val initial = if (nearSide.isNotEmpty() && nearTop.isNotEmpty())
            nearSide[nearSide.size / 2] to nearTop[nearTop.size / 2] else fit(points) ?: return null
        val kept = points.filter { p -> abs((p.x / initial.first).pow(2) + (p.y / initial.second).pow(2) - 1f) < 0.25f }
        val axes = fit(kept.takeIf { it.size >= 8 } ?: points) ?: return null
        return ThumbArc(axes.first, sweep, axes.second).normalized()
    }
}
