package app.freshtuber.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.Home as OutlinedHome
import androidx.compose.material.icons.outlined.Subscriptions as OutlinedSubscriptions
import androidx.compose.material.icons.outlined.Slideshow as OutlinedSlideshow
import androidx.compose.material.icons.outlined.VideoLibrary as OutlinedVideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.ui.components.Reach

/** The five dock buttons. [Mine] opens a short list instead of a screen. */
enum class DockTab(val icon: ImageVector, val selectedIcon: ImageVector, @param:StringRes val labelRes: Int) {
    Home(Icons.Outlined.OutlinedHome, Icons.Rounded.Home, R.string.nav_home),
    Subscriptions(Icons.Outlined.OutlinedSubscriptions, Icons.Rounded.Subscriptions, R.string.nav_subscriptions),
    Shorts(Icons.Outlined.OutlinedSlideshow, Icons.Rounded.Slideshow, R.string.nav_shorts),
    Search(Icons.Rounded.Search, Icons.Rounded.Search, R.string.nav_search),
    Mine(Icons.Outlined.OutlinedVideoLibrary, Icons.Rounded.VideoLibrary, R.string.nav_mine),
}

/** Rows of «Моё», most used first: the first one sits right above the dock. */
enum class MineItem(val icon: ImageVector, @param:StringRes val labelRes: Int) {
    WatchLater(Icons.Rounded.Schedule, R.string.mine_later),
    Downloads(Icons.Rounded.DownloadDone, R.string.library_downloads),
    History(Icons.Rounded.History, R.string.library_history),
    Playlists(Icons.AutoMirrored.Rounded.PlaylistPlay, R.string.library_playlists),
    Settings(Icons.Rounded.Settings, R.string.nav_settings),
}

object DockLayout {
    /**
     * Visual left-to-right order. Home is outermost on the working-hand side, right under the
     * thumb; «Моё» follows the other tabs on the inner side, not across the screen.
     */
    fun order(left: Boolean, shorts: Boolean): List<DockTab> {
        val handFirst = DockTab.entries.filter { shorts || it != DockTab.Shorts }
        return if (left) handFirst else handFirst.reversed()
    }

    /** Top-to-bottom rows of «Моё»: the most used row is the lowest, nearest the finger. */
    fun mineRows(): List<MineItem> = MineItem.entries.reversed()
}

/**
 * The navigation bar: a floating liquid drop. The container is transparent; the background
 * shader draws the drop at the rect reported here.
 */
@Composable
fun AppDock(selected: DockTab?, mineOpen: Boolean, left: Boolean, shorts: Boolean, onSelect: (DockTab) -> Unit, modifier: Modifier = Modifier) {
    val light = app.freshtuber.ui.liquid.LocalLiquidLight.current
    val radius = with(androidx.compose.ui.platform.LocalDensity.current) { 34.dp.toPx() }
    DisposableEffect(light) { onDispose { light?.setDrop(0, null, 0f) } }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp)
                .fillMaxWidth().height(Reach.DockHeight)
                .onGloballyPositioned { light?.setDrop(0, it.boundsInRoot(), radius) }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockLayout.order(left, shorts).forEach { tab ->
                val active = if (tab == DockTab.Mine) mineOpen || (selected == DockTab.Mine) else selected == tab && !mineOpen
                DockButton(tab, active, Modifier.weight(1f)) { onSelect(tab) }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun DockButton(tab: DockTab, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val label = stringResource(tab.labelRes)
    val description = if (tab == DockTab.Mine) stringResource(R.string.nav_mine_description) else label
    Column(
        modifier.fillMaxHeight().testTag("dock:${tab.name}")
            .semantics { selected = active; contentDescription = description }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = false, radius = 32.dp),
                role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        Box(Modifier.size(56.dp, 30.dp).background(if (active) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
            RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Icon(if (active) tab.selectedIcon else tab.icon, null, Modifier.size(24.dp), tint = color)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

/** «Моё»: a short plain list right above the dock. The dock stays visible and usable. */
@Composable
fun MineList(downloads: Int, onPick: (MineItem) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        // Tap outside closes; not a control of its own (Back does the same for TalkBack).
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .pointerInput(onDismiss) { detectTapGestures { onDismiss() } })
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
            Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                DockLayout.mineRows().forEach { item ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("mine:${item.name}").clickable { onPick(item) }.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(item.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(item.labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (item == MineItem.Downloads && downloads > 0) Text(downloads.toString(), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
