package app.freshtuber.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class DockLayoutTest {
    @Test fun rightHandPutsHomeUnderTheThumbAndMineAfterTheTabs() {
        assertEquals(listOf(DockTab.Mine, DockTab.Search, DockTab.Shorts, DockTab.Subscriptions, DockTab.Home),
            DockLayout.order(left = false, shorts = true))
    }

    @Test fun leftHandMirrorsTheOrder() {
        assertEquals(listOf(DockTab.Home, DockTab.Subscriptions, DockTab.Shorts, DockTab.Search, DockTab.Mine),
            DockLayout.order(left = true, shorts = true))
    }

    @Test fun hiddenShortsKeepsTheRestInPlace() {
        assertEquals(listOf(DockTab.Mine, DockTab.Search, DockTab.Subscriptions, DockTab.Home), DockLayout.order(left = false, shorts = false))
        assertEquals(listOf(DockTab.Home, DockTab.Subscriptions, DockTab.Search, DockTab.Mine), DockLayout.order(left = true, shorts = false))
    }

    @Test fun mineTilesPutTheMostUsedLowestOnTheHandSide() {
        // Right hand: Watch later bottom right, Downloads beside it, Settings alone on top.
        assertEquals(listOf(null, MineItem.Settings, MineItem.Playlists, MineItem.History, MineItem.Downloads, MineItem.WatchLater),
            app.freshtuber.ui.components.handGrid(MineItem.entries, 2, left = false))
        assertEquals(listOf(MineItem.Settings, null, MineItem.History, MineItem.Playlists, MineItem.WatchLater, MineItem.Downloads),
            app.freshtuber.ui.components.handGrid(MineItem.entries, 2, left = true))
    }
}
