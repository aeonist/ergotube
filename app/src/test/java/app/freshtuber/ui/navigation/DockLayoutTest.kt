package app.freshtuber.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class DockLayoutTest {
    @Test fun requestedVisualOrder() {
        assertEquals(listOf(DockTab.Mine, DockTab.Subscriptions, DockTab.Search, DockTab.Shorts, DockTab.Home),
            DockLayout.order(left = false, shorts = true))
    }

    @Test fun leftHandKeepsTheRequestedOrder() {
        assertEquals(listOf(DockTab.Mine, DockTab.Subscriptions, DockTab.Search, DockTab.Shorts, DockTab.Home),
            DockLayout.order(left = true, shorts = true))
    }

    @Test fun hiddenShortsKeepsTheRestInPlace() {
        assertEquals(listOf(DockTab.Mine, DockTab.Subscriptions, DockTab.Search, DockTab.Home), DockLayout.order(left = false, shorts = false))
        assertEquals(listOf(DockTab.Mine, DockTab.Subscriptions, DockTab.Search, DockTab.Home), DockLayout.order(left = true, shorts = false))
    }

    @Test fun swappingOnlyExchangesTheTwoEndTabs() {
        assertEquals(listOf(DockTab.Home, DockTab.Subscriptions, DockTab.Search, DockTab.Shorts, DockTab.Mine), DockLayout.order(false, true, true))
        assertEquals(listOf(DockTab.Home, DockTab.Subscriptions, DockTab.Search, DockTab.Mine), DockLayout.order(true, false, true))
    }

    @Test fun expandedScreenAlwaysUsesFullWidth() {
        assertEquals(300f, DockLayout.width(400f, 0, false))
        assertEquals(400f, DockLayout.width(400f, 100, false))
        assertEquals(840f, DockLayout.width(840f, 0, true))
        assertEquals(280f, DockLayout.width(280f, 0, false))
    }

    @Test fun mineTilesPutTheMostUsedLowestOnTheHandSide() {
        // Right hand: Watch later bottom right, Downloads beside it, Settings alone on top.
        assertEquals(listOf(null, MineItem.Settings, MineItem.Playlists, MineItem.History, MineItem.Downloads, MineItem.WatchLater),
            app.freshtuber.ui.components.handGrid(MineItem.entries, 2, left = false))
        assertEquals(listOf(MineItem.Settings, null, MineItem.History, MineItem.Playlists, MineItem.WatchLater, MineItem.Downloads),
            app.freshtuber.ui.components.handGrid(MineItem.entries, 2, left = true))
    }
}
