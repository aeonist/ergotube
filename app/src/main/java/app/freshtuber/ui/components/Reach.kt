package app.freshtuber.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.freshtuber.R

/**
 * One-handed reach rules. Reading lives in the top of the screen; every fixed control sits in
 * the bottom part, pressed to the working hand.
 */
object Reach {
    /** Fixed controls start below this fraction of the screen height. */
    const val LINE = 0.55f

    /** Height of the floating navigation drop. */
    val DockHeight: Dp = 68.dp

    /** The slim dock drawn over a Short: icons only. */
    val CompactDockHeight: Dp = 50.dp
}

/** The working hand from settings; controls press to this side. */
val LocalLeftHanded = compositionLocalOf { false }

/**
 * Height of the current screen's bottom bar, so snackbars and the floating video sit above it.
 * While two screens cross-fade both have a bar; the one that reported last owns the value, and
 * the leaving one cannot clear it.
 */
class BottomBarSpace {
    var height by mutableStateOf(0.dp)
        private set
    private var owner: Any? = null

    fun report(owner: Any, value: androidx.compose.ui.unit.Dp) { this.owner = owner; height = value }

    fun clear(owner: Any) { if (this.owner === owner) { this.owner = null; height = 0.dp } }
}

val LocalBottomBarSpace = staticCompositionLocalOf { BottomBarSpace() }

/** A compact title above a list: little air above and below, the first item follows right away. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)) {
        Text(title, style = app.freshtuber.ui.theme.AppType.screenTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        subtitle?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** A screen's own controls, right above the dock. Reports its height for snackbars. */
@Composable
fun BottomBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val space = LocalBottomBarSpace.current
    val density = LocalDensity.current
    val owner = remember { Any() }
    DisposableEffect(space) { onDispose { space.clear(owner) } }
    Column(
        modifier.fillMaxWidth()
            .onSizeChanged { space.report(owner, with(density) { it.height.toDp() }) },
    ) {
        // Transparent: the background light shows through under the bar.
        content()
    }
}

data class ContextAction(
    val icon: ImageVector,
    val label: String,
    val primary: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** Sub-screen controls in one row; the first action sits under the thumb. Nothing is drawn without actions. */
@Composable
fun ContextBar(actions: List<ContextAction>, left: Boolean, modifier: Modifier = Modifier, above: (@Composable ColumnScope.() -> Unit)? = null) {
    if (actions.isEmpty() && above == null) return
    BottomBar(modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            above?.invoke(this)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, if (left) Alignment.Start else Alignment.End)) {
                handOrder(actions, left).forEach { action -> ContextButton(action, Modifier.weight(1f, fill = false).widthIn(min = 64.dp, max = 150.dp).fillMaxWidth()) }
            }
        }
    }
}

