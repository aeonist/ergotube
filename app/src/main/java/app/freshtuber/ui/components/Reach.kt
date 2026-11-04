package app.freshtuber.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    /**
     * Big header above every list, pushing the first item down toward the thumb. Half of the
     * mockup's 240px so that one and a half videos fit; pending the user's confirmation.
     */
    val HeaderHeight: Dp = 120.dp

    /** Fixed controls start below this fraction of the screen height. */
    const val LINE = 0.55f

    val DockHeight: Dp = 64.dp
}

/** The working hand from settings; controls press to this side. */
val LocalLeftHanded = compositionLocalOf { false }

/** Height of the current screen's bottom bar, so snackbars appear above it. */
class BottomBarSpace {
    var height by mutableStateOf(0.dp)
}

val LocalBottomBarSpace = staticCompositionLocalOf { BottomBarSpace() }

/** Big title block whose text sits at the bottom, near the first item. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, height: Dp = Reach.HeaderHeight) {
    Column(
        modifier.fillMaxWidth().heightIn(min = height).padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, lineHeight = 38.sp),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        subtitle?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** A screen's own controls, right above the dock. Reports its height for snackbars. */
@Composable
fun BottomBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val space = LocalBottomBarSpace.current
    val density = LocalDensity.current
    DisposableEffect(space) { onDispose { space.height = 0.dp } }
    Column(
        modifier.fillMaxWidth()
            .onSizeChanged { space.height = with(density) { it.height.toDp() } }
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

/** Sub-screen controls in one row; the first action sits under the thumb. */
@Composable
fun ContextBar(actions: List<ContextAction>, left: Boolean, modifier: Modifier = Modifier, above: (@Composable ColumnScope.() -> Unit)? = null) {
    BottomBar(modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            above?.invoke(this)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, if (left) Alignment.Start else Alignment.End)) {
                handOrder(actions, left).forEach { action -> ContextButton(action, Modifier.weight(1f, fill = false).widthIn(min = 64.dp, max = 150.dp).fillMaxWidth()) }
            }
        }
    }
}

/** Back first, under the thumb, then the screen's own actions. */
@Composable
fun backAction(onBack: () -> Unit) = ContextAction(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), onClick = onBack)

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

/** A chip row that starts at the working-hand edge. */
@Composable
fun HandChips(chips: List<ChipItem>, left: Boolean, modifier: Modifier = Modifier) {
    LazyRow(modifier.fillMaxWidth(), reverseLayout = !left, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
