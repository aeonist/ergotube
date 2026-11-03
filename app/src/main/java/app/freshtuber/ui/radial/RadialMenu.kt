package app.freshtuber.ui.radial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.ui.theme.BrandColors
import kotlinx.coroutines.delay

data class RadialItem(
    val id: String,
    val title: String,
    val icon: ImageVector? = null,
    val selected: Boolean = false,
    val children: List<RadialItem> = emptyList(),
    val shortTitle: String = title,
    val enabled: Boolean = true,
    val action: () -> Unit,
)

/** Keeps the original pointer alive while a launcher opens a full-screen menu. */
object RadialDrag {
    var point by mutableStateOf<Offset?>(null)
    var pressed by mutableStateOf(false)
}

fun Modifier.radialLauncher(open: () -> Unit): Modifier = composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val launch by rememberUpdatedState(open)
    onGloballyPositioned { origin = it.positionInRoot() }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            RadialDrag.point = origin + down.position
            RadialDrag.pressed = true
            launch()
            try {
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    RadialDrag.point = origin + change.position
                    change.consume()
                    if (!change.pressed) break
                } while (true)
            } finally { RadialDrag.pressed = false }
        }
    }
}

/** Screen-owned filters join the shell without moving feed/account work into navigation. */
class RadialFilters {
    var items by mutableStateOf<List<RadialItem>>(emptyList())
        private set
    fun set(items: List<RadialItem>) { this.items = items }
    fun clear() { items = emptyList() }
}
val LocalRadialFilters = staticCompositionLocalOf { RadialFilters() }

@Composable
fun PublishRadialFilters(items: List<RadialItem>) {
    val host = LocalRadialFilters.current
    SideEffect { host.set(items) }
    // Home stays in the navigation back stack; its filters remain reachable from other sections.
}

