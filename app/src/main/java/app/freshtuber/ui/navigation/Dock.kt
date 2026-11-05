package app.freshtuber.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.graphicsLayer
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

/** Tiles of «Моё», most used first: the first one sits lowest, on the working-hand side. */
enum class MineItem(val icon: ImageVector, @param:StringRes val labelRes: Int, @param:StringRes val hintRes: Int) {
    WatchLater(Icons.Rounded.Schedule, R.string.mine_later, R.string.mine_later_hint),
    Downloads(Icons.Rounded.DownloadDone, R.string.library_downloads, R.string.mine_downloads_hint),
    History(Icons.Rounded.History, R.string.library_history, R.string.mine_history_hint),
    Playlists(Icons.AutoMirrored.Rounded.PlaylistPlay, R.string.library_playlists, R.string.mine_playlists_hint),
    Settings(Icons.Rounded.Settings, R.string.nav_settings, R.string.mine_settings_hint),
}

object DockLayout {
    /** Compact still leaves five readable 56dp slots; the setting interpolates to full width. */
    fun width(available: Float, percent: Int, expanded: Boolean): Float {
        val minimum = minOf(available, 300f)
        return if (expanded) available else minimum + (available - minimum) * percent.coerceIn(0, 100) / 100f
    }
    /** Fixed visual order chosen by the user, independent of the working hand. */
    fun order(left: Boolean, shorts: Boolean, swapHomeMine: Boolean = false): List<DockTab> {
        val first = if (swapHomeMine) DockTab.Home else DockTab.Mine
        val last = if (swapHomeMine) DockTab.Mine else DockTab.Home
        return listOf(first, DockTab.Subscriptions, DockTab.Search, DockTab.Shorts, last)
            .filter { shorts || it != DockTab.Shorts }
    }

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
    val preferences = app.freshtuber.ui.theme.LocalAppPreferences.current
    val expanded = app.freshtuber.ui.components.isLargeScreen()
    // Expanded inner screens always use the whole width; phone width is a personal setting.
    Column(modifier.fillMaxWidth(), horizontalAlignment = if (left) Alignment.Start else Alignment.End) {
        BoxWithConstraints(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp).fillMaxWidth(),
            contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd) {
        val width = DockLayout.width(maxWidth.value, preferences.dockWidth, expanded).dp
        Row(
            Modifier.width(width).height(Reach.DockHeight)
                .onGloballyPositioned { light?.setDrop(0, it.boundsInRoot(), radius) }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockLayout.order(left, shorts, preferences.enabled(app.freshtuber.data.settings.AppFeature.SwapHomeMine)).forEach { tab ->
                val active = if (tab == DockTab.Mine) mineOpen || (selected == DockTab.Mine) else selected == tab && !mineOpen
                DockButton(tab, active, Modifier.weight(1f)) { onSelect(tab) }
            }
        }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/**
 * The dock over a Short: a slim row of icons on a soft shade, so the video keeps the screen.
 * Same order and buttons as [AppDock]; labels go to TalkBack only.
 */
@Composable
fun CompactDock(selected: DockTab?, mineOpen: Boolean, left: Boolean, shorts: Boolean, onSelect: (DockTab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color(0x8C000000))))) {
        val preferences = app.freshtuber.ui.theme.LocalAppPreferences.current
        val expanded = app.freshtuber.ui.components.isLargeScreen()
        BoxWithConstraints(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 14.dp).fillMaxWidth(), contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd) {
        Row(
            Modifier.width(DockLayout.width(maxWidth.value, preferences.dockWidth, expanded).dp).height(Reach.CompactDockHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockLayout.order(left, shorts, preferences.enabled(app.freshtuber.data.settings.AppFeature.SwapHomeMine)).forEach { tab ->
                val active = if (tab == DockTab.Mine) mineOpen || (selected == DockTab.Mine) else selected == tab && !mineOpen
                val label = stringResource(tab.labelRes)
                val description = if (tab == DockTab.Mine) stringResource(R.string.nav_mine_description) else label
                Box(Modifier.weight(1f).fillMaxHeight().testTag("dock:${tab.name}")
                    .semantics { this.selected = active; contentDescription = description }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = false, radius = 24.dp),
                        role = Role.Tab) { onSelect(tab) },
                    contentAlignment = Alignment.Center) {
                    Icon(if (active) tab.selectedIcon else tab.icon, null, Modifier.size(24.dp),
                        tint = if (active) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f))
                    if (active) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(4.dp)
                        .background(androidx.compose.ui.graphics.Color.White, androidx.compose.foundation.shape.CircleShape))
                }
            }
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

/**
 * «Моё»: section tiles right above the dock, in the Settings tiles' style, over the blurred
 * screen (the blur is applied to the content by the caller). The most used tile sits lowest on
 * the working-hand side; the dock stays visible and usable.
 */
@Composable
fun MineList(downloads: Int, onPick: (MineItem) -> Unit, onDismiss: () -> Unit, bottomInset: androidx.compose.ui.unit.Dp = 0.dp) {
    BackHandler(onBack = onDismiss)
    val left = app.freshtuber.ui.components.LocalLeftHanded.current
    // The tiles rise a little out of the dock as the pane opens.
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        appear.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 520f))
    }
    val resources = androidx.compose.ui.platform.LocalResources.current
    Box(Modifier.fillMaxSize()) {
        // Tap outside closes; not a control of its own (Back does the same for TalkBack).
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.22f * appear.value))
            .pointerInput(onDismiss) { detectTapGestures { onDismiss() } })
        app.freshtuber.ui.components.SectionTiles(MineItem.entries, left,
            Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp + bottomInset).widthIn(max = 560.dp)
                .graphicsLayer {
                    alpha = appear.value
                    translationY = (1f - appear.value) * 24.dp.toPx()
                }) { item, modifier ->
            val hint = when (item) {
                MineItem.Downloads -> if (downloads > 0) resources.getQuantityString(R.plurals.mine_downloads_count, downloads, downloads)
                    else resources.getString(R.string.mine_downloads_hint)
                else -> resources.getString(item.hintRes)
            }
            app.freshtuber.ui.components.SectionTile(item.icon, stringResource(item.labelRes), hint, modifier.testTag("mine:${item.name}")) { onPick(item) }
        }
    }
}