@Composable
fun ContextButton(action: ContextAction, modifier: Modifier = Modifier) {
    val container = if (action.primary) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainer
    val content = if (action.primary) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Surface(onClick = action.onClick, enabled = action.enabled, modifier = modifier.height(56.dp), shape = RoundedCornerShape(14.dp),
        color = container, contentColor = content.copy(alpha = if (action.enabled) 1f else 0.38f)) {
        Column(Modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(action.icon, null, Modifier.size(24.dp))
            Text(action.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** Visual left-to-right order: the first item lands on the working-hand side. */
fun <T> handOrder(items: List<T>, left: Boolean): List<T> = if (left) items else items.reversed()

/**
 * Bottom-up, hand-first grid order: item 0 lands in the bottom row on the working-hand side.
 * Null cells pad short rows. Returned top-to-bottom, left-to-right.
 */
fun <T> handGrid(items: List<T>, columns: Int, left: Boolean): List<T?> {
    val rows = items.chunked(columns)
    return rows.reversed().flatMap { row ->
        val padded: List<T?> = row + List(columns - row.size) { null }
        if (left) padded else padded.reversed()
    }
}

data class ChipItem(val label: String, val selected: Boolean, val onClick: () -> Unit)

/**
 * A chip row that starts at the working-hand edge. With [natural] the chips keep their reading
 * order left to right (scales such as speeds or quality), still pressed to the hand edge.
 */
@Composable
fun HandChips(chips: List<ChipItem>, left: Boolean, modifier: Modifier = Modifier, natural: Boolean = false) {
    val chips = if (natural && !left) chips.reversed() else chips
    // A short row also sits at the hand edge, not across the screen. A row too long for the
    // screen fades out where more chips wait, instead of looking cut off.
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyRow(modifier.fillMaxWidth().edgeFade(state, reversed = !left), state = state, reverseLayout = !left,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, if (left) Alignment.Start else Alignment.End)) {
        itemsIndexed(chips, key = { index, chip -> "$index:${chip.label}" }) { _, chip ->
            val bg = if (chip.selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh
            val fg = if (chip.selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                Text(chip.label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg)
                        .semantics { selected = chip.selected }
                        .clickable(role = Role.Tab, onClick = chip.onClick)
                        .height(40.dp).wrapContentHeight().padding(horizontal = 16.dp))
            }
        }
    }
}

/** Fades a sideways list's edges where it can still scroll. */
fun Modifier.edgeFade(state: androidx.compose.foundation.lazy.LazyListState, reversed: Boolean, width: Dp = 28.dp): Modifier =
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = width.toPx()
            val clear = androidx.compose.ui.graphics.Color.Transparent
            val black = androidx.compose.ui.graphics.Color.Black
            // Forward is to the right in a normal row and to the left in a reversed one.
            val fadeLeft = if (reversed) state.canScrollForward else state.canScrollBackward
            val fadeRight = if (reversed) state.canScrollBackward else state.canScrollForward
            if (fadeLeft) drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(black, clear), startX = 0f, endX = w),
                size = androidx.compose.ui.geometry.Size(w, size.height), blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
            if (fadeRight) drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(clear, black), startX = size.width - w, endX = size.width),
                topLeft = androidx.compose.ui.geometry.Offset(size.width - w, 0f), size = androidx.compose.ui.geometry.Size(w, size.height),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
        }

data class TileItem(val icon: ImageVector, val label: String, val value: String? = null, val selected: Boolean = false,
    val enabled: Boolean = true, val onClick: () -> Unit)

/** Sheet tiles in hand-first, bottom-up order. */
@Composable
fun TileGrid(items: List<TileItem>, columns: Int, left: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        handGrid(items, columns, left).chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile ->
                    if (tile == null) Spacer(Modifier.weight(1f)) else Tile(tile, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Tile(tile: TileItem, modifier: Modifier) {
    val bg = if (tile.selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (tile.selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Surface(onClick = tile.onClick, enabled = tile.enabled, modifier = modifier.heightIn(min = 78.dp).semantics { selected = tile.selected },
        shape = RoundedCornerShape(16.dp), color = bg, contentColor = fg.copy(alpha = if (tile.enabled) 1f else 0.38f)) {
        Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(tile.icon, null, Modifier.size(26.dp))
            Text(tile.label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
            tile.value?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (tile.selected) fg.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** A plain choice list for sheets: one row per option, 48dp minimum. */
@Composable
fun ChoiceRows(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { this.selected = index == selected }.clickable { onPick(index) }
                .padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, null, Modifier.size(24.dp), tint = if (index == selected) MaterialTheme.colorScheme.tertiary else androidx.compose.ui.graphics.Color.Transparent)
                Spacer(Modifier.width(16.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                    color = if (index == selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * Hides a bar while the list is read downward and brings it back on the way up. Attach
 * [connection] with nestedScroll to the list; read [visible].
 */
class ScrollHide {
    var visible by mutableStateOf(true)
    val connection = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
            if (available.y < -6f) visible = false else if (available.y > 6f) visible = true
            return androidx.compose.ui.geometry.Offset.Zero
        }
    }
}

@Composable
fun rememberScrollHide(): ScrollHide = remember { ScrollHide() }
