package app.freshtuber.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class DockLayoutTest {
    @Test fun requestedVisualOrder() {
        assertEquals(listOf(DockTab.Mine, DockTab.Subscriptions, DockTab.Search, DockTab.Shorts, DockTab.Home),
            DockLayout.order(left = false, shorts = true))
    }

    @Test fun leftHandInvertsTheOrder() {
        assertEquals(listOf(DockTab.Home, DockTab.Shorts, DockTab.Search, DockTab.Subscriptions, DockTab.Mine),
            DockLayout.order(left = true, shorts = true))
    }

    @Test fun hiddenShortsKeepsTheRestInPlace() {
        assertEquals(listOf(DockTab.Mine, DockTab.Subscriptions, DockTab.Search, DockTab.Home), DockLayout.order(left = false, shorts = false))
        assertEquals(listOf(DockTab.Home, DockTab.Search, DockTab.Subscriptions, DockTab.Mine), DockLayout.order(left = true, shorts = false))
    }

    @Test fun swappingInvertsTheWholeRowAroundTheCentre() {
        assertEquals(listOf(DockTab.Home, DockTab.Shorts, DockTab.Search, DockTab.Subscriptions, DockTab.Mine), DockLayout.order(false, true, swapHomeMine = true))
        assertEquals(listOf(DockTab.Mine, DockTab.Subscriptions, DockTab.Search, DockTab.Home), DockLayout.order(true, false, swapHomeMine = true))
    }

    @Test fun expandedScreenAlwaysUsesFullWidth() {
        assertEquals(300f, DockLayout.width(400f, 0, false))
        assertEquals(400f, DockLayout.width(400f, 100, false))
        assertEquals(840f, DockLayout.width(840f, 0, true))
        assertEquals(280f, DockLayout.width(280f, 0, false))
    }

    @Test fun mineTilesPutTheMostUsedLowestOnTheHandSide() {
        for (showShorts in listOf(false, true)) {
            val rightGrid = app.freshtuber.ui.components.handGrid(DockLayout.mineItems(false, showShorts), 2, left = false)
            val leftGrid = app.freshtuber.ui.components.handGrid(DockLayout.mineItems(true, showShorts), 2, left = true)
            val accountRow = 2
            assertEquals(true, rightGrid.contains(MineItem.Favorites))
            assertEquals(listOf(MineItem.Account, MineItem.Settings), rightGrid.subList(accountRow, accountRow + 2))
            assertEquals(listOf(MineItem.Account, MineItem.Settings), leftGrid.subList(accountRow, accountRow + 2))
            assertEquals(listOf(MineItem.Downloads, MineItem.WatchLater), rightGrid.takeLast(2))
            assertEquals(listOf(MineItem.WatchLater, MineItem.Downloads), leftGrid.takeLast(2))
        }
    }

    @Test fun mineHasNoMusicTile() {
        assertEquals(false, MineItem.entries.any { it.name == "Music" })
    }

    @Test fun musicTakesShortsPlaceWhenChosen() {
        assertEquals(listOf(DockTab.Mine, DockTab.Subscriptions, DockTab.Search, DockTab.Music, DockTab.Home),
            DockLayout.order(left = false, shorts = true, music = true))
        assertEquals(listOf(DockTab.Home, DockTab.Music, DockTab.Search, DockTab.Subscriptions, DockTab.Mine),
            DockLayout.order(left = true, shorts = false, music = true))
    }

    @Test fun musicDockPutsListenByTheThumb() {
        assertEquals(listOf(DockTab.Video, DockTab.Search, DockTab.Library, DockTab.Listen), DockLayout.musicOrder(left = false))
        assertEquals(listOf(DockTab.Listen, DockTab.Library, DockTab.Search, DockTab.Video), DockLayout.musicOrder(left = true))
        assertEquals(listOf(DockTab.Listen, DockTab.Library, DockTab.Search, DockTab.Video), DockLayout.musicOrder(left = false, swapHomeMine = true))
    }
}
