package app.freshtuber.ui.classic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.freshtuber.R
import app.freshtuber.ui.components.Reach
import app.freshtuber.ui.navigation.DockTab
import app.freshtuber.ui.theme.BrandColors

/** YouTube's top bar: the logo on the left, search and settings on the right. */
@Composable
fun ClassicTopBar(onSearch: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(48.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ClassicLogo()
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch, modifier = Modifier.testTag("classic:search")) {
            Icon(Icons.Rounded.Search, stringResource(R.string.nav_search))
        }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.nav_settings)) }
    }
}

/** The red rounded rectangle with a white play triangle. */
@Composable
fun ClassicLogo(modifier: Modifier = Modifier) {
    Canvas(modifier.size(30.dp, 21.dp)) {
        drawRoundRect(BrandColors.Red, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.3f))
        val w = size.width; val h = size.height
        drawPath(Path().apply {
            moveTo(w * 0.40f, h * 0.30f); lineTo(w * 0.40f, h * 0.70f); lineTo(w * 0.66f, h * 0.50f); close()
        }, Color.White)
    }
}

/**
 * YouTube's bottom tabs: a plain full-width bar with an icon and a small label per tab. Over a
 * Short it turns black, like YouTube's.
 */
@Composable
fun ClassicNavBar(selected: DockTab?, mineOpen: Boolean, shorts: Boolean, overVideo: Boolean, onSelect: (DockTab) -> Unit, modifier: Modifier = Modifier) {
    val preferences = app.freshtuber.ui.theme.LocalAppPreferences.current
    val left = app.freshtuber.ui.components.LocalLeftHanded.current
    val expanded = app.freshtuber.ui.components.isLargeScreen()
    val background = Color.Transparent
    val content = if (overVideo) Color.White else MaterialTheme.colorScheme.onSurface
    Column(modifier.fillMaxWidth().background(background)) {
        BoxWithConstraints(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).fillMaxWidth(),
            contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd) {
        Row(Modifier.width(app.freshtuber.ui.navigation.DockLayout.width(maxWidth.value, preferences.dockWidth, expanded).dp).height(Reach.CompactDockHeight),
            verticalAlignment = Alignment.CenterVertically) {
            app.freshtuber.ui.navigation.DockLayout.order(left, shorts).forEach { tab ->
                val active = if (tab == DockTab.Mine) mineOpen || selected == DockTab.Mine else selected == tab && !mineOpen
                val label = stringResource(tab.labelRes)
                Column(Modifier.weight(1f).fillMaxHeight().testTag("dock:${tab.name}")
                    .semantics { this.selected = active; contentDescription = label }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = false, radius = 28.dp),
                        role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(if (active) tab.selectedIcon else tab.icon, null, Modifier.size(24.dp), tint = content)
                    Text(label, fontSize = 10.sp, lineHeight = 12.sp, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** YouTube's category chips: grey pills, the selected one inverted. */
@Composable
fun ClassicChips(chips: List<app.freshtuber.ui.components.ChipItem>, modifier: Modifier = Modifier) {
    androidx.compose.foundation.lazy.LazyRow(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips.size, key = { "$it:${chips[it].label}" }) { index ->
            val chip = chips[index]
            val bg = if (chip.selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh
            val fg = if (chip.selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
            Text(chip.label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1,
                modifier = Modifier.background(bg, RoundedCornerShape(8.dp)).semantics { selected = chip.selected }
                    .clickable(role = Role.Tab, onClick = chip.onClick).heightIn(min = 32.dp).wrapContentHeight()
                    .padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}