/** One visible, labeled level. A section replaces the current level instead of adding a ring. */
@Composable
fun RadialMenu(
    items: List<RadialItem>,
    leftHanded: Boolean,
    arc: ThumbArc,
    modifier: Modifier = Modifier,
    autoHideMillis: Long? = null,
    onInteraction: () -> Unit = {},
    onExpandedChange: (Boolean) -> Unit = {},
    scrimColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
    contentInsets: WindowInsets = WindowInsets.safeDrawing.union(WindowInsets.safeGestures),
    initiallyExpanded: Boolean = false,
    showAnchor: Boolean = true,
    closeOnAction: Boolean = true,
    dismissOnOutside: Boolean = true,
    showCaption: Boolean = true,
    heading: String? = null,
    instruction: String? = null,
    anchorRoot: Offset? = null,
    anchorMargin: Float = 40f,
) {
    var menuOrigin by remember { mutableStateOf(Offset.Zero) }
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var path by remember { mutableStateOf<List<String>>(emptyList()) }
    var hovered by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var interactions by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dwellAction by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val interact by rememberUpdatedState(onInteraction)
    val notifyExpanded by rememberUpdatedState(onExpandedChange)
    LaunchedEffect(expanded) {
        notifyExpanded(expanded)
        if (!expanded) { path = emptyList(); hovered = null; page = 0 }
    }
    DisposableEffect(Unit) { onDispose { notifyExpanded(false) } }
    val openLabel = stringResource(R.string.radial_open)
    val closeLabel = stringResource(R.string.radial_close)
    val previousLabel = stringResource(R.string.radial_previous)
    val nextLabel = stringResource(R.string.radial_next)
    val backLabel = stringResource(R.string.action_back)
    BackHandler(expanded) { if (path.isNotEmpty()) { path = path.dropLast(1); page = 0 } else expanded = false }
    LaunchedEffect(expanded, interactions, dragging) {
        if (expanded && !dragging && autoHideMillis != null) { delay(autoHideMillis); expanded = false }
    }
    val opacity by animateFloatAsState(if (expanded) 1f else 0f, label = "radial")
    LaunchedEffect(hovered, dragging) {
        if (dragging && hovered != null) {
            delay(300)
            dwellAction?.invoke(hovered!!)
        }
    }
    Box(modifier.fillMaxSize()) {
        if ((expanded || opacity > 0f) && dismissOnOutside) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = opacity }.background(scrimColor)
                .clickable(onClickLabel = closeLabel) { expanded = false })
        }
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(contentInsets).onGloballyPositioned { menuOrigin = it.positionInRoot() }) {
            val roomy = maxHeight > 400.dp
            val branch = path.fold(null as RadialItem?) { parent, id -> (parent?.children ?: items).firstOrNull { it.id == id } }
            val all = if (branch == null) items else listOf(
                RadialItem("level-back", backLabel, Icons.AutoMirrored.Rounded.NavigateBefore) { path = path.dropLast(1); page = 0 }
            ) + branch.children
            val preferredTarget = (64f * density.fontScale).coerceIn(64f, 96f)
            val origin = anchorRoot?.let { ArcPoint((it.x - menuOrigin.x) / density.density, (it.y - menuOrigin.y) / density.density) }
            val capacity = 6
            val layout = remember(maxWidth, maxHeight, leftHanded, arc, preferredTarget, origin, anchorMargin) {
                RadialGeometry.menuLayout(maxWidth.value, maxHeight.value, leftHanded, arc, preferredTarget, origin, anchorMargin)
            }
            val target = layout.outerTarget
            val chunks = mutableListOf<List<RadialItem>>()
            if (all.size <= capacity) chunks += all
            else {
                var offset = 0
                while (offset < all.size) {
                    val free = capacity - if (chunks.isEmpty()) 0 else 1
                    val count = free - if (all.size - offset > free) 1 else 0
                    chunks += all.drop(offset).take(count)
                    offset += count
                }
            }
            val pages = chunks.size.coerceAtLeast(1)
            val currentPage = page.coerceIn(0, pages - 1)
            val visible = buildList {
                if (currentPage > 0) add(RadialItem("page-back", previousLabel, Icons.AutoMirrored.Rounded.NavigateBefore,
                    shortTitle = stringResource(R.string.radial_previous_short)) { page = currentPage - 1 })
                addAll(chunks.getOrElse(currentPage) { emptyList() })
                if (currentPage < pages - 1) add(RadialItem("page-next", nextLabel, Icons.AutoMirrored.Rounded.NavigateNext,
                    shortTitle = stringResource(R.string.radial_next_short)) { page = currentPage + 1 })
            }
            val points = layout.points(visible.size, outer = true)
            val currentItems by rememberUpdatedState(visible)
            val currentPoints by rememberUpdatedState(points)
            fun execute(item: RadialItem) {
                if (!item.enabled) return
                interactions++; interact(); hovered = null
                when {
                    item.id in listOf("level-back", "page-back", "page-next") -> item.action()
                    item.children.isNotEmpty() -> { path = path + item.id; page = 0 }
                    else -> { if (closeOnAction) expanded = false; item.action() }
                }
            }
            LaunchedEffect(RadialDrag.point, RadialDrag.pressed, visible.map { it.id }) {
                val point = RadialDrag.point
                if (point != null && expanded) {
                    val relative = point - menuOrigin
                    val hit = RadialGeometry.hit(ArcPoint(relative.x / density.density, relative.y / density.density), points, target / 2)
                    val item = hit?.let { visible[it] }
                    dragging = RadialDrag.pressed
                    hovered = item?.id
                    if (!RadialDrag.pressed) {
                        RadialDrag.point = null
                        item?.let(::execute)
                    }
                }
            }
            SideEffect {
                dwellAction = { id -> currentItems.firstOrNull { it.id == id }?.let { item ->
                    if (item.children.isNotEmpty() || item.id in listOf("level-back", "page-back", "page-next")) execute(item)
                } }
            }
            fun Modifier.at(point: ArcPoint, size: Float): Modifier = offset {
                IntOffset(with(density) { (point.x - size / 2).dp.roundToPx() }, with(density) { (point.y - size / 2).dp.roundToPx() })
            }.size(size.dp)
            if (expanded || opacity > 0f) {
                if (showCaption && maxHeight > 200.dp) Column(
                    Modifier.align(Alignment.TopCenter).padding(24.dp).graphicsLayer { alpha = opacity },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val pageContext = if (all.size > capacity) stringResource(R.string.radial_page, currentPage + 1, pages) else null
                    Text(listOfNotNull(branch?.title?.substringBefore('\n') ?: heading ?: stringResource(R.string.radial_sections), pageContext).joinToString(" · "), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                    if (roomy) Text(instruction ?: stringResource(if (branch == null) R.string.radial_hint else R.string.radial_choose_action), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
                visible.forEachIndexed { index, item ->
                    // Current location is outlined; only the finger's live target is filled.
                    val active = item.id == hovered
                    Surface(
                        onClick = { execute(item) },
                        enabled = item.enabled,
                        modifier = Modifier.at(points[index], target).graphicsLayer { alpha = opacity }
                            .semantics { contentDescription = item.title; selected = item.selected },
                        shape = RoundedCornerShape(20.dp),
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (!item.enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        border = if (item.selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null,
                    ) {
                        Column(Modifier.padding(if (target < 56f) 1.dp else 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            item.icon?.let { Icon(it, null, Modifier.size(if (target < 56f) 16.dp else 20.dp)) }
                            val compactStyle = if (target < 56f) labelStyle.copy(fontSize = labelStyle.fontSize * 0.875f, lineHeight = labelStyle.lineHeight * 0.875f) else labelStyle
                            val longestWord = item.shortTitle.split(Regex("\\s+")).maxOfOrNull {
                                textMeasurer.measure(AnnotatedString(it), compactStyle, softWrap = false).size.width
                            } ?: 1
                            val availableLabelWidth = with(density) { (target - if (target < 56f) 2f else 8f).dp.toPx() }
                            val labelFit = (availableLabelWidth * 0.94f / longestWord.coerceAtLeast(1)).coerceAtMost(1f)
                            Text(item.shortTitle, style = compactStyle.copy(fontSize = compactStyle.fontSize * labelFit), textAlign = TextAlign.Center,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (showAnchor) Box(
                Modifier.at(layout.anchor, 56f).clip(CircleShape).background(BrandColors.Red)
                    .semantics {
                        contentDescription = if (expanded) closeLabel else openLabel; role = Role.Button
                        onClick { expanded = !expanded; interact(); true }
                    }
                    .pointerInput(layout) {
                        awaitEachGesture {
                            val down = awaitFirstDown(); down.consume()
                            val wasOpen = expanded; expanded = true; dragging = true; interactions++; interact()
                            var moved = false
                            var released: RadialItem? = null
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                    val p = ArcPoint(layout.anchor.x + with(density) { (change.position.x - size.width / 2).toDp().value },
                                        layout.anchor.y + with(density) { (change.position.y - size.height / 2).toDp().value })
                                    released = RadialGeometry.hit(p, currentPoints, target / 2)?.let { currentItems[it] }
                                    if (released?.id != hovered) {
                                        hovered = released?.id
                                        if (hovered != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    change.consume()
                                    if (!change.pressed) break
                                } while (true)
                                if (moved) { released?.let(::execute) ?: run { expanded = false } }
                                else if (wasOpen) expanded = false
                            } finally { dragging = false }
                        }
                    }, contentAlignment = Alignment.Center,
            ) { Icon(if (expanded) Icons.Rounded.Close else Icons.Rounded.Menu, null, tint = Color.White) }
        }
    }
}
